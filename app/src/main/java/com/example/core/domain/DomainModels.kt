package com.example.core.domain

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ProcessingMode(val wire: String, val label: String) {
    LOCAL("local", "Local AI"),
    CLOUD("cloud", "Cloud AI");

    companion object {
        fun fromWire(value: String?): ProcessingMode =
            entries.firstOrNull { it.wire == value } ?: LOCAL
    }
}

enum class OutputResolution(val wire: String, val label: String) {
    ORIGINAL("ORIGINAL", "Original"),
    X2("X2", "2x"),
    UHD_4K("4K", "4K"),
    CUSTOM("CUSTOM", "Custom");

    companion object {
        fun fromWire(value: String?): OutputResolution =
            entries.firstOrNull { it.wire == value } ?: UHD_4K
    }
}

enum class AspectRatioOption(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    SQUARE_1_1("1:1", 1f),
    PORTRAIT_3_4("3:4", 3f / 4f),
    LANDSCAPE_4_3("4:3", 4f / 3f),
    WIDE_16_9("16:9", 16f / 9f)
}

enum class ExportFormat(val wire: String, val mimeType: String, val extension: String) {
    JPEG("JPEG", "image/jpeg", "jpg"),
    PNG("PNG", "image/png", "png"),
    WEBP("WebP", "image/webp", "webp");

    companion object {
        fun fromWire(value: String?): ExportFormat =
            entries.firstOrNull { it.wire == value } ?: JPEG
    }
}

enum class PhotoFilter(
    val id: String,
    val label: String,
    val saturationDelta: Float,
    val contrastDelta: Float,
    val warmthDelta: Float,
    val exposureDelta: Float,
    val vignetteBoost: Float,
    val isMonochrome: Boolean = false,
    val fadeLift: Float = 0f
) {
    NATURAL("natural", "Natural", 0f, 0f, 0f, 0f, 0f),
    VIVID("vivid", "Vivid", 0.35f, 0.14f, 0.06f, 0.04f, 0f),
    CINEMATIC("cinematic", "Cinematic", 0.12f, 0.20f, -0.14f, -0.04f, 0.24f),
    GOLDEN("golden", "Golden", 0.22f, 0.08f, 0.36f, 0.08f, 0.10f),
    NORDIC("nordic", "Nordic", -0.14f, 0.12f, -0.28f, 0.06f, 0.08f),
    FILM("film", "Analog Film", -0.06f, 0.10f, 0.16f, 0.02f, 0.18f, fadeLift = 12f),
    NOIR("noir", "Noir B&W", -1f, 0.32f, 0f, 0f, 0.28f, isMonochrome = true),
    MATTE("matte", "Soft Matte", -0.10f, -0.08f, 0.08f, 0.05f, 0.12f, fadeLift = 18f),
    HDR_CRISP("hdr_crisp", "HDR Crisp", 0.26f, 0.24f, 0.02f, 0.05f, 0f),
    PORTRAIT("portrait", "Portrait Glow", 0.10f, 0.05f, 0.14f, 0.10f, 0.12f);

    companion object {
        fun fromId(id: String?): PhotoFilter =
            entries.firstOrNull { it.id == id } ?: NATURAL
    }
}

