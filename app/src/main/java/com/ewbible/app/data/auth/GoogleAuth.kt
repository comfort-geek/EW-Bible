package com.ewbible.app.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GoogleAuth(context: Context) {

    // FIX 1: Use applicationContext for CredentialManager - prevents Activity leak
    // Your old code stored whatever Context was passed in (could be an Activity)
    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(appContext)

    private val WEB_CLIENT_ID =
        "449427316381-tqtci4os3eqilpvj8ubmvm72ocjp2ff4.apps.googleusercontent.com"

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun isLoggedIn(): Boolean = auth.currentUser != null

    // FIX 2: Require Activity, not just Context
    // CredentialManager.getCredential() needs an Activity to show the bottom-sheet UI.
    // Passing LocalContext.current (which is an Activity in Compose) worked by luck.
    // Now it's explicit - compiler will stop you passing the wrong Context.
    suspend fun signInWithGoogle(activity: Activity): Result<String> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)

                val firebaseCredential = GoogleAuthProvider.getCredential(
                    googleCredential.idToken,
                    null
                )

                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user

                val name = user?.displayName ?: "User"
                val email = user?.email ?: ""

                Result.success("$name|$email")
            } else {
                Result.failure(IllegalStateException("Unexpected credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            // FIX 3: User pressed back / dismissed sheet - not a real error
            // Old code returned this as a generic failure, UI would show "Sign-in failed"
            Result.failure(UserCancelledException())
        } catch (e: NoCredentialException) {
            // FIX 4: No Google account on device / no saved credential
            Result.failure(IllegalStateException("No Google account found on this device. Add a Google account in Settings first."))
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    // So UI can check: if (e is UserCancelledException) -> just do nothing, don't show error toast
    class UserCancelledException : Exception("Sign-in cancelled")
}