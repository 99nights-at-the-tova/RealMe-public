package com.realme.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realme.core.model.AuthSession
import com.realme.core.model.UserProfile
import com.realme.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val isCheckingSession: Boolean = true,
    val session: AuthSession? = null,
    val localAccounts: List<UserProfile> = emptyList(),
    val pinProtectedAccountIds: Set<String> = emptySet(),
    val pendingPinAccount: UserProfile? = null,
    val lockedAccount: UserProfile? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val submission = MutableStateFlow(Submission())

    init {
        viewModelScope.launch {
            val session = repository.session.first()
            val locked = if (session != null && repository.requiresPin(session.userId)) {
                repository.localAccounts.first().firstOrNull { it.id == session.userId }
                    ?: UserProfile(session.userId, "ローカルアカウント")
            } else {
                null
            }
            submission.value = Submission(lockChecked = true, lockedAccount = locked)
        }
    }

    val uiState = combine(
        repository.session,
        repository.localAccounts,
        repository.pinProtectedAccountIds,
        submission,
    ) { session, accounts, protectedIds, submission ->
        AuthUiState(
            isCheckingSession = !submission.lockChecked,
            session = session,
            localAccounts = accounts,
            pinProtectedAccountIds = protectedIds,
            pendingPinAccount = submission.pendingPinAccount,
            lockedAccount = submission.lockedAccount,
            isSubmitting = submission.loading,
            error = submission.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthUiState())

    fun signIn(displayName: String) {
        if (submission.value.loading) return
        viewModelScope.launch {
            submission.value = Submission(loading = true, lockChecked = true)
            repository.signInAnonymously(displayName)
                .onSuccess { submission.value = Submission(lockChecked = true) }
                .onFailure {
                    submission.value = Submission(
                        loading = false,
                        lockChecked = true,
                        error = it.message ?: "サインインできませんでした",
                    )
                }
        }
    }

    fun clearError() {
        submission.value = submission.value.copy(error = null)
    }

    fun selectAccount(userId: String) {
        if (submission.value.loading) return
        viewModelScope.launch {
            val account = uiState.value.localAccounts.firstOrNull { it.id == userId } ?: return@launch
            if (repository.requiresPin(userId)) {
                submission.value = Submission(lockChecked = true, pendingPinAccount = account)
            } else {
                signInExisting(userId, null, null)
            }
        }
    }

    fun submitPin(pin: String) {
        val account = submission.value.pendingPinAccount ?: return
        signInExisting(account.id, pin, account)
    }

    fun cancelPinDialog() {
        submission.value = submission.value.copy(pendingPinAccount = null, error = null)
    }

    fun unlock(pin: String) {
        val account = submission.value.lockedAccount ?: return
        if (submission.value.loading) return
        viewModelScope.launch {
            submission.value = submission.value.copy(loading = true, error = null)
            repository.verifyPin(account.id, pin)
                .onSuccess { submission.value = Submission(lockChecked = true) }
                .onFailure {
                    submission.value = submission.value.copy(
                        loading = false,
                        error = it.message ?: "PINが正しくありません",
                    )
                }
        }
    }

    fun signOutFromLock() {
        viewModelScope.launch {
            repository.signOut()
            submission.value = Submission(lockChecked = true)
        }
    }

    private fun signInExisting(userId: String, pin: String?, pendingAccount: UserProfile?) {
        if (submission.value.loading) return
        viewModelScope.launch {
            submission.value = Submission(
                loading = true,
                lockChecked = true,
                pendingPinAccount = pendingAccount,
            )
            repository.signInExisting(userId, pin)
                .onSuccess { submission.value = Submission(lockChecked = true) }
                .onFailure {
                    submission.value = Submission(
                        lockChecked = true,
                        error = it.message ?: "アカウントを開けませんでした",
                        pendingPinAccount = pendingAccount,
                    )
                }
        }
    }

    private data class Submission(
        val loading: Boolean = false,
        val error: String? = null,
        val lockChecked: Boolean = false,
        val pendingPinAccount: UserProfile? = null,
        val lockedAccount: UserProfile? = null,
    )
}
