var exec = require('cordova/exec');

exports.signIn = function (success, error, options) {
  exec(success, error, 'GoogleSignInPlugin', 'signIn', [options]);
};

exports.isSignedIn = function (success, error) {
  exec(success, error, 'GoogleSignInPlugin', 'isSignedIn');
};

exports.signOut = function (success, error) {
  exec(success, error, 'GoogleSignInPlugin', 'signOut');
};

exports.disconnect = function (success, error) {
  exec(success, error, 'GoogleSignInPlugin', 'disconnect');
};

exports.oneTapLogin = function (success, error, options) {
  exec(success, error, 'GoogleSignInPlugin', 'oneTapLogin', [options]);
};

exports.oneTapSignIn = function (success, error) {
  exec(success, error, 'GoogleSignInPlugin', 'oneTapSignIn');
};
