package com.learn.apigateway.auth;

// Carries Cognito's own error shape (on failure, Cognito's JSON API responds with
// { "__type": "NotAuthorizedException", "message": "..." }) one level up out of
// CognitoAuthClient, so AuthController can decide the right HTTP status to hand back to
// our own frontend -- NotAuthorizedException (wrong password, or -- for refresh -- an
// expired/revoked refresh token) is a 401; anything else is treated as a plain 400.
public class CognitoAuthException extends RuntimeException {

    private final String cognitoErrorType;

    public CognitoAuthException(String cognitoErrorType, String message) {
        super(message);
        this.cognitoErrorType = cognitoErrorType;
    }

    public String getCognitoErrorType() {
        return cognitoErrorType;
    }
}
