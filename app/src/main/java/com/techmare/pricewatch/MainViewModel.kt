package com.techmare.pricewatch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.techmare.pricewatch.core.Change
import com.techmare.pricewatch.core.Compare
import com.techmare.pricewatch.core.Comparison
import com.techmare.pricewatch.core.Offer
import com.techmare.pricewatch.core.PricePoint
import com.techmare.pricewatch.core.Snapshot
import com.techmare.pricewatch.data.Prefs
import com.techmare.pricewatch.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val loading: Boolean = true,
    val offline: Boolean = false,
    val snapshot: Snapshot? = null,
    val comparisons: List<Comparison> = emptyList(),
    val changes: List<Change> = emptyList(),
    val threshold: Int = 20,
    val excluded: Set<String> = emptySet(),
    val notify: Boolean = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = Prefs(app)

    private val _state = MutableStateFlow(UiState(threshold = prefs.threshold, excluded = prefs.excluded, notify = prefs.notify))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (repo.hasCache()) recompute(loading = true)
            refresh()
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.refresh() }
            recompute(loading = false, offline = !ok)
        }
    }

    private suspend fun recompute(loading: Boolean = _state.value.loading, offline: Boolean = _state.value.offline) {
        val s = _state.value
        val result = withContext(Dispatchers.Default) {
            val snap = try { repo.snapshot() } catch (e: Exception) { null }
            val changes = try { repo.changes() } catch (e: Exception) { emptyList() }
            val comps = snap?.let { Compare.run(it, prefs.overrides, prefs.excluded, prefs.threshold.toDouble()) }.orEmpty()
            Triple(snap, comps, changes)
        }
        _state.value = s.copy(
            loading = loading, offline = offline, snapshot = result.first, comparisons = result.second, changes = result.third,
            threshold = prefs.threshold, excluded = prefs.excluded, notify = prefs.notify,
        )
    }

    private fun update() { viewModelScope.launch { recompute() } }

    fun reject(ownUrl: String, offer: Offer) {
        val o = prefs.overrides
        prefs.overrides = o.copy(
            rejected = o.rejected + (ownUrl to (o.rejected[ownUrl].orEmpty() + offer.url)),
            confirmed = o.confirmed + (ownUrl to (o.confirmed[ownUrl].orEmpty() - offer.url)),
        )
        update()
    }

    fun confirm(ownUrl: String, offer: Offer) {
        val o = prefs.overrides
        prefs.overrides = o.copy(
            confirmed = o.confirmed + (ownUrl to (o.confirmed[ownUrl].orEmpty() + offer.url)),
            rejected = o.rejected + (ownUrl to (o.rejected[ownUrl].orEmpty() - offer.url)),
        )
        update()
    }

    fun setThreshold(v: Int) { prefs.threshold = v; update() }

    fun setSiteIncluded(id: String, included: Boolean) {
        prefs.excluded = if (included) prefs.excluded - id else prefs.excluded + id
        update()
    }

    fun setNotify(on: Boolean) {
        prefs.notify = on
        _state.value = _state.value.copy(notify = on)
        PriceWorker.schedule(getApplication(), on)
    }

    fun search(query: String): List<Offer> = _state.value.snapshot?.let { Compare.search(it, query) }.orEmpty()

    suspend fun history(offer: Offer): List<PricePoint> = withContext(Dispatchers.IO) { repo.history(offer.siteId, offer.url) }
}
