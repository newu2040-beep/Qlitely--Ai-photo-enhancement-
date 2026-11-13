package com.example.core.data

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.core.domain.AppPreferences
import com.example.core.domain.AspectRatioOption
import com.example.core.domain.EnhancementAdjustments
import com.example.core.domain.EnhancementJob
import com.example.core.domain.EnhancementPreset
import com.example.core.domain.ExportFormat
import com.example.core.domain.ImportedImageMeta
import com.example.core.domain.LibraryAsset
import com.example.core.domain.OutputResolution
import com.example.core.domain.PhotoFilter
import com.example.core.domain.ProcessingMode
import com.example.core.image.CloudAiEnhancementEngine
import com.example.core.image.DeviceCapabilities
import com.example.core.image.ImageImportManager
import com.example.core.image.LocalEnhancementEngine
import com.example.core.image.MediaExportManager
import com.example.core.image.NotificationHelper
import com.example.core.image.PhotoDiagnosticReport
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class EditorUiState(
    val importedMeta: ImportedImageMeta? = null,
    val processingMode: ProcessingMode = ProcessingMode.LOCAL,
    val resolution: OutputResolution = OutputResolution.UHD_4K,
    val aspectRatio: AspectRatioOption = AspectRatioOption.ORIGINAL,
    val customWidth: Int? = null,
    val customHeight: Int? = null,
    val aspectLock: Boolean = true,
    val adjustments: EnhancementAdjustments = EnhancementAdjustments(),
    val filterThumbnails: Map<PhotoFilter, Bitmap> = emptyMap(),
    val exportFormat: ExportFormat = ExportFormat.JPEG,
    val jpegQuality: Int = 92,
    val isImporting: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isAutoTuning: Boolean = false,
    val diagnosticReport: PhotoDiagnosticReport? = null,
    val isProcessing: Boolean = false,
    val processingStage: String = "",
    val processedPreviewPath: String? = null,
    val errorMessage: String? = null,
    val showCloudConsentSheet: Boolean = false,
    val pendingPreset: EnhancementPreset? = null
)

data class ResultUiState(
    val asset: LibraryAsset? = null,
    val isSavingToGallery: Boolean = false,
    val isSavingCopy: Boolean = false,
    val lastProcessingTimeMs: Long? = null,
    val errorMessage: String? = null
)

