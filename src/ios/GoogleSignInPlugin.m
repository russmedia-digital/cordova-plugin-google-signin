/********* GoogleSignInPlugin.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>

#import <GoogleSignIn/GoogleSignIn.h>

@interface GoogleSignInPlugin : CDVPlugin {
  // Member variables go here.
}

@property (nonatomic, assign) BOOL isSigningIn;
@property (nonatomic, copy) NSString* callbackId;
@end

@implementation GoogleSignInPlugin

- (void)pluginInitialize
{
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleOpenURL:) name:CDVPluginHandleOpenURLNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleOpenURLWithAppSourceAndAnnotation:) name:CDVPluginHandleOpenURLWithAppSourceAndAnnotationNotification object:nil];
}

//============

- (void)handleOpenURL:(NSNotification*)notification
{
    // no need to handle this handler, we dont have an sourceApplication here, which is required by GIDSignIn handleURL
}

- (void)handleOpenURLWithAppSourceAndAnnotation:(NSNotification*)notification
{
    NSMutableDictionary * options = [notification object];

    NSURL* url = options[@"url"];

    NSString* possibleReversedClientId = [url.absoluteString componentsSeparatedByString:@":"].firstObject;

    if ([possibleReversedClientId isEqualToString:self.getreversedClientId] && self.isSigningIn) {
        self.isSigningIn = NO;
        [GIDSignIn.sharedInstance handleURL:url];
    }
}

- (void) signIn:(CDVInvokedUrlCommand*)command {
    _callbackId = command.callbackId;
    NSString *reversedClientId = [self getreversedClientId];

    if (reversedClientId == nil) {
        NSDictionary *errorDetails = @{@"status": @"error", @"message": @"Could not find REVERSED_CLIENT_ID url scheme in app .plist"};
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[self toJSONString:errorDetails]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:_callbackId];
        return;
    }

    GIDSignIn *signIn = [GIDSignIn sharedInstance];

    [signIn signInWithPresentingViewController:self.viewController
                                    completion:^(GIDSignInResult * _Nullable signInResult, NSError * _Nullable error) {
        if (error != nil) {
            NSDictionary *errorDetails = @{@"status": @"error", @"message": error.localizedDescription};
            CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[self toJSONString:errorDetails]];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:self->_callbackId];
        } else {
            GIDGoogleUser *user = signInResult.user;
            NSString *email = user.profile.email;
            NSString *userId = user.userID;
            NSURL *imageUrl = [user.profile imageURLWithDimension:120];

            NSDictionary *result = @{
                @"email": email ? email : [NSNull null],
                @"id": userId ? userId : [NSNull null],
                @"id_token": user.idToken.tokenString ? user.idToken.tokenString : [NSNull null],
                @"display_name": user.profile.name ? user.profile.name : [NSNull null],
                @"given_name": user.profile.givenName ? user.profile.givenName : [NSNull null],
                @"family_name": user.profile.familyName ? user.profile.familyName : [NSNull null],
                @"photo_url": imageUrl ? imageUrl.absoluteString : [NSNull null]
            };

            NSDictionary *response = @{@"message": result, @"status": @"success"};

            CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[self toJSONString:response]];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:self->_callbackId];
        }
    }];
}

- (NSString*) reverseUrlScheme:(NSString*)scheme {
    NSArray* originalArray = [scheme componentsSeparatedByString:@"."];
    NSArray* reversedArray = [[originalArray reverseObjectEnumerator] allObjects];
    NSString* reversedString = [reversedArray componentsJoinedByString:@"."];
    return reversedString;
}

- (NSString*) getreversedClientId {
    NSArray* URLTypes = [[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleURLTypes"];

    if (URLTypes != nil) {
        for (NSDictionary* dict in URLTypes) {
            NSString *urlName = dict[@"CFBundleURLName"];
            if ([urlName isEqualToString:@"REVERSED_CLIENT_ID"]) {
                NSArray* URLSchemes = dict[@"CFBundleURLSchemes"];
                if (URLSchemes != nil) {
                    return URLSchemes[0];
                }
            }
        }
    }
    return nil;
}

- (void) signOut:(CDVInvokedUrlCommand*)command {
    [GIDSignIn.sharedInstance signOut];
    NSDictionary *details = @{@"status": @"success", @"message": @"Logged out"};
    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[self toJSONString:details]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void) disconnect:(CDVInvokedUrlCommand*)command {
    // Revoke access manually (GoogleSignIn v8 no longer has disconnectWithCallback)
    GIDGoogleUser *user = [GIDSignIn sharedInstance].currentUser;
    NSString *accessToken = user.authentication.accessToken;

    if (accessToken == nil) {
        NSDictionary *details = @{@"status": @"error", @"message": @"No user access token available"};
        CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[self toJSONString:details]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }

    NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"https://accounts.google.com/o/oauth2/revoke?token=%@", accessToken]];
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    [request setHTTPMethod:@"GET"];

    [[[NSURLSession sharedSession] dataTaskWithRequest:request
                                     completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (!error) {
                [[GIDSignIn sharedInstance] signOut]; // Also sign out locally
                NSDictionary *details = @{@"status": @"success", @"message": @"Disconnected"};
                CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[self toJSONString:details]];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            } else {
                NSDictionary *details = @{@"status": @"error", @"message": [error localizedDescription]};
                CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[self toJSONString:details]];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
        });
    }] resume];
}


- (void) isSignedIn:(CDVInvokedUrlCommand*)command {
    bool isSignedIn = [GIDSignIn.sharedInstance currentUser] != nil;
    NSDictionary *details = @{@"status": @"success", @"message": (isSignedIn) ? @"true" : @"false"};
    CDVPluginResult * pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[self toJSONString:details]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

#pragma mark - GIDSignInDelegate
- (void)signIn:(GIDSignIn *)signIn didSignInForUser:(GIDGoogleUser *)user withError:(NSError *)error {
    
}

- (void)signIn:(GIDSignIn *)signIn presentViewController:(UIViewController *)viewController {
    self.isSigningIn = YES;
    [self.viewController presentViewController:viewController animated:YES completion:nil];
}

- (void)signIn:(GIDSignIn *)signIn dismissViewController:(UIViewController *)viewController {
    [self.viewController dismissViewControllerAnimated:YES completion:nil];
}

- (NSString*)toJSONString:(NSDictionary*)dictionaryOrArray {
    NSError *error;
         NSData *jsonData = [NSJSONSerialization dataWithJSONObject:dictionaryOrArray
                                                       options:NSJSONWritingPrettyPrinted
                                                         error:&error];
         if (! jsonData) {
            NSLog(@"%s: error: %@", __func__, error.localizedDescription);
            return @"{}";
         } else {
            return [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
         }
}

@end
