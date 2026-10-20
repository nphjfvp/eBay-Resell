package de.dealcheck.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.dealcheck.app.analysis.AnalysisParser
import de.dealcheck.app.analysis.DealAnalysis
import de.dealcheck.app.analysis.DealCheckException
import de.dealcheck.app.analysis.ProfitCalc
import de.dealcheck.app.data.AnalysisEntity
import de.dealcheck.app.data.AnalysisRepository
import de.dealcheck.app.data.AppSettings
import de.dealcheck.app.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewAnalysisState(
    val imagePaths: List<String> = emptyList(),
    val note: String = "",
    val importing: Boolean = false,
    val analyzing: Boolean = false,
    val error: String? = null,
)

class NewAnalysisViewModel(
    private val repo: AnalysisRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NewAnalysisState())
    val state: StateFlow<NewAnalysisState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepo.settings

    private val _finished = Channel<Long>(Channel.BUFFERED)
    /** ID der gespeicherten Analyse, sobald sie fertig ist. */
    val finished = _finished.receiveAsFlow()

    private val _sharedImages = Channel<Unit>(Channel.CONFLATED)
    /** Meldet, dass Bilder per „Teilen“ angekommen sind (Navigation zum Analyse-Tab). */
    val sharedImages = _sharedImages.receiveAsFlow()

    init {
        viewModelScope.launch { runCatching { repo.cleanupImages() } }
    }

    fun addImages(uris: List<Uri>, fromShare: Boolean = false) {
        if (uris.isEmpty()) return
        if (fromShare) _sharedImages.trySend(Unit)
        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null) }
            val errors = mutableListOf<String>()
            for (uri in uris) {
                if (_state.value.imagePaths.size >= MAX_IMAGES) {
                    errors += "Maximal $MAX_IMAGES Bilder pro Analyse."
                    break
                }
                try {
                    val path = repo.importImage(uri)
                    _state.update { it.copy(imagePaths = it.imagePaths + path) }
                } catch (e: DealCheckException) {
                    errors += e.message.orEmpty()
                }
            }
            _state.update { it.copy(importing = false, error = errors.distinct().joinToString("\n").ifBlank { null }) }
        }
    }

    fun removeImage(path: String) {
        if (_state.value.analyzing) return
        _state.update { it.copy(imagePaths = it.imagePaths - path) }
        repo.discardImage(path)
    }

    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun analyze() {
        val s = _state.value
        if (s.analyzing || s.imagePaths.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(analyzing = true, error = null) }
            try {
                val id = repo.analyze(s.imagePaths, s.note)
                _state.value = NewAnalysisState()
                _finished.send(id)
            } catch (e: DealCheckException) {
                _state.update { it.copy(analyzing = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(analyzing = false, error = "Unerwarteter Fehler: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    companion object {
        const val MAX_IMAGES = 6
    }
}

class HistoryViewModel(repo: AnalysisRepository) : ViewModel() {
    val items: StateFlow<List<AnalysisEntity>?> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class CalcFields(
    val purchase: String = "",
    val shipping: String = "",
    val parts: String = "",
    val sale: String = "",
)

class ResultViewModel(private val repo: AnalysisRepository, private val id: Long) : ViewModel() {

    data class Loaded(val entity: AnalysisEntity, val analysis: DealAnalysis)

    /** null = lädt noch, Result.failure = nicht gefunden / defekt. */
    val result: StateFlow<Result<Loaded>?> = repo.observe(id)
        .map { e ->
            if (e == null) Result.failure(IllegalStateException("Analyse nicht gefunden"))
            else runCatching { Loaded(e, AnalysisParser.parse(e.resultJson, strict = false)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _calc = MutableStateFlow<CalcFields?>(null)
    val calc: StateFlow<CalcFields?> = _calc.asStateFlow()

    private var saveJob: Job? = null

    /** Füllt die Rechnung einmalig vor: gespeicherte Werte oder Schätzung aus der Analyse. */
    fun initCalc(loaded: Loaded) {
        if (_calc.value != null) return
        val e = loaded.entity
        val a = loaded.analysis
        _calc.value = CalcFields(
            purchase = formatInput(e.calcPurchase ?: a.angebotspreisEur),
            shipping = formatInput(e.calcShipping ?: a.versandEur),
            parts = formatInput(e.calcParts ?: ProfitCalc.midpoint(a.reparaturKostenMinEur, a.reparaturKostenMaxEur)),
            sale = formatInput(e.calcSale ?: ProfitCalc.midpoint(a.verkaufspreisRepariertMinEur, a.verkaufspreisRepariertMaxEur)),
        )
    }

    fun updateCalc(fields: CalcFields) {
        _calc.value = fields
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            val p = ProfitCalc
            repo.updateCalc(id, p.parseInput(fields.purchase), p.parseInput(fields.shipping), p.parseInput(fields.parts), p.parseInput(fields.sale))
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            saveJob?.cancel()
            repo.delete(id)
            onDone()
        }
    }
}

class SettingsViewModel(private val settingsRepo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepo.settings
    fun save(s: AppSettings) = settingsRepo.update(s)
}

