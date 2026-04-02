package com.ehansih.silentphonedetector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ehansih.silentphonedetector.data.repository.PermissionRepository
import com.ehansih.silentphonedetector.data.repository.ScanResult
import com.ehansih.silentphonedetector.data.scanner.BreachResult
import com.ehansih.silentphonedetector.data.scanner.BreachScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Done(val result: ScanResult) : ScanState()
    data class Error(val message: String) : ScanState()
}

sealed class BreachState {
    object Idle : BreachState()
    object Checking : BreachState()
    data class Done(val result: BreachResult) : BreachState()
}

class DetectorViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PermissionRepository(app)
    private val breachScanner = BreachScanner()

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state

    private val _breachState = MutableStateFlow<BreachState>(BreachState.Idle)
    val breachState: StateFlow<BreachState> = _breachState

    fun scan() {
        if (_state.value is ScanState.Scanning) return
        viewModelScope.launch {
            _state.value = ScanState.Scanning
            _state.value = try {
                ScanState.Done(repository.scan())
            } catch (e: Exception) {
                ScanState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun checkPassword(password: String) {
        if (_breachState.value is BreachState.Checking) return
        viewModelScope.launch {
            _breachState.value = BreachState.Checking
            _breachState.value = BreachState.Done(breachScanner.checkPassword(password))
        }
    }

    fun resetBreachState() {
        _breachState.value = BreachState.Idle
    }
}
