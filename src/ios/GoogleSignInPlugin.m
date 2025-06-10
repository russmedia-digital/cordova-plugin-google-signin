/********* GoogleSignInPlugin.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import <GoogleSignIn/GoogleSignIn.h>

@interface GoogleSignInPlugin : CDVPlugin {
  // Member variables go here.
}

@property (nonatomic, assign) BOOL isSigningIn;
@property (nonatomic, copy) NSString* callbackId;
@property (nonatomic, copy) NSString* clientId;
@property (nonatomic, copy) NSString* reversedClientId;

@end

@implementation GoogleSignInPlugin

- (void)pluginInitialize {
    [super pluginInitialize];
    
    self.clientId = [self getClientId];
    self.reversedClientId = [self getreversedClientId];
    
    if (!self.clientId || !self.reversedClientId) {
        NSLog(@"Google Sign-In configuration error:");
        NSLog(@"Client ID: %@", self.clientId ? @"Exists" : @"MISSING");
        NSLog(@"Reversed Client ID: %@", self.reversedClientId ? @"Exists" : @"MISSING");
    }
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

    if ([possibleReversedClientId isEqualToString:self.reversedClientId] && self.isSigningIn) {
        self.isSigningIn = NO;
        [GIDSignIn.sharedInstance handleURL:url];
    }
}

- (void)signIn:(CDVInvokedUrlCommand*)command {
    self.callbackId = command.callbackId;
    
    if (!self.clientId) {
        NSDictionary *errorDetails = @{
            @"status": @"error",
            @"message": @"Missing GIDClientID. Verify your plugin variables and rebuild."
        };
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[self toJSONString:errorDetails]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }

    // Configure Google Sign-In
    GIDConfiguration *config = [[GIDConfiguration alloc] initWithClientID:self.clientId];
    [GIDSignIn.sharedInstance setConfiguration:config];
    
    self.isSigningIn = YES;
    
    [GIDSignIn.sharedInstance signInWithPresentingViewController:self.viewController
                                              completion:^(GIDSignInResult * _Nullable signInResult,
                                                          NSError * _Nullable error) {
        self.isSigningIn = NO;
        
        if (error) {
            NSDictionary *errorDetails = @{@"status": @"error", @"message": error.localizedDescription};
            CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[self toJSONString:errorDetails]];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
        } else {
            GIDGoogleUser *user = signInResult.user;
            NSString *email = user.profile.email;
            NSURL *imageUrl = [user.profile imageURLWithDimension:120];
            NSString *serverAuthCode = signInResult.serverAuthCode;

            NSString *idToken = signInResult.user.idToken.tokenString;
            NSString *userId = signInResult.user.identifier;
            
            NSDictionary *result = @{
                @"email": email ?: [NSNull null],
                @"id": userId ?: [NSNull null],
                @"id_token": idToken ?: [NSNull null],
                @"display_name": user.profile.name ?: [NSNull null],
                @"given_name": user.profile.givenName ?: [NSNull null],
                @"family_name": user.profile.familyName ?: [NSNull null],
                @"photo_url": imageUrl ? imageUrl.absoluteString : [NSNull null],
                @"server_auth_code": serverAuthCode ?: [NSNull null]
            };
            
            NSDictionary *response = @{@"message": result, @"status": @"success"};
            
            CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[self toJSONString:response]];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
        }
    }];
}

- (NSString*)reverseUrlScheme:(NSString*)scheme {
    NSArray* originalArray = [scheme componentsSeparatedByString:@"."];
    NSArray* reversedArray = [[originalArray reverseObjectEnumerator] allObjects];
    NSString* reversedString = [reversedArray componentsJoinedByString:@"."];
    return reversedString;
}

- (NSString*)getreversedClientId {
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

- (NSString*)getClientId {
    // Method 1: Directly from Info.plist dictionary
    NSString *clientId = [[[NSBundle mainBundle] infoDictionary] objectForKey:@"GIDClientID"];
    if (clientId) {
        return clientId;
    }
    
    // Method 2: Fallback - read from plist file directly
    NSString *path = [[NSBundle mainBundle] pathForResource:@"Info" ofType:@"plist"];
    NSDictionary *dict = [NSDictionary dictionaryWithContentsOfFile:path];
    clientId = [dict objectForKey:@"GIDClientID"];
    
    if (!clientId) {
        NSLog(@"GIDClientID not found in Info.plist. Please verify your plugin configuration.");
    }
    
    return clientId;
}

- (void)signOut:(CDVInvokedUrlCommand*)command {
    [GIDSignIn.sharedInstance signOut];
    NSDictionary *details = @{@"status": @"success", @"message": @"Logged out"};
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[self toJSONString:details]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)disconnect:(CDVInvokedUrlCommand*)command {
    [GIDSignIn.sharedInstance disconnectWithCompletion:^(NSError * _Nullable error) {
        if(error == nil) {
            NSDictionary *details = @{@"status": @"success", @"message": @"Disconnected"};
            CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[self toJSONString:details]];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        } else {
            NSDictionary *details = @{@"status": @"error", @"message": [error localizedDescription]};
            CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[self toJSONString:details]];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    }];
}

- (void)isSignedIn:(CDVInvokedUrlCommand*)command {
    bool isSignedIn = [GIDSignIn.sharedInstance currentUser] != nil;
    NSDictionary *details = @{@"status": @"success", @"message": (isSignedIn) ? @"true" : @"false"};
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[self toJSONString:details]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (NSString*)toJSONString:(NSDictionary*)dictionaryOrArray {
    NSError *error;
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:dictionaryOrArray
                                                   options:NSJSONWritingPrettyPrinted
                                                     error:&error];
    if (!jsonData) {
        NSLog(@"%s: error: %@", __func__, error.localizedDescription);
        return @"{}";
    } else {
        return [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
    }
}

@end