package com.goldcalculateur.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldcalculateur.app.core.CalcMode
import com.goldcalculateur.app.core.CalculationRunner
import com.goldcalculateur.app.core.IngotRow
import com.goldcalculateur.app.core.InputParser
import com.goldcalculateur.app.core.ResultUi
import com.goldcalculateur.app.core.WorkState
import com.goldcalculateur.app.core.WorkStateCodec
import com.goldcalculateur.app.data.AppDatabase
import com.goldcalculateur.app.data.HistoryEntity
import com.goldcalculateur.app.data.SettingsRepository
import com.goldcalculateur.engine.GroupSummary
import com.goldcalculateur.engine.ScalePrecision
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val work: WorkState = WorkState.DEFAULT,
    /** Engine ingot index chosen in the "cut" dropdown; null = automatic (smallest cut). */
    val selectedCutIndex: Int? = null,
    /** Null = no valid/up-to-date result. Any edit of the ingots or target clears it (never stale). */
    val result: ResultUi? = null,
    val liveSummary: GroupSummary? = null,
    val scale: ScalePrecision = ScalePrecision.G_0_01,
    val errorRow: Int? = null,
    val restored: Boolean = false,
)

@OptIn(FlowPreview::class)
class MainViewModel(
    private val settings: SettingsRepository,
    private val db: AppDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    val history: StateFlow<List<HistoryEntity>> = db.history().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var nextId = 1L
    private var lastCountedSignature: String? = null

    init {
        viewModelScope.launch {
            val saved = WorkStateCodec.decode(settings.workState())
            lastCountedSignature = settings.lastCountedSignature()
            _state.update { s ->
                val work = saved ?: s.work
                nextId = (work.rows.maxOfOrNull { it.id } ?: 0L) + 1
                s.copy(work = work, liveSummary = CalculationRunner.liveSummary(work), restored = true)
            }
            // Auto-save the last working state (restored when the app is reopened).
            _state.map { it.work }.distinctUntilChanged().debounce(400)
                .collect { settings.saveWorkState(WorkStateCodec.encode(it)) }
        }
    }

    // ------------------------------------------------------------------ editing

    private fun editWork(transform: (WorkState) -> WorkState) = _state.update { s ->
        val work = transform(s.work)
        s.copy(
            work = work, liveSummary = CalculationRunner.liveSummary(work),
            result = null, selectedCutIndex = null, errorRow = null,
        )
    }

    fun onWeightChange(id: Long, text: String) = editWork { w ->
        w.copy(rows = w.rows.map { if (it.id == id) it.copy(weight = InputParser.sanitize(text)) else it })
    }

    fun onKaratChange(id: Long, text: String) = editWork { w ->
        w.copy(rows = w.rows.map { if (it.id == id) it.copy(karat = InputParser.sanitize(text)) else it })
    }

    fun addRow() = editWork { w -> w.copy(rows = w.rows + IngotRow(nextId++)) }

    fun removeRow(id: Long) = editWork { w ->
        val rest = w.rows.filterNot { it.id == id }
        w.copy(rows = rest.ifEmpty { listOf(IngotRow(nextId++)) })
    }

    fun onTargetChange(text: String) = editWork { it.copy(target = InputParser.sanitize(text)) }

    // Changing the method / the added karat / the cut ingot refreshes the SAME result card in place.

    fun setMode(mode: CalcMode) {
        _state.update { it.copy(work = it.work.copy(mode = mode), selectedCutIndex = null, errorRow = null) }
        recomputeIfVisible()
    }

    fun onAddedKaratChange(text: String) {
        _state.update { it.copy(work = it.work.copy(addedKarat = InputParser.sanitize(text))) }
        recomputeIfVisible()
    }

    fun selectCutIngot(engineIndex: Int) {
        _state.update { it.copy(selectedCutIndex = engineIndex) }
        recomputeIfVisible()
    }

    private fun recomputeIfVisible() {
        _state.update { s ->
            if (s.result == null) s
            else {
                val r = CalculationRunner.run(s.work, s.scale, s.selectedCutIndex)
                s.copy(result = r, errorRow = (r as? ResultUi.Failure)?.badRow)
            }
        }
    }

    // ------------------------------------------------------------------ actions

    /** Calculation is local and instant; it never waits for an ad. */
    fun calculate() {
        val s = _state.value
        val result = CalculationRunner.run(s.work, s.scale, s.selectedCutIndex)
        _state.update { it.copy(result = result, errorRow = (result as? ResultUi.Failure)?.badRow) }
        if (result.isSuccess) viewModelScope.launch { countAndRecord(s, result) }
    }

    private suspend fun countAndRecord(s: MainUiState, result: ResultUi) {
        // Re-pressing "Calculate" with identical inputs is not a new completed calculation.
        val signature = "${WorkStateCodec.encode(s.work)}#${s.scale.decimals}#${s.selectedCutIndex}"
        if (signature == lastCountedSignature) return
        lastCountedSignature = signature
        settings.recordCompletedCalculation(signature)

        val (weight, karat) = when (result) {
            is ResultUi.Added -> result.result.practicalWeight to result.result.practicalFinalKarat
            is ResultUi.Cut -> {
                val plan = result.analysis.plan ?: return
                plan.practicalCut to plan.practicalFinalKarat
            }
            is ResultUi.Failure -> return
        }
        db.history().insertAndTrim(
            HistoryEntity(
                createdAt = System.currentTimeMillis(),
                mode = s.work.mode.name,
                inputs = WorkStateCodec.encode(s.work),
                resultWeight = weight.toPlainString(),
                finalKarat = karat.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString(),
            )
        )
    }

    fun clearAll() = _state.update { s ->
        s.copy(
            work = s.work.copy(rows = listOf(IngotRow(nextId++)), target = ""),
            selectedCutIndex = null, result = null, liveSummary = null, errorRow = null,
        )
    }

    fun openHistoryEntry(id: Long, onRestored: () -> Unit) {
        viewModelScope.launch {
            val work = db.history().get(id)?.let { WorkStateCodec.decode(it.inputs) } ?: return@launch
            nextId = (work.rows.maxOfOrNull { it.id } ?: 0L) + 1
            _state.update { s ->
                val r = CalculationRunner.run(work, s.scale, null)
                s.copy(
                    work = work, selectedCutIndex = null, liveSummary = CalculationRunner.liveSummary(work),
                    result = r, errorRow = (r as? ResultUi.Failure)?.badRow,
                )
            }
            onRestored()
        }
    }

    fun deleteHistoryEntry(id: Long) { viewModelScope.launch { db.history().delete(id) } }
    fun clearHistory() { viewModelScope.launch { db.history().clear() } }
}
