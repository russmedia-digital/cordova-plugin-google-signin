/********* GoogleSignInPlugin.m Cordova Plugin Implementation *******/
#import <Cordova/CDV.h>
#import <GoogleSignIn/GoogleSignIn.h>

@interface GoogleSignInPlugin : CDVPlugin
@property (nonatomic, assign) BOOL isSigningIn;
@property (nonatomic, copy) NSString* callbackId;
@property (nonatomic, copy) NSString* clientId;
@end
@implementation GoogleSignInPlugin
- (void)pluginInitialize {
    [super pluginInitialize];
    self.clientId = [self getClientId];
    NSLog(@"Google Sign-In configuration:");
    NSLog(@"Client ID: %@", self.clientId);
}

- (void)handleOpenURLWithAppSourceAndAnnotation:(NSNotification*)notification {
    NSMutableDictionary *options = [notification object];
    NSURL* url = options[@"url"];
    
    // Handle Google Sign-In callback URL
    [GIDSignIn.sharedInstance handleURL:url];
}

- (void)signIn:(CDVInvokedUrlCommand*)command {
    self.callbackId = command.callbackId;
    
    // 1. Configure Google Sign-In
    GIDConfiguration *config = [[GIDConfiguration alloc]
        initWithClientID:self.clientId
        serverClientID:self.clientId]; // Using same clientID for server
    
    [GIDSignIn.sharedInstance setConfiguration:config];

    // 2. Start sign-in flow
    self.isSigningIn = YES;
    [GIDSignIn.sharedInstance signInWithPresentingViewController:self.viewController
        completion:^(GIDSignInResult * _Nullable signInResult, NSError * _Nullable error) {
            
        self.isSigningIn = NO;
        
        if (error) {
            [self handleSignInError:error];
            return;
        }
        
        // 3. Success handler
        [self handleSignInSuccess:signInResult];
    }];
}

- (void)handleSignInSuccess:(GIDSignInResult *)signInResult {
    GIDGoogleUser *user = signInResult.user;
    
    NSDictionary *response = @{
        @"status": @"success",
        @"message": @{
            @"email": user.profile.email ?: [NSNull null],
            @"id": user.userID ?: [NSNull null],
            @"id_token": user.idToken.tokenString ?: [NSNull null],
            @"display_name": user.profile.name ?: [NSNull null],
            @"given_name": user.profile.givenName ?: [NSNull null],
            @"family_name": user.profile.familyName ?: [NSNull null],
            @"photo_url": [user.profile imageURLWithDimension:120].absoluteString ?: [NSNull null],
            @"server_auth_code": signInResult.serverAuthCode ?: [NSNull null]

        }
    };
    
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                            messageAsDictionary:response];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
}

- (void)handleSignInError:(NSError *)error {
    NSDictionary *response = @{
        @"status": @"error",
        @"error": @{
            @"code": @(error.code),
            @"message": error.localizedDescription ?: @"Unknown error",
            @"details": error.userInfo ?: @{}
        }
    };
    
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                            messageAsDictionary:response];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
}
- (NSString*)getClientId {
    // 1. Check main bundle first
    NSString *clientId = [[[NSBundle mainBundle] infoDictionary] objectForKey:@"GIDClientID"];
    if (clientId) return clientId;
    
    // 2. Fallback to direct plist read
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