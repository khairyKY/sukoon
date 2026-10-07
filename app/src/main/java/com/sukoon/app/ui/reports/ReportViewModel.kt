package com.sukoon.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.reports.Agp
import com.sukoon.app.reports.AgpReport
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ReportUiState(val days: Int = 14, val report: AgpReport? = null, val loading: Boolean = true)

class ReportViewModel(glucose: GlucoseRepository) : ViewModel() {

    private val days = MutableStateFlow(14)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportUiState> = days.flatMapLatest { d ->
        glucose.readingsSince(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(d.toLong()))
            .conflate()
            .map { readings -> ReportUiState(d, Agp.build(readings, Instant.now(), ZoneId.systemDefault(), d), loading = false) }
    }
        .flowOn(Dispatchers.Default) // 30 days is ~43k readings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ReportUiState())

    fun selectDays(value: Int) {
        days.value = value
    }

    companion object {
        fun factory(glucose: GlucoseRepository) = viewModelFactory {
            initializer { ReportViewModel(glucose) }
        }
    }
}
