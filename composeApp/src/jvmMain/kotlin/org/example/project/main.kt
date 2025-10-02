package org.example.project


import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.example.project.firebase.AuthFirebaseService
import org.example.project.firebase.FirestoreService

import org.example.project.ui.App
import org.example.project.viewmodels.AuthViewModel



fun main() = application {
    val authService = AuthFirebaseService()
    val firestoreService = FirestoreService()
    val authViewModel = AuthViewModel(authService)

    Window(onCloseRequest = ::exitApplication, title = "Customer Tracker") {
        App(authViewModel, firestoreService) // Let App decide when to create CustomerViewModel
    }
}
