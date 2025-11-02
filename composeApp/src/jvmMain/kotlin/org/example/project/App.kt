// FILE: App.kt
package org.example.project

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.firebase.FirestoreService
import org.example.project.navigation.Screen

import org.example.project.viewmodels.AuthViewModel
import org.example.project.viewmodels.CustomerViewModel

// NEW: Sealed class to define the main screens for navigation
sealed class MainScreen {
    object CustomerList : MainScreen()
    object PendingInstallments : MainScreen()
}

@Composable
fun App(authViewModel: AuthViewModel, firestoreService: FirestoreService) {
    var authScreen by remember { mutableStateOf<Screen>(Screen.SignUp) }
    val authState = authViewModel.uiState
    val authData = authState.authData

    val customerViewModel = remember(authData) {
        authData?.let {
            CustomerViewModel(firestoreService, it.localId)
        }
    }

    val customerState = customerViewModel?.uiState

    // NEW: State to manage which main screen is currently visible
    var currentScreen by remember { mutableStateOf<MainScreen>(MainScreen.CustomerList) }

    MaterialTheme {
        if (authState.authData != null && customerViewModel != null && customerState != null) {
            // The logic for the detail screen takes priority
            if (customerState.selectedCustomer != null) {
                _root_ide_package_.org.example.project.ui.CustomerDetailScreen(
                    customerViewModel = customerViewModel,

                    customer = customerState.selectedCustomer,
                    onBack = {
                        customerViewModel.clearSelectedCustomer()
                        // Ensure we return to the customer list
                        currentScreen = MainScreen.CustomerList
                    }
                )
            } else {
                // NEW: Navigation logic for switching between the main screens
                when (currentScreen) {
                    is MainScreen.CustomerList -> {
                        _root_ide_package_.org.example.project.ui.CustomerScreen(
                            customerViewModel = customerViewModel,
                            authViewModel = authViewModel,
                            onSignOut = authViewModel::signOut,
                            onCustomerClick = customerViewModel::selectCustomer,
                            // Pass the lambda to navigate to the pending screen
                            onNavigateToPending = { currentScreen = MainScreen.PendingInstallments }
                        )
                    }
                    is MainScreen.PendingInstallments -> {
                        _root_ide_package_.org.example.project.ui.PendingInstallmentsScreen(
                            viewModel = customerViewModel,
                            // Pass the lambda to navigate back to the list
                            onNavigateBack = { currentScreen = MainScreen.CustomerList }
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.width(400.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    when (authScreen) {
                        is Screen.SignUp -> _root_ide_package_.org.example.project.ui.SignUpScreen(
                            authViewModel
                        ) { authScreen = Screen.SignIn }
                        is Screen.SignIn -> _root_ide_package_.org.example.project.ui.SignInScreen(
                            authViewModel
                        ) { authScreen = Screen.SignUp }
                    }
                }
            }
        }
    }
}