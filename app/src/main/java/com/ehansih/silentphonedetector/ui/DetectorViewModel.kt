package com.ehansih.silentphonedetector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ehansih.silentphonedetector.data.repository.PermissionRepository
import com.ehansih.silentphonedetector.data.repository.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Done(val result: ScanResult) : ScanState()
    data class Error(val message: String) : ScanState()
}

class DetectorViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PermissionRepository(app)

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state

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
}
