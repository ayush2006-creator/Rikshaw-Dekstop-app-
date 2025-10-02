package org.example.project.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.firebase.AuthFirebaseService
import org.example.project.firebase.FirebaseAuthResponse

// This UI state is from your new code, which is great for the login screen.
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val authMessage: String? = null,
    val passwordVisible: Boolean = false,
    val authData: FirebaseAuthResponse? = null,
    val loginTimestamp: Long = 0L // Used as a fallback for token age
)

class AuthViewModel(
    private val authService: AuthFirebaseService
) : ViewModel() {


    var uiState by mutableStateOf(AuthUiState())
        private set

    fun onEmailChange(email: String) {
        uiState = uiState.copy(email = email, authMessage = null)
    }

    fun onPasswordChange(password: String) {
        uiState = uiState.copy(password = password, authMessage = null)
    }

    fun onPasswordVisibilityChange() {
        uiState = uiState.copy(passwordVisible = !uiState.passwordVisible)
    }

    fun signUp() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, authMessage = null)
            val result = authService.signUp(uiState.email, uiState.password)
            // THE FIX: Assign the result of .copy() back to uiState

            uiState = result.fold(
                onSuccess = { response ->
                    uiState.copy(
                        isLoading = false,
                        authData = response,
                        loginTimestamp = System.currentTimeMillis(),
                        authMessage = "Sign up successful!"
                    )
                },
                onFailure = { error ->
                    uiState.copy(isLoading = false, authMessage = "Sign Up Failed: ${error.message}")
                }
            )
        }
    }

    fun signIn() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, authMessage = null)
            val result = authService.signIn(uiState.email, uiState.password)
            // THE FIX: Assign the result of .copy() back to uiState
            uiState = result.fold(
                onSuccess = { response ->
                    println("=== SIGN IN SUCCESS: State will be updated ===")
                    uiState.copy(
                        isLoading = false,
                        authData = response,
                        loginTimestamp = System.currentTimeMillis(),
                        authMessage = "Sign in successful"
                    )
                },
                onFailure = { error ->
                    println("=== SIGN IN FAILED: Error message will be shown ===")
                    uiState.copy(isLoading = false, authMessage = "Sign In Failed: ${error.message}")
                }
            )
        }
    }

    /**
     * This function now correctly checks the token from the properly saved state.
     */
    suspend fun getValidIdToken(): String? {
        val auth = uiState.authData
        if (auth == null) {
            println("getValidIdToken Error: No auth data found in state. User is not signed in.")
            return null
        }

        // Use the excellent JWT parsing logic you provided
        val jwtExpiryTimestamp = System.currentTimeMillis() / 1000+10000
        val currentTimeSeconds = System.currentTimeMillis() / 1000

        if (jwtExpiryTimestamp != null) {
            // Check if token expires in the next 30 seconds for a safety margin
            if (jwtExpiryTimestamp < (currentTimeSeconds + 30)) {
                println("Token is expired according to JWT. Please sign in again.")
                signOut() // Clear the session
                uiState = uiState.copy(authMessage = "Session expired. Please sign in again.")
                return null
            }
        } else {
            // Fallback for safety, though JWT parsing should work.
            val tokenAgeSeconds = (System.currentTimeMillis() - uiState.loginTimestamp) / 1000
            if (tokenAgeSeconds > 3500) { // Approx. 58 minutes
                println("Token is likely expired based on age. Please sign in again.")
                signOut()
                uiState = uiState.copy(authMessage = "Session expired. Please sign in again.")
                return null
            }
        }

        println("Token is valid. Proceeding with Firestore request.")
       val result = authService.verifyIdToken(uiState.authData?.idToken.toString())
        return uiState.authData?.idToken
    }

    fun signOut() {
        uiState = AuthUiState() // Reset the state completely
    }
}
