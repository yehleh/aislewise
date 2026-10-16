package com.yehleh.aislewise.feature.scan

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehleh.aislewise.data.repo.ShoppingRepository
import com.yehleh.aislewise.data.repo.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface ScanPhase {
    data object Collecting : ScanPhase
    data object Reading : ScanPhase
    data class Review(val rows: List<ReviewRow>) : ScanPhase
    data class Failed(val message: String) : ScanPhase
    data object Done : ScanPhase
}

data class ScanUiState(
    val configured: Boolean,
    val photos: List<Uri> = emptyList(),
    val phase: ScanPhase = ScanPhase.Collecting,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedState: SavedStateHandle,
    private val parser: ListImageParser,
    private val matcher: ScanMatcher,
    private val storeRepo: StoreRepository,
    private val shopping: ShoppingRepository,
) : ViewModel() {

    val stores = storeRepo.observeStores().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sections = storeRepo.observeSections().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(
        ScanUiState(
            configured = parser.isConfigured,
            photos = savedState.get<ArrayList<Uri>>(KEY_PHOTOS).orEmpty(),
        ),
    )
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /** Creates a file for the camera app to write into; survives process death while the camera is open. */
    fun newCameraUri(): Uri {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        savedState[KEY_PENDING] = uri
        return uri
    }

    fun onPhotoTaken(success: Boolean) {
        val uri = savedState.get<Uri>(KEY_PENDING) ?: return
        savedState.remove<Uri>(KEY_PENDING)
        if (success) addPhotos(listOf(uri))
    }

    fun addPhotos(uris: List<Uri>) = setPhotos((_state.value.photos + uris).distinct())

    fun removePhoto(uri: Uri) = setPhotos(_state.value.photos - uri)

    private fun setPhotos(photos: List<Uri>) {
        savedState[KEY_PHOTOS] = ArrayList(photos)
        _state.update { it.copy(photos = photos, phase = ScanPhase.Collecting) }
    }

    fun read() {
        val photos = _state.value.photos
        if (photos.isEmpty()) return
        _state.update { it.copy(phase = ScanPhase.Reading) }
        viewModelScope.launch {
            val sectionList = storeRepo.getSections()
            val phase = parser.parse(photos, sectionList.map { it.name }).fold(
                onSuccess = { items ->
                    if (items.isEmpty()) {
                        ScanPhase.Failed("No list items found in these photos. Try a clearer, closer shot.")
                    } else {
                        ScanPhase.Review(matcher.match(items, sectionList, storeRepo.otherSectionId()))
                    }
                },
                onFailure = { e -> ScanPhase.Failed(e.message ?: "Something went wrong reading the list.") },
            )
            _state.update { it.copy(phase = phase) }
        }
    }

    fun backToPhotos() = _state.update { it.copy(phase = ScanPhase.Collecting) }

    fun updateRow(key: Int, transform: (ReviewRow) -> ReviewRow) = updateRows { rows ->
        rows.map { if (it.key == key) transform(it) else it }
    }

    /** Adds or removes a store on every included new item at once, for a first scan full of unknown items. */
    fun toggleStoreForNewItems(storeId: Long) = updateRows { rows ->
        val targets = rows.filter { !it.known && it.include }
        val allHave = targets.isNotEmpty() && targets.all { storeId in it.storeIds }
        rows.map { row ->
            if (row.known || !row.include) row
            else row.copy(storeIds = if (allHave) row.storeIds - storeId else row.storeIds + storeId)
        }
    }

    fun addToList() {
        val phase = _state.value.phase as? ScanPhase.Review ?: return
        viewModelScope.launch {
            phase.rows.filter { it.include && it.name.isNotBlank() }.forEach { row ->
                shopping.addToList(row.name, row.quantity, row.sectionId, row.storeIds)
            }
            cleanUpCameraFiles()
            savedState.remove<ArrayList<Uri>>(KEY_PHOTOS)
            _state.update { it.copy(phase = ScanPhase.Done) }
        }
    }

    private fun updateRows(transform: (List<ReviewRow>) -> List<ReviewRow>) = _state.update { s ->
        val phase = s.phase as? ScanPhase.Review ?: return@update s
        s.copy(phase = ScanPhase.Review(transform(phase.rows)))
    }

    private suspend fun cleanUpCameraFiles() = withContext(Dispatchers.IO) {
        File(context.cacheDir, "scans").listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val KEY_PHOTOS = "photos"
        const val KEY_PENDING = "pendingCameraUri"
    }
}
