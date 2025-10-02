package org.example.project.ui



import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.example.project.viewmodels.AuthViewModel

@Composable
fun SignInScreen(viewModel: AuthViewModel, onNavigateToSignUp: () -> Unit) {
    val uiState = viewModel.uiState

    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Welcome Back", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, "Email") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, "Password") },
            trailingIcon = {
                IconButton(onClick = viewModel::onPasswordVisibilityChange) {
                    val icon = if (uiState.passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                    Icon(icon, "Toggle password visibility")
                }
            },
            visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = viewModel::signIn, enabled = !uiState.isLoading, modifier = Modifier.fillMaxWidth()) {
            if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Sign In")
        }
        uiState.authMessage?.let { Text(it) }
        TextButton(onClick = onNavigateToSignUp) {
            Text("Don't have an account? Sign Up")
        }
    }
}