enum class EnhancementPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultMode: ProcessingMode,
    val defaultResolution: OutputResolution,
    val strength: Float,
    val denoise: Float,
    val sharpen: Float,
    val colorRecovery: Float,
    val contrastBoost: Float,
    val warmRestore: Boolean = false
) {
    AI_ENHANCE(
        id = "ai_enhance",
        title = "AI Enhance",
        subtitle = "Balanced clarity & tone recovery",
        defaultMode = ProcessingMode.LOCAL,
        defaultResolution = OutputResolution.X2,
        strength = 0.72f,
        denoise = 0.45f,
        sharpen = 0.65f,
        colorRecovery = 0.45f,
        contrastBoost = 0.30f
    ),
    UPSCALE_4K(
        id = "upscale_4k",
        title = "Upscale 4K",
        subtitle = "High-res edge-preserving upscale",
        defaultMode = ProcessingMode.LOCAL,
        defaultResolution = OutputResolution.UHD_4K,
        strength = 0.78f,
        denoise = 0.35f,
        sharpen = 0.78f,
        colorRecovery = 0.25f,
        contrastBoost = 0.22f
    ),
    RESTORE_OLD(
        id = "restore_old",
        title = "Restore Old Photos",
        subtitle = "Denoise, deblur & faded color repair",
        defaultMode = ProcessingMode.CLOUD,
        defaultResolution = OutputResolution.X2,
        strength = 0.85f,
        denoise = 0.82f,
        sharpen = 0.58f,
        colorRecovery = 0.65f,
        contrastBoost = 0.38f,
        warmRestore = true
    ),
    NATURAL_CLARITY(
        id = "natural_clarity",
        title = "Natural Clarity",
        subtitle = "Subtle micro-contrast & sharpness",
        defaultMode = ProcessingMode.LOCAL,
        defaultResolution = OutputResolution.ORIGINAL,
        strength = 0.55f,
        denoise = 0.25f,
        sharpen = 0.52f,
        colorRecovery = 0.18f,
        contrastBoost = 0.18f
    ),
    LOW_LIGHT_DENOISE(
        id = "low_light_denoise",
        title = "Low-Light Denoise",
        subtitle = "Clean grain while keeping edges",
        defaultMode = ProcessingMode.LOCAL,
        defaultResolution = OutputResolution.X2,
        strength = 0.75f,
        denoise = 0.90f,
        sharpen = 0.48f,
        colorRecovery = 0.30f,
        contrastBoost = 0.26f
    );

    companion object {
        fun fromId(id: String?): EnhancementPreset =
            entries.firstOrNull { it.id == id } ?: AI_ENHANCE
    }
}

data class EnhancementAdjustments(
    val strength: Float = 0.72f,
    val exposure: Float = 0f,        // -1.0 .. +1.0
    val contrastBoost: Float = 0.30f,// -0.5 .. +1.0
    val highlights: Float = 0f,      // -1.0 .. +1.0 (negative recovers blown highlights)
    val shadows: Float = 0f,         // -1.0 .. +1.0 (positive lifts dark shadows)
    val colorRecovery: Float = 0.45f,// -1.0 .. +1.0 (saturation/vibrance)
    val warmth: Float = 0f,          // -1.0 (cool) .. +1.0 (warm)
    val sharpen: Float = 0.65f,      // 0.0 .. 1.0
    val denoise: Float = 0.45f,      // 0.0 .. 1.0
    val vignette: Float = 0f,        // 0.0 .. 1.0
    val filter: PhotoFilter = PhotoFilter.NATURAL,
    val preset: EnhancementPreset = EnhancementPreset.AI_ENHANCE
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val planId: String = "free",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppPreferences(
    @DocumentId val id: String = "",
    val userId: String = "",
    val defaultProcessingMode: String = ProcessingMode.LOCAL.wire,
    val autoSave: Boolean = false,
    val defaultFormat: String = ExportFormat.JPEG.wire,
    val defaultResolution: String = OutputResolution.UHD_4K.wire,
    val stripLocationMetadata: Boolean = true,
    val cloudAiConsentGranted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EnhancementJob(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceName: String = "",
    val sourceChecksum: String = "",
    val mode: String = ProcessingMode.LOCAL.wire,
    val modelId: String = "",
    val presetId: String = EnhancementPreset.AI_ENHANCE.id,
    val status: String = "queued",
    val progressStage: String = "Queued",
    val inputWidth: Long = 0L,
    val inputHeight: Long = 0L,
    val outputWidth: Long = 0L,
    val outputHeight: Long = 0L,
    val format: String = ExportFormat.JPEG.wire,
    val errorCode: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LibraryAsset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val jobId: String = "",
    val displayName: String = "",
    val mode: String = ProcessingMode.LOCAL.wire,
    val resolutionLabel: String = "4K",
    val inputWidth: Long = 0L,
    val inputHeight: Long = 0L,
    val outputWidth: Long = 0L,
    val outputHeight: Long = 0L,
    val format: String = ExportFormat.JPEG.wire,
    val byteSize: Long = 0L,
    val localOutputPath: String = "",
    val localOriginalPath: String = "",
    val thumbnailPath: String = "",
    val isFavorite: Boolean = false,
    val isSavedToGallery: Boolean = false,
    val presetUsed: String = EnhancementPreset.AI_ENHANCE.id,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class ImportedImageMeta(
    val sourceUriString: String,
    val localOriginalPath: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val byteSize: Long,
    val checksumSha256: String,
    val hasAlpha: Boolean
)
