package com.example.chat.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.data.local.SavedAccountManager
import com.example.chat.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class AuthViewModel : ViewModel() {
    private val authRepository = AuthRepository()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun isUserLoggedIn(): Boolean = authRepository.isUserLoggedIn()

    fun login(context: Context, emailOrUsername: String, password: String, onNavMain: () -> Unit) {
        val email = if (!emailOrUsername.contains("@")) {
            "${emailOrUsername.trim()}@talky.com"
        } else {
            emailOrUsername.trim()
        }
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in all fields") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                val userData = authRepository.getCurrentUserData()
                SavedAccountManager.saveAccount(
                    context,
                    email,
                    userData?.displayName ?: email.substringBefore("@"),
                    userData?.photoUrl ?: "",
                    password
                )
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                onNavMain()
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Login failed") }
            }
        }
    }

    fun register(context: Context, displayName: String, emailOrUsername: String, password: String, confirmPassword: String, onNavMain: () -> Unit) {
        if (displayName.isBlank() || emailOrUsername.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in all fields") }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.register(displayName.trim(), emailOrUsername.trim(), password)
            if (result.isSuccess) {
                val email = if (!emailOrUsername.contains("@")) "${emailOrUsername.trim()}@talky.com" else emailOrUsername.trim()
                SavedAccountManager.saveAccount(
                    context,
                    email,
                    displayName.trim(),
                    "",
                    password
                )
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                onNavMain()
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Registration failed") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
