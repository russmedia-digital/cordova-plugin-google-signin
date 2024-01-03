package com.devapps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import com.google.android.gms.common.api.CommonStatusCodes;
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
//Firas
import com.google.android.gms.common.api.Scope;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;

//Firas
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.MessageDigest;
import android.content.pm.Signature;

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

    //Firas
    private String mScopes = "";
    private final static String FIELD_ACCESS_TOKEN      = "accessToken";
    private final static String FIELD_TOKEN_EXPIRES     = "expires";
    private final static String FIELD_TOKEN_EXPIRES_IN  = "expires_in";
    private final static String VERIFY_TOKEN_URL        = "https://www.googleapis.com/oauth2/v1/tokeninfo?access_token=";

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mCurrentActivity = this.cordova.getActivity();
        mAuth = FirebaseAuth.getInstance();
        mContext = this.cordova.getActivity().getApplicationContext();
        FirebaseApp.initializeApp(mContext);
        checkIfOneTapSignInCoolingPeriodShouldBeReset();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (action.equals(Constants.CORDOVA_ACTION_ONE_TAP_LOGIN)) {
            this.oneTapLogin(callbackContext);
            return true;
        } else if (action.equals(Constants.CORDOVA_ACTION_IS_SIGNEDIN)) {
            this.isSignedIn(callbackContext);
            return true;
        } else if (action.equals(Constants.CORDOVA_ACTION_DISCONNECT)) {
            this.disconnect(callbackContext);
            return true;
        } else if (action.equals(Constants.CORDOVA_ACTION_SIGNIN)) {
            //Firas
            this.mScopes = args.getString(0);
            if(this.mScopes == null || this.mScopes.trim().isEmpty()) {
                this.mScopes = "";
            }
            this.signIn(callbackContext);
            return true;
        } else if (action.equals(Constants.CORDOVA_ACTION_SIGNOUT)) {
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
                getAuthToken(mCurrentActivity, account, false, new AccessTokenCallback() {
                    @Override
                    public void onToken(String token) {
                        firebaseAuthWithGoogle(account.getIdToken(), token);
                    }
                    
                    @Override
                    public void onTokenError(String error) {
                        mCallbackContext.error(getErrorMessageInJsonString(error));
                    }
                });
            } catch (Exception ex) {
                System.out.println("Google sign in failed: " + ex);
                mCallbackContext.error(getErrorMessageInJsonString(ex.getMessage()));
            }
        } else if (requestCode == RC_ONE_TAP) {
            try {
                SignInCredential credential = mOneTapSigninClient.getSignInCredentialFromIntent(data);
                firebaseAuthWithGoogle(credential.getGoogleIdToken(), "");
            } catch(ApiException ex) {
                String errorMessage = "";
                switch (ex.getStatusCode()) {
                    case CommonStatusCodes.CANCELED:
                        errorMessage = "One Tap Signin was denied by the user.";
                        beginOneTapSigninCoolingPeriod();
                        break;
                    default:
                        errorMessage = ex.getLocalizedMessage();
                        break;
                }

                mCallbackContext.error(getErrorMessageInJsonString(errorMessage));
            } catch (Exception ex) {
                mCallbackContext.error(getErrorMessageInJsonString(ex.getMessage()));
            }
        }
    }

    private void isSignedIn(CallbackContext callbackContext) {
        boolean isSignedIn = (account != null || mAuth.getCurrentUser() != null);
        callbackContext.success(getSuccessMessageInJsonString(String.valueOf(isSignedIn)));
    }

    private void disconnect(CallbackContext callbackContext) {
        callbackContext.error(getErrorMessageInJsonString("Not available on Android."));
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
        checkIfOneTapSignInCoolingPeriodShouldBeReset();
        SharedPreferences sharedPreferences = getSharedPreferences();
        boolean shouldShowOneTapUI = sharedPreferences.getBoolean(Constants.PREF_SHOW_ONE_TAP_UI, true);

        if(shouldShowOneTapUI) {
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
                            } catch (IntentSender.SendIntentException ex) {
                                ex.printStackTrace();
                                mCallbackContext.error(getErrorMessageInJsonString(ex.getMessage()));
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception ex) {
                            mCallbackContext.error(getErrorMessageInJsonString(ex.getMessage()));
                        }
                    });
        } else {
            mCallbackContext.error(getErrorMessageInJsonString("One Tap Signin was denied by the user."));
        }
    }

    private void signOut() {
        GoogleSignInOptions gso = getGoogleSignInOptions();

        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(mContext, gso);
        mGoogleSignInClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                account = null;
                mCallbackContext.success(getSuccessMessageInJsonString("Logged out"));
                mAuth.signOut();
            }
        });
        mGoogleSignInClient.signOut().addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception ex) {
                mCallbackContext.error(getErrorMessageInJsonString(ex.getMessage()));
            }
        });
    }

    private void firebaseAuthWithGoogle(String googleIdToken, String accessToken) {
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
                                userInfo.put("access_token", accessToken);
                                mCallbackContext.success(getSuccessMessageForOneTapLogin(userInfo));
                            } catch (Exception ex) {
                                mCallbackContext.error(getErrorMessageInJsonString(ex.getMessage()));
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception ex) {
                            mCallbackContext.error(getErrorMessageInJsonString(ex.getMessage()));
                        }
                    });
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception ex) {
                mCallbackContext.error(getErrorMessageInJsonString(ex.getMessage()));
            }
        });
    }

    private GoogleSignInOptions getGoogleSignInOptions() {
        //Firas
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(new Scope("https://www.googleapis.com/auth/calendar"), new Scope("https://www.googleapis.com/auth/drive"), new Scope("https://www.googleapis.com/auth/drive.appdata"), new Scope("https://www.googleapis.com/auth/drive.readonly"), new Scope("https://www.googleapis.com/auth/drive.file"), new Scope("https://www.googleapis.com/auth/drive.metadata"), new Scope("https://www.googleapis.com/auth/drive.metadata.readonly"))
            .requestIdToken(this.cordova.getActivity().getResources().getString(getAppResource("default_client_id", "string")))
            .requestEmail()
            .build();
        return gso;
    }

    private int getAppResource(String name, String type) {
        return cordova.getActivity().getResources().getIdentifier(name, type, cordova.getActivity().getPackageName());
    }

    private void beginOneTapSigninCoolingPeriod() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        SharedPreferences.Editor preferences = sharedPreferences.edit();
        preferences.putBoolean(Constants.PREF_SHOW_ONE_TAP_UI, false);
        preferences.putLong(Constants.PREF_COOLING_START_TIME, new Date().getTime());
        preferences.apply();
    }

    private void checkIfOneTapSignInCoolingPeriodShouldBeReset() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        Date now = new Date();
        long coolingStartTime = sharedPreferences.getLong(Constants.PREF_COOLING_START_TIME, now.getTime());

        int daysApart = (int)((now.getTime() - coolingStartTime) / (1000*60*60*24l));
        if(daysApart >= 1) {
            SharedPreferences.Editor preferences = sharedPreferences.edit();
            preferences.putBoolean(Constants.PREF_SHOW_ONE_TAP_UI, true);
            preferences.putLong(Constants.PREF_COOLING_START_TIME, 0L);
            preferences.apply();
        }
    }

    private String getSuccessMessageForOneTapLogin(JSONObject userInfo) {
        try {
            JSONObject response = new JSONObject();
            response.put(Constants.JSON_STATUS, Constants.JSON_SUCCESS);
            response.put(Constants.JSON_MESSAGE, userInfo);
            return response.toString();
        } catch (JSONException e) {
            return "{\"status\": \"error\", \"message\": \"JSON error while building the response\"}";
        }
    }

    private String getSuccessMessageInJsonString(String message) {
        try {
            JSONObject response = new JSONObject();
            response.put(Constants.JSON_STATUS, Constants.JSON_SUCCESS);
            response.put(Constants.JSON_MESSAGE, message);
            return response.toString();
        } catch (JSONException e) {
            return "{\"status\": \"error\", \"message\": \"JSON error while building the response\"}";
        }
    }

    private String getErrorMessageInJsonString(String errorMessage) {
        try {
            JSONObject response = new JSONObject();
            response.put(Constants.JSON_STATUS, Constants.JSON_ERROR);
            response.put(Constants.JSON_MESSAGE, errorMessage);
            return response.toString();
        } catch (JSONException e) {
            return "{\"status\": \"error\", \"message\": \"JSON error while building the response\"}";
        }
    }

    private SharedPreferences getSharedPreferences() {
         return mContext.getSharedPreferences(Constants.PREF_FILENAME, Context.MODE_PRIVATE);
    }

    private void getAuthToken(Activity activity, Account account, boolean retry, AccessTokenCallback callback) throws Exception {
        AccountManager manager = AccountManager.get(activity);
        AccountManagerFuture<Bundle> future = manager.getAuthToken(account, "oauth2:profile email", null, activity, null, null);
        Bundle bundle = future.getResult();
        String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        try {
            // return verifyToken(authToken);
            JSONObject verifiedToken = verifyToken(authToken);
            if(callback != null) {
                callback.onToken(verifiedToken.get("accessToken"));
            }
        } catch (IOException e) {
            if (retry) {
                manager.invalidateAuthToken("com.google", authToken);
                return getAuthToken(activity, account, false, callback);
            } else {
                if(callback != null) {
                    callback.onTokenError(e.getMessage());
                }
            }
        }
    }

    private JSONObject verifyToken(String authToken) throws IOException, JSONException {
        URL url = new URL(VERIFY_TOKEN_URL+authToken);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setInstanceFollowRedirects(true);
        String stringResponse = fromStream(
            new BufferedInputStream(urlConnection.getInputStream())
        );
        /* expecting:
        {
            "issued_to": "608941808256-43vtfndets79kf5hac8ieujto8837660.apps.googleusercontent.com",
            "audience": "608941808256-43vtfndets79kf5hac8ieujto8837660.apps.googleusercontent.com",
            "user_id": "107046534809469736555",
            "scope": "https://www.googleapis.com/auth/userinfo.profile",
            "expires_in": 3595,
            "access_type": "offline"
        }*/

        Log.d("AuthenticatedBackend", "token: " + authToken + ", verification: " + stringResponse);
        JSONObject jsonResponse = new JSONObject(
            stringResponse
        );
        int expires_in = jsonResponse.getInt(FIELD_TOKEN_EXPIRES_IN);
        if (expires_in < KAssumeStaleTokenSec) {
            throw new IOException("Auth token soon expiring.");
        }
        jsonResponse.put(FIELD_ACCESS_TOKEN, authToken);
        jsonResponse.put(FIELD_TOKEN_EXPIRES, expires_in + (System.currentTimeMillis()/1000));
        return jsonResponse;
    }

    public interface AccessTokenCallback {
        public void onToken(String token);
        public void onTokenError(String error);
    }
}