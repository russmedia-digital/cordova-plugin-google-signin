# cordova-plugin-google-signin

Cordova plugin for integrating Google Signin in Android &amp; iOS app. It is updated to the latest Google Signin SDK on iOS `6.1` with support for M1 chip. Android additionally supports One Tap Login.

## Description

The plugin allows you to authenticate users with Google Sign-In on iOS and Android. The API allows you to get email, display name, profile picture url, user id and idToken.

## Installation

### Firebase Project

Create a New Project in Firebase Console. Once the project is created, add an Android & iOS app to the project.

### Android app

Register app by providing the package name, nickname and SHA-1 key. The package name should be the same you used during creation of the Corddova project. If you're not aware about the package name or have modified it, open the android project in Android Studio and note the applicationId from app module's build.gradle file.

You can get the SHA-1 key using the following command, debug SHA-1:

    keytool -list -v -alias <your-key-name> -keystore <path-to-production-keystore>

For the debug keystore, you can use the below command (default password is "android"):

    keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore

You'll need to add the SHA-1 for the release keystore for the production build to work. Use the above commmand by replacing the path to your release keystore file.

### iOS App

Provide the bundle ID for the app and the name. Download the `GoogleService-Info.plist` file, open the plist file in a text editor and note the value of REVERSED_CLIENT_ID from it. The Bundle ID should be the same you used during creation of the cordova project. In case you're not aware or have modified it, you can find the bundle ID by opening the iOS project in Xcode.

### Oauth Requests

This app needs to be configured to make OAuth requests. To do that, set up the apps OAuth consent screen in the Google Cloud Console https://console.developers.google.com/apis/credentials/consent?project=${your_project_number}
Open the above link by replacing the project number (can be found from firebase console's project settings), make sure the "OAuth consent screen" is selected in the left pane. Then select "External" and fill out the details on the next page.

## Install the plugin in your cordova project

You should have the value for the REVERSED_CLIENT_ID and CLIENT_ID handy before you install the plugin.

    cordova plugin add https://github.com/russmedia-digital/cordova-plugin-google-signin --save --variable REVERSED_CLIENT_ID=myreversedclientid --variable CLIENT_ID=yourclientid

Example:

    cordova plugin add https://github.com/russmedia-digital/cordova-plugin-google-signin n --save --variable REVERSED_CLIENT_ID="com.googleusercontent.apps.741002292512-79l1vjkim5tctosr07kcm61bb4frp7cr" --variable CLIENT_ID="741002292512-cs3emldkmt4vg5e1m9o6b3bpf8i6atfp.apps.googleusercontent.com"

## Usage

```javascript
cordova.plugins.GoogleSignInPlugin.signIn(
  function (authData) {
    // {
    //   "status": "success",
    //   "message": {
    //     "id": "",
    //     "display_name": "",
    //     "email": "",
    //     "photo_url": "",
    //     "id_token": ""
    //   }
    // }
    console.log(authData);
  },
  function (error) {
    console.error(error);
  }
);

cordova.plugins.GoogleSignInPlugin.isSignedIn(
  function (success) {
    console.log(success);
  },
  function (error) {
    console.error(error);
  }
);

cordova.plugins.GoogleSignInPlugin.signOut(
  function (success) {
    console.log(success);
  },
  function (error) {
    console.error(error);
  }
);

// Android only
cordova.plugins.GoogleSignInPlugin.oneTapLogin(
  function (success) {
    console.log(success);
  },
  function (error) {
    console.error(error);
  }
);
```
