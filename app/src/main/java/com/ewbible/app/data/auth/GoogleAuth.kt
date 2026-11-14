package com.ewbible.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class GoogleAuth(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context)
    private val WEB_CLIENT_ID = "449427316381-tqtci4os3eqilpvj8ubmvm72ocjp2ff4.apps.googleusercontent.com"

    suspend fun signInWithGoogle(): Result<String> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
            val result = credentialManager.getCredential(context, request)
            val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val name = authResult.user?.displayName ?: "User"
            val email = authResult.user?.email ?: ""
            Result.success("$name|$email")
        } catch (e: Exception) { Result.failure(e) }
    }

    fun signOut() { auth.signOut() }
    fun currentUser() = auth.currentUser
    fun isLoggedIn(): Boolean = auth.currentUser != null
}