class QltyMainViewModel(
    private val appContext: Context,
    private val repository: QltyFirestoreRepository,
    val currentUserId: String,
    userDisplayName: String,
    userEmail: String,
    userPhotoUrl: String?
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = repository.observePreferences(currentUserId)
        .catch { error ->
            Log.w(TAG, "Error observing preferences", error)
            emit(AppPreferences(id = "app", userId = currentUserId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppPreferences(id = "app", userId = currentUserId)
        )

    val libraryState: StateFlow<UiState<List<LibraryAsset>>> = repository.observeAssets(currentUserId)
        .map<List<LibraryAsset>, UiState<List<LibraryAsset>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing library assets", root)
            emit(UiState.Error(root.message ?: "Failed to load your library."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val jobsState: StateFlow<List<EnhancementJob>> = repository.observeJobs(currentUserId)
        .catch { error ->
            Log.w(TAG, "Error observing jobs", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private val _resultState = MutableStateFlow(ResultUiState())
    val resultState: StateFlow<ResultUiState> = _resultState.asStateFlow()

    private val _toastMessage = MutableStateFlow<Pair<String, Boolean>?>(null)
    val toastMessage: StateFlow<Pair<String, Boolean>?> = _toastMessage.asStateFlow()

    private var activeProcessJob: Job? = null
    private var filterThumbJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(
                displayName = userDisplayName.ifBlank { "QLITEY Creator" },
                email = userEmail,
                photoUrl = userPhotoUrl
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        recycleFilterThumbnails(_editorState.value.filterThumbnails)
    }

    private fun recycleFilterThumbnails(thumbs: Map<PhotoFilter, Bitmap>) {
        thumbs.values.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    fun showToast(message: String, isError: Boolean = false) {
        viewModelScope.launch {
            _toastMessage.value = message to isError
            delay(2800L)
            if (_toastMessage.value?.first == message) {
                _toastMessage.value = null
            }
        }
    }

    fun inspectDeviceCapabilities(): DeviceCapabilities {
        return LocalEnhancementEngine.inspectCapabilities(appContext)
    }

    fun isNetworkConnected(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun setPendingPreset(preset: EnhancementPreset?) {
        _editorState.update { it.copy(pendingPreset = preset) }
    }

    fun importPickedImage(uri: Uri, onReadyForEditor: () -> Unit) {
        viewModelScope.launch {
            val prefs = preferences.value
            val preset = _editorState.value.pendingPreset ?: EnhancementPreset.AI_ENHANCE
            _editorState.update { it.copy(isImporting = true, errorMessage = null) }

            when (val res = ImageImportManager.importFromUri(appContext, uri)) {
                is ImageImportManager.ImportResult.Success -> {
                    val meta = res.meta
                    val mode = if (_editorState.value.pendingPreset != null) {
                        preset.defaultMode
                    } else {
                        ProcessingMode.fromWire(prefs.defaultProcessingMode)
                    }
                    val resolution = if (_editorState.value.pendingPreset != null) {
                        preset.defaultResolution
                    } else {
                        OutputResolution.fromWire(prefs.defaultResolution)
                    }
                    val format = ExportFormat.fromWire(prefs.defaultFormat)

                    val previousThumbs = _editorState.value.filterThumbnails
                    _editorState.value = EditorUiState(
                        importedMeta = meta,
                        processingMode = mode,
                        resolution = resolution,
                        aspectRatio = AspectRatioOption.ORIGINAL,
                        customWidth = meta.width,
                        customHeight = meta.height,
                        adjustments = EnhancementAdjustments(
                            strength = preset.strength,
                            denoise = preset.denoise,
                            sharpen = preset.sharpen,
                            colorRecovery = preset.colorRecovery,
                            contrastBoost = preset.contrastBoost,
                            preset = preset
                        ),
                        exportFormat = format,
                        isImporting = false,
                        pendingPreset = null
                    )
                    recycleFilterThumbnails(previousThumbs)
                    generateFilterPreviewsAsync(meta.localOriginalPath)
                    onReadyForEditor()
                }
                is ImageImportManager.ImportResult.UnsupportedFormat -> {
                    _editorState.update { it.copy(isImporting = false) }
                    showToast(
                        "Unsupported format (${res.mimeType}). Accepted: JPEG, PNG, WebP, HEIC.",
                        isError = true
                    )
                }
                is ImageImportManager.ImportResult.TooLarge -> {
                    _editorState.update { it.copy(isImporting = false) }
                    val mb = res.byteSize / (1024 * 1024)
                    showToast(
                        "Image is $mb MB (max 35 MB). Select a smaller photo.",
                        isError = true
                    )
                }
                is ImageImportManager.ImportResult.Error -> {
                    _editorState.update { it.copy(isImporting = false) }
                    showToast(res.message, isError = true)
                }
            }
        }
    }

    private fun generateFilterPreviewsAsync(originalPath: String) {
        filterThumbJob?.cancel()
        filterThumbJob = viewModelScope.launch {
            val generated = runCatching {
                LocalEnhancementEngine.generateFilterThumbnails(originalPath)
            }.getOrDefault(emptyMap())
            val old = _editorState.value.filterThumbnails
            _editorState.update { it.copy(filterThumbnails = generated) }
            if (old.isNotEmpty() && old !== generated) {
                recycleFilterThumbnails(old)
            }
        }
    }

    fun selectProcessingMode(mode: ProcessingMode) {
        _editorState.update { it.copy(processingMode = mode, errorMessage = null) }
    }

    fun selectResolution(resolution: OutputResolution) {
        _editorState.update { state ->
            val meta = state.importedMeta
            val (w, h) = if (meta != null && resolution == OutputResolution.CUSTOM) {
                (state.customWidth ?: meta.width) to (state.customHeight ?: meta.height)
            } else {
                state.customWidth to state.customHeight
            }
            state.copy(resolution = resolution, customWidth = w, customHeight = h)
        }
    }

    fun selectAspectRatio(ratio: AspectRatioOption) {
        _editorState.update { it.copy(aspectRatio = ratio, processedPreviewPath = null) }
    }

    fun updateCustomDimensions(width: Int?, height: Int?) {
        _editorState.update { state ->
            val meta = state.importedMeta
            if (state.aspectLock && meta != null) {
                val (cropW, cropH) = LocalEnhancementEngine.computeCroppedDimensions(
                    meta.width,
                    meta.height,
                    state.aspectRatio
                )
                val aspect = cropW.toFloat() / cropH.coerceAtLeast(1).toFloat()
                when {
                    width != null && width != state.customWidth -> {
                        val clampedW = width.coerceIn(64, 4096)
                        val lockedH = (clampedW / aspect).toInt().coerceIn(64, 4096)
                        state.copy(customWidth = clampedW, customHeight = lockedH)
                    }
                    height != null && height != state.customHeight -> {
                        val clampedH = height.coerceIn(64, 4096)
                        val lockedW = (clampedH * aspect).toInt().coerceIn(64, 4096)
                        state.copy(customWidth = lockedW, customHeight = clampedH)
                    }
                    else -> state
                }
            } else {
                state.copy(
                    customWidth = width?.coerceIn(64, 4096) ?: state.customWidth,
                    customHeight = height?.coerceIn(64, 4096) ?: state.customHeight
                )
            }
        }
    }

    fun toggleAspectLock(locked: Boolean) {
        _editorState.update { it.copy(aspectLock = locked) }
    }

    fun selectFilter(filter: PhotoFilter) {
        _editorState.update { state ->
            state.copy(
                adjustments = state.adjustments.copy(filter = filter),
                processedPreviewPath = null,
                errorMessage = null
            )
        }
    }

    fun applyPreset(preset: EnhancementPreset) {
        _editorState.update {
            it.copy(
                adjustments = it.adjustments.copy(
                    strength = preset.strength,
                    denoise = preset.denoise,
                    sharpen = preset.sharpen,
                    colorRecovery = preset.colorRecovery,
                    contrastBoost = preset.contrastBoost,
                    exposure = 0f,
                    highlights = 0f,
                    shadows = 0f,
                    warmth = if (preset.warmRestore) 0.14f else 0f,
                    vignette = 0f,
                    preset = preset
                ),
                processedPreviewPath = null,
                errorMessage = null
            )
        }
    }

    fun updateAdjustments(transform: (EnhancementAdjustments) -> EnhancementAdjustments) {
        _editorState.update {
            it.copy(
                adjustments = transform(it.adjustments),
                processedPreviewPath = null
            )
        }
    }

    fun autoTuneAdjustments() {
        val meta = _editorState.value.importedMeta ?: return
        viewModelScope.launch {
            _editorState.update { it.copy(isAutoTuning = true, errorMessage = null) }
            val tuned = withContext(Dispatchers.Default) {
                val sample = ImageImportManager.decodeOrientedBitmap(meta.localOriginalPath, maxDimension = 720)
                if (sample != null) {
                    try {
                        LocalEnhancementEngine.autoAnalyzeHistogram(sample, _editorState.value.adjustments)
                    } finally {
                        sample.recycle()
                    }
                } else {
                    null
                }
            }
            if (tuned != null) {
                _editorState.update {
                    it.copy(
                        isAutoTuning = false,
                        adjustments = tuned,
                        processedPreviewPath = null
                    )
                }
                showToast("Histogram Auto-Tone applied")
            } else {
                _editorState.update { it.copy(isAutoTuning = false) }
                showToast("Could not analyze image histogram", isError = true)
            }
        }
    }

    fun resetAdjustments() {
        _editorState.update {
            val base = EnhancementPreset.NATURAL_CLARITY
            it.copy(
                aspectRatio = AspectRatioOption.ORIGINAL,
                resolution = OutputResolution.ORIGINAL,
                adjustments = EnhancementAdjustments(
                    strength = base.strength,
                    exposure = 0f,
                    contrastBoost = base.contrastBoost,
                    highlights = 0f,
                    shadows = 0f,
                    colorRecovery = base.colorRecovery,
                    warmth = 0f,
                    sharpen = base.sharpen,
                    denoise = base.denoise,
                    vignette = 0f,
                    filter = PhotoFilter.NATURAL,
                    preset = base
                ),
                processedPreviewPath = null,
                errorMessage = null
            )
        }
        showToast("Adjustments & filter reset")
    }

    fun selectExportFormat(format: ExportFormat) {
        _editorState.update { it.copy(exportFormat = format) }
    }

    fun updateJpegQuality(quality: Int) {
        _editorState.update { it.copy(jpegQuality = quality.coerceIn(50, 100)) }
    }

    fun dismissCloudConsentSheet() {
        _editorState.update { it.copy(showCloudConsentSheet = false) }
    }

    fun grantCloudConsentAndEnhance(onCompleted: () -> Unit) {
        viewModelScope.launch {
            val updated = preferences.value.copy(cloudAiConsentGranted = true)
            repository.savePreferences(updated)
            _editorState.update { it.copy(showCloudConsentSheet = false) }
            startEnhancement(onCompleted)
        }
    }

    fun runCloudSmartDiagnosis() {
        val meta = _editorState.value.importedMeta ?: return
        if (!isNetworkConnected()) {
            showToast("No internet connection for Cloud AI diagnostics.", isError = true)
            return
        }
        viewModelScope.launch {
            _editorState.update { it.copy(isAnalyzing = true, errorMessage = null) }
            val result = CloudAiEnhancementEngine.analyzePhoto(meta.localOriginalPath)
            result.onSuccess { report ->
                val recommended = EnhancementPreset.fromId(report.recommendedPresetId)
                _editorState.update {
                    it.copy(
                        isAnalyzing = false,
                        diagnosticReport = report,
                        adjustments = it.adjustments.copy(
                            preset = recommended,
                            denoise = report.recommendedDenoise,
                            sharpen = report.recommendedSharpen,
                            colorRecovery = report.recommendedColorRecovery,
                            exposure = report.recommendedExposure,
                            contrastBoost = report.recommendedContrast
                        ),
                        processedPreviewPath = null
                    )
                }
                showToast("AI diagnosis applied: ${recommended.title}")
            }.onFailure { err ->
                _editorState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = err.localizedMessage ?: "Cloud AI analysis failed."
                    )
                }
            }
        }
    }

    fun startEnhancement(onCompleted: () -> Unit) {
        val state = _editorState.value
        val meta = state.importedMeta ?: run {
            showToast("Please import a photo first.", isError = true)
            return
        }

        if (state.processingMode == ProcessingMode.CLOUD) {
            if (!isNetworkConnected()) {
                _editorState.update {
                    it.copy(
                        errorMessage = "Internet connection is offline. Switch to Local AI to process on-device."
                    )
                }
                return
            }
            if (!preferences.value.cloudAiConsentGranted) {
                _editorState.update { it.copy(showCloudConsentSheet = true) }
                return
            }
        }

        activeProcessJob?.cancel()
        activeProcessJob = viewModelScope.launch {
            val modelId = if (state.processingMode == ProcessingMode.CLOUD) {
                CloudAiEnhancementEngine.MODEL_IMAGE_ENHANCE
            } else {
                "local_dsp_lanczos_unsharp_v2"
            }

            _editorState.update {
                it.copy(
                    isProcessing = true,
                    processingStage = "Initializing ${state.processingMode.label}",
                    errorMessage = null
                )
            }

            val jobIdResult = repository.createJob(
                sourceName = meta.displayName,
                sourceChecksum = meta.checksumSha256,
                mode = state.processingMode.wire,
                modelId = modelId,
                presetId = state.adjustments.preset.id,
                inputWidth = meta.width,
                inputHeight = meta.height,
                format = state.exportFormat.wire
            )
            val jobId = jobIdResult.getOrNull() ?: "local_${System.currentTimeMillis()}"

            val processResult = if (state.processingMode == ProcessingMode.CLOUD) {
                CloudAiEnhancementEngine.enhanceInCloud(
                    context = appContext,
                    originalPath = meta.localOriginalPath,
                    aspectRatio = state.aspectRatio,
                    resolution = state.resolution,
                    customWidth = state.customWidth,
                    customHeight = state.customHeight,
                    adjustments = state.adjustments,
                    format = state.exportFormat,
                    jpegQuality = state.jpegQuality,
                    onStage = { stage ->
                        _editorState.update { it.copy(processingStage = stage) }
                    }
                )
            } else {
                LocalEnhancementEngine.enhanceLocally(
                    context = appContext,
                    originalPath = meta.localOriginalPath,
                    aspectRatio = state.aspectRatio,
                    resolution = state.resolution,
                    customWidth = state.customWidth,
                    customHeight = state.customHeight,
                    adjustments = state.adjustments,
                    format = state.exportFormat,
                    jpegQuality = state.jpegQuality,
                    onStage = { stage ->
                        _editorState.update { it.copy(processingStage = stage) }
                    }
                )
            }

            processResult.onSuccess { output ->
                repository.completeJob(
                    jobId = jobId,
                    outputWidth = output.outputWidth,
                    outputHeight = output.outputHeight
                )

                val autoSaveEnabled = preferences.value.autoSave
                var savedToGallery = false
                if (autoSaveEnabled) {
                    val exportRes = MediaExportManager.saveToGallery(
                        context = appContext,
                        localOutputPath = output.outputPath,
                        displayName = "${meta.displayName}_enhanced",
                        format = state.exportFormat
                    )
                    savedToGallery = exportRes.isSuccess
                }

                val resLabel = computeTruthfulResolutionLabel(
                    requested = state.resolution,
                    outW = output.outputWidth,
                    outH = output.outputHeight
                )

                val newAsset = LibraryAsset(
                    userId = currentUserId,
                    jobId = jobId,
                    displayName = meta.displayName,
                    mode = state.processingMode.wire,
                    resolutionLabel = resLabel,
                    inputWidth = meta.width.toLong(),
                    inputHeight = meta.height.toLong(),
                    outputWidth = output.outputWidth.toLong(),
                    outputHeight = output.outputHeight.toLong(),
                    format = state.exportFormat.wire,
                    byteSize = output.byteSize,
                    localOutputPath = output.outputPath,
                    localOriginalPath = meta.localOriginalPath,
                    thumbnailPath = output.thumbnailPath,
                    isFavorite = false,
                    isSavedToGallery = savedToGallery,
                    presetUsed = state.adjustments.preset.id
                )

                val createdId = repository.createAsset(newAsset).getOrElse { "" }
                val finalAsset = newAsset.copy(id = createdId)

                _editorState.update {
                    it.copy(
                        isProcessing = false,
                        processingStage = "Complete",
                        processedPreviewPath = output.outputPath
                    )
                }
                _resultState.value = ResultUiState(
                    asset = finalAsset,
                    lastProcessingTimeMs = output.processingTimeMs
                )

                NotificationHelper.notifyEnhancementComplete(
                    context = appContext,
                    title = "QLITEY Enhancement Complete",
                    body = "${meta.displayName} (${output.outputWidth}×${output.outputHeight}) finished in ${String.format("%.1fs", output.processingTimeMs / 1000f)}"
                )

                if (savedToGallery) {
                    showToast("Enhanced and auto-saved to Gallery!")
                }
                onCompleted()
            }.onFailure { err ->
                if (err is CancellationException) throw err
                val msg = err.localizedMessage ?: "Enhancement failed."
                repository.failOrCancelJob(
                    jobId = jobId,
                    status = "failed",
                    stage = "Enhancement failed",
                    errorCode = msg.take(120)
                )
                _editorState.update {
                    it.copy(
                        isProcessing = false,
                        processingStage = "",
                        errorMessage = msg
                    )
                }
            }
        }
    }

    fun cancelActiveProcessing() {
        activeProcessJob?.cancel()
        activeProcessJob = null
        _editorState.update { it.copy(isProcessing = false, processingStage = "") }
        showToast("Processing canceled.")
    }

    fun openResultForAsset(asset: LibraryAsset) {
        _resultState.value = ResultUiState(asset = asset)
    }

    fun loadAssetIntoEditor(asset: LibraryAsset, onReady: () -> Unit) {
        val origFile = File(asset.localOriginalPath)
        if (!origFile.exists()) {
            showToast("Original file expired on this device; cannot re-enhance.", isError = true)
            return
        }
        val preset = EnhancementPreset.fromId(asset.presetUsed)
        val previousThumbs = _editorState.value.filterThumbnails
        _editorState.value = EditorUiState(
            importedMeta = ImportedImageMeta(
                sourceUriString = "",
                localOriginalPath = asset.localOriginalPath,
                displayName = asset.displayName,
                width = asset.inputWidth.toInt().coerceAtLeast(1),
                height = asset.inputHeight.toInt().coerceAtLeast(1),
                mimeType = ExportFormat.fromWire(asset.format).mimeType,
                byteSize = origFile.length(),
                checksumSha256 = asset.jobId,
                hasAlpha = asset.format == ExportFormat.PNG.wire
            ),
            processingMode = ProcessingMode.fromWire(asset.mode),
            resolution = when (asset.resolutionLabel) {
                "Original" -> OutputResolution.ORIGINAL
                "2x" -> OutputResolution.X2
                "4K" -> OutputResolution.UHD_4K
                else -> OutputResolution.CUSTOM
            },
            exportFormat = ExportFormat.fromWire(asset.format),
            adjustments = EnhancementAdjustments(
                strength = preset.strength,
                denoise = preset.denoise,
                sharpen = preset.sharpen,
                colorRecovery = preset.colorRecovery,
                contrastBoost = preset.contrastBoost,
                preset = preset
            ),
            processedPreviewPath = asset.localOutputPath.takeIf { File(it).exists() }
        )
        recycleFilterThumbnails(previousThumbs)
        generateFilterPreviewsAsync(asset.localOriginalPath)
        onReady()
    }

    fun saveResultToGallery() {
        val asset = _resultState.value.asset ?: return
        viewModelScope.launch {
            _resultState.update { it.copy(isSavingToGallery = true, errorMessage = null) }
            val format = ExportFormat.fromWire(asset.format)
            val res = MediaExportManager.saveToGallery(
                context = appContext,
                localOutputPath = asset.localOutputPath,
                displayName = "${asset.displayName}_QLITEY",
                format = format
            )
            res.onSuccess {
                if (asset.id.isNotBlank()) {
                    repository.markSavedToGallery(asset.id, true)
                }
                _resultState.update {
                    it.copy(
                        isSavingToGallery = false,
                        asset = asset.copy(isSavedToGallery = true)
                    )
                }
                showToast("Saved to Pictures/QLITEY")
            }.onFailure { err ->
                _resultState.update {
                    it.copy(
                        isSavingToGallery = false,
                        errorMessage = err.localizedMessage ?: "Failed to save to gallery."
                    )
                }
            }
        }
    }

    fun saveCopyToUri(targetUri: Uri) {
        val asset = _resultState.value.asset ?: return
        viewModelScope.launch {
            _resultState.update { it.copy(isSavingCopy = true, errorMessage = null) }
            val res = MediaExportManager.saveCopyToUri(
                context = appContext,
                localOutputPath = asset.localOutputPath,
                targetUri = targetUri
            )
            res.onSuccess {
                _resultState.update { it.copy(isSavingCopy = false) }
                showToast("Saved copy to selected folder")
            }.onFailure { err ->
                _resultState.update {
                    it.copy(
                        isSavingCopy = false,
                        errorMessage = err.localizedMessage ?: "Failed to save copy."
                    )
                }
            }
        }
    }

    fun toggleAutoSave(enabled: Boolean) {
        viewModelScope.launch {
            val updated = preferences.value.copy(autoSave = enabled)
            repository.savePreferences(updated)
            showToast(if (enabled) "Auto Save enabled" else "Auto Save disabled")
        }
    }

    fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch {
            val updated = transform(preferences.value)
            repository.savePreferences(updated).onSuccess {
                showToast("Preferences updated")
            }.onFailure {
                showToast("Failed to save preferences", isError = true)
            }
        }
    }

    fun toggleFavorite(asset: LibraryAsset) {
        if (asset.id.isBlank()) return
        viewModelScope.launch {
            repository.toggleFavorite(asset.id, !asset.isFavorite)
            if (_resultState.value.asset?.id == asset.id) {
                _resultState.update { it.copy(asset = asset.copy(isFavorite = !asset.isFavorite)) }
            }
        }
    }

    fun renameAsset(asset: LibraryAsset, newName: String) {
        if (asset.id.isBlank()) return
        viewModelScope.launch {
            repository.renameAsset(asset.id, newName).onSuccess {
                if (_resultState.value.asset?.id == asset.id) {
                    _resultState.update { it.copy(asset = asset.copy(displayName = newName.trim())) }
                }
                showToast("Renamed to ${newName.trim()}")
            }
        }
    }

    fun deleteAssets(assets: List<LibraryAsset>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            assets.forEach { asset ->
                if (asset.id.isNotBlank()) {
                    repository.deleteAsset(asset.id)
                }
                File(asset.localOutputPath).delete()
                File(asset.thumbnailPath).delete()
                File(asset.localOriginalPath).delete()
            }
            showToast(if (assets.size == 1) "Photo deleted" else "${assets.size} photos deleted")
            onComplete()
        }
    }

    fun exportAssetsToGallery(assets: List<LibraryAsset>) {
        viewModelScope.launch {
            var count = 0
            assets.forEach { asset ->
                val format = ExportFormat.fromWire(asset.format)
                val res = MediaExportManager.saveToGallery(
                    context = appContext,
                    localOutputPath = asset.localOutputPath,
                    displayName = "${asset.displayName}_QLITEY",
                    format = format
                )
                if (res.isSuccess) {
                    count++
                    if (asset.id.isNotBlank()) {
                        repository.markSavedToGallery(asset.id, true)
                    }
                }
            }
            showToast("Saved $count photo(s) to Gallery")
        }
    }

    fun deleteAllCloudData() {
        viewModelScope.launch {
            repository.deleteAllUserCloudData().onSuccess {
                showToast("All cloud history metadata deleted")
            }.onFailure {
                showToast("Failed to clear cloud metadata", isError = true)
            }
        }
    }

    private fun computeTruthfulResolutionLabel(
        requested: OutputResolution,
        outW: Int,
        outH: Int
    ): String {
        val longEdge = maxOf(outW, outH)
        return when {
            longEdge >= 3840 -> "4K"
            requested == OutputResolution.X2 -> "2x"
            requested == OutputResolution.ORIGINAL -> "Original"
            else -> "Custom"
        }
    }

    companion object {
        private const val TAG = "QltyMainVM"

        fun provideFactory(
            context: Context,
            user: FirebaseUser
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appCtx = context.applicationContext
                val dbId = appCtx.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                QltyMainViewModel(
                    appContext = appCtx,
                    repository = QltyFirestoreRepository(db),
                    currentUserId = user.uid,
                    userDisplayName = user.displayName ?: "",
                    userEmail = user.email ?: "",
                    userPhotoUrl = user.photoUrl?.toString()
                )
            }
        }
    }
}
