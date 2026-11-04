package com.ppm.momentum.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Gets a Google ID token with Credential Manager. Requires an OAuth *web* client id (public value) and an
 * Android OAuth client for this package + signing SHA-1 in the same Google Cloud project. See docs/GOOGLE_SIGNIN.md.
 * The ID token is sent to our backend, which verifies it; the app never sees Google credentials.
 */
object GoogleSignIn {
    suspend fun getIdToken(activityContext: Context, webClientId: String): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type")
    }
}
