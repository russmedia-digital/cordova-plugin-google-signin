package com.devapps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.auth.api.credentials.Credentials;
import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.BeginSignInResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.GoogleAuthProvider;

public class GoogleSignInPlugin extends CordovaPlugin {

    private static final int RC_SIGN_IN = 101;
    private static final int RC_ONE_TAP = 102;

    private GoogleSignInAccount account;
    private FirebaseAuth mAuth;

    private SignInClient mOneTapSigninClient;
    private BeginSignInRequest mSiginRequest;

    private Context mContext;
    private Activity mCurrentActivity;
    private CallbackContext mCallbackContext;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mCurrentActivity = this.cordova.getActivity();
        mAuth = FirebaseAuth.getInstance();
        mContext = this.cordova.getActivity().getApplicationContext();
        FirebaseApp.initializeApp(mContext);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (action.equals("oneTapLogin")) {
            this.oneTapLogin(callbackContext);
            return true;
        } else if (action.equals("isSignedIn")) {
            this.isSignedIn(callbackContext);
            return true;
        } else if (action.equals("disconnect")) {
            this.disconnect(callbackContext);
            return true;
        } else if (action.equals("signIn")) {
            this.signIn(callbackContext);
            return true;
        } else if (action.equals("signOut")) {
            this.signOut(callbackContext);
            return true;
        }
        return false;
    }

    private void oneTapLogin(CallbackContext callbackContext) {
        mCallbackContext = callbackContext;
        processOneTap();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (Exception e) {
                System.out.println("Google sign in failed: " + e);
                mCallbackContext.error("{\"status\" : \"error\", \"message\" : " + e.getMessage() + "}");
            }
        } else if (requestCode == RC_ONE_TAP) {
            try {
                SignInCredential credential = mOneTapSigninClient.getSignInCredentialFromIntent(data);
                firebaseAuthWithGoogle(credential.getGoogleIdToken());
            } catch (Exception ex) {
                String dataToSend = "{\"status\" : \"error\", \"message\" : " + ex.getMessage() + "}";
                mCallbackContext.error(dataToSend);
            }
        }
    }

    private void isSignedIn(CallbackContext callbackContext) {
        boolean isSignedIn = (account != null || mAuth.getCurrentUser() != null);
        String dataToSend = "{\"status\" : \"success\", \"message\" : " + isSignedIn + "}";
        callbackContext.success(dataToSend);
    }

    private void disconnect(CallbackContext callbackContext) {
        String dataToSend = "{\"status\" : \"error\", \"message\" : \"Not available on Android.\"}";
        callbackContext.error(dataToSend);
    }

    private void signIn(CallbackContext callbackContext) {
        mCallbackContext = callbackContext;
        signIn();
    }

    private void signOut(CallbackContext callbackContext) {
        mCallbackContext = callbackContext;
        signOut();
    }

    private void signIn() {
        cordova.setActivityResultCallback(this);
        GoogleSignInOptions gso = getGoogleSignInOptions();
        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(mContext, gso);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        mCurrentActivity.startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void processOneTap() {
        cordova.setActivityResultCallback(this);
        mOneTapSigninClient = Identity.getSignInClient(mContext);
        mSiginRequest = BeginSignInRequest.builder()
                .setPasswordRequestOptions(BeginSignInRequest.PasswordRequestOptions.builder().build())
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(this.cordova.getActivity().getResources().getString(getAppResource("default_client_id", "string")))
                        .build())
                .setAutoSelectEnabled(true)
                .build();

        mOneTapSigninClient.beginSignIn(mSiginRequest)
                .addOnSuccessListener(new OnSuccessListener<BeginSignInResult>() {
                    @Override
                    public void onSuccess(BeginSignInResult beginSignInResult) {
                        try {
                            mCurrentActivity.startIntentSenderForResult(beginSignInResult.getPendingIntent().getIntentSender(), RC_ONE_TAP, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                            mCallbackContext.error("{\"status\" : \"error\", \"message\" : \"" + e.getMessage() + "\"}");
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        mCallbackContext.error("{\"status\" : \"error\", \"message\" : \"" + e.getMessage() + "\"}");
                    }
                });
    }

    private void signOut() {
        GoogleSignInOptions gso = getGoogleSignInOptions();

        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(mContext, gso);
        mGoogleSignInClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                account = null;
                mCallbackContext.success("{\"status\" : \"success\", \"message\":\"Logged out\"}");
                mAuth.signOut();
            }
        });
        mGoogleSignInClient.signOut().addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                mCallbackContext.error("{\"status\" : \"error\", \"message\" : \"" + e.getMessage() + "\"}");
            }
        });
    }

    private void firebaseAuthWithGoogle(String googleIdToken) {
        AuthCredential credentials = GoogleAuthProvider.getCredential(googleIdToken, null);
        mAuth.signInWithCredential(credentials).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {

                if(task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    user.getIdToken(false).addOnSuccessListener(new OnSuccessListener<GetTokenResult>() {
                        @Override
                        public void onSuccess(GetTokenResult getTokenResult) {
                            try {
                                JSONObject userInfo = new JSONObject();
                                userInfo.put("id", user.getUid());
                                userInfo.put("display_name", user.getDisplayName());
                                userInfo.put("email", user.getEmail());
                                userInfo.put("photo_url", user.getPhotoUrl());
                                userInfo.put("id_token", getTokenResult.getToken());
                                String dataToSend = "{\"status\" : \"success\", \"user\" : " + userInfo.toString() + "}";
                                mCallbackContext.success(dataToSend);
                            } catch (Exception ex) {
                                mCallbackContext.error("{\"status\" : \"error\", \"message\" : \"" + ex.getMessage() + "\"}");
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception ex) {
                            mCallbackContext.error("{\"status\" : \"error\", \"message\" : \"" + ex.getMessage() + "\"}");
                        }
                    });
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                mCallbackContext.error("{\"status\" : \"error\", \"message\" : \"" + e.getMessage() + "\"}");
            }
        });
    }

    private GoogleSignInOptions getGoogleSignInOptions() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(this.cordova.getActivity().getResources().getString(getAppResource("default_client_id", "string")))
                .requestEmail()
                .build();
        return gso;
    }

    private int getAppResource(String name, String type) {
        return cordova.getActivity().getResources().getIdentifier(name, type, cordova.getActivity().getPackageName());
    }
}
