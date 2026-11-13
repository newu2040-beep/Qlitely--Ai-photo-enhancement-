package com.example.core.data

import android.content.Context
import com.example.R
import com.example.core.domain.AppPreferences
import com.example.core.domain.EnhancementJob
import com.example.core.domain.LibraryAsset
import com.example.core.domain.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class QltyFirestoreRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String,
        photoUrl: String?
    ): Result<UserProfile> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to displayName.take(100),
                "email" to email.take(200),
                "planId" to "free",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!photoUrl.isNullOrBlank()) {
                payload["photoUrl"] = photoUrl.take(500)
            }
            docRef.set(payload).await()
        } else {
            val updatePayload = mutableMapOf<String, Any>(
                "displayName" to displayName.take(100),
                "email" to email.take(200),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!photoUrl.isNullOrBlank()) {
                updatePayload["photoUrl"] = photoUrl.take(500)
            }
            docRef.update(updatePayload).await()
        }
        val fresh = docRef.get().await()
        fresh.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: UserProfile(id = uid, userId = uid, displayName = displayName, email = email, photoUrl = photoUrl)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}")
    }

    fun observePreferences(userId: String): Flow<AppPreferences> = flow {
        val docRef = db.collection("users").document(userId)
            .collection("preferences").document("app")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        AppPreferences::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: AppPreferences(id = "app", userId = userId)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun savePreferences(prefs: AppPreferences): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("preferences").document("app")
        val existing = docRef.get().await()
        if (!existing.exists()) {
            val payload = mapOf(
                "userId" to uid,
                "defaultProcessingMode" to prefs.defaultProcessingMode,
                "autoSave" to prefs.autoSave,
                "defaultFormat" to prefs.defaultFormat,
                "defaultResolution" to prefs.defaultResolution,
                "stripLocationMetadata" to prefs.stripLocationMetadata,
                "cloudAiConsentGranted" to prefs.cloudAiConsentGranted,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } else {
            val payload = mapOf(
                "defaultProcessingMode" to prefs.defaultProcessingMode,
                "autoSave" to prefs.autoSave,
                "defaultFormat" to prefs.defaultFormat,
                "defaultResolution" to prefs.defaultResolution,
                "stripLocationMetadata" to prefs.stripLocationMetadata,
                "cloudAiConsentGranted" to prefs.cloudAiConsentGranted,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/preferences/app")
    }

    suspend fun createJob(
        sourceName: String,
        sourceChecksum: String,
        mode: String,
        modelId: String,
        presetId: String,
        inputWidth: Int,
        inputHeight: Int,
        format: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val jobId = "job_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("users").document(uid)
            .collection("jobs").document(jobId)
        val payload = mapOf(
            "userId" to uid,
            "sourceName" to sourceName.ifBlank { "photo" }.take(120),
            "sourceChecksum" to sourceChecksum.ifBlank { "checksum" }.take(128),
            "mode" to mode,
            "modelId" to modelId.take(64),
            "presetId" to presetId.take(64),
            "status" to "processing",
            "progressStage" to "Preparing enhancement",
            "inputWidth" to inputWidth.coerceIn(1, 32768),
            "inputHeight" to inputHeight.coerceIn(1, 32768),
            "outputWidth" to 0,
            "outputHeight" to 0,
            "format" to format,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        jobId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/jobs")
    }

    suspend fun completeJob(
        jobId: String,
        outputWidth: Int,
        outputHeight: Int,
        stage: String = "Enhancement complete"
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("jobs").document(jobId)
        docRef.update(
            mapOf(
                "status" to "completed",
                "progressStage" to stage.take(80),
                "outputWidth" to outputWidth.coerceIn(1, 32768),
                "outputHeight" to outputHeight.coerceIn(1, 32768),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/jobs/$jobId")
    }

    suspend fun failOrCancelJob(
        jobId: String,
        status: String,
        stage: String,
        errorCode: String? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("jobs").document(jobId)
        val payload = mutableMapOf<String, Any>(
            "status" to status,
            "progressStage" to stage.take(80),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!errorCode.isNullOrBlank()) {
            payload["errorCode"] = errorCode.take(120)
        }
        docRef.update(payload).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/jobs/$jobId")
    }

    fun observeJobs(userId: String): Flow<List<EnhancementJob>> = flow {
        val colRef = db.collection("users").document(userId).collection("jobs")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        EnhancementJob::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun createAsset(asset: LibraryAsset): Result<String> = runCatching {
        val uid = requireUserId()
        val assetId = if (asset.id.isNotBlank()) asset.id else "ast_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("users").document(uid)
            .collection("assets").document(assetId)
        val payload = mapOf(
            "userId" to uid,
            "jobId" to asset.jobId.take(128),
            "displayName" to asset.displayName.ifBlank { "Enhanced Photo" }.take(120),
            "mode" to asset.mode,
            "resolutionLabel" to asset.resolutionLabel,
            "inputWidth" to asset.inputWidth.coerceIn(1L, 32768L),
            "inputHeight" to asset.inputHeight.coerceIn(1L, 32768L),
            "outputWidth" to asset.outputWidth.coerceIn(1L, 32768L),
            "outputHeight" to asset.outputHeight.coerceIn(1L, 32768L),
            "format" to asset.format,
            "byteSize" to asset.byteSize.coerceIn(0L, 500_000_000L),
            "localOutputPath" to asset.localOutputPath.take(500),
            "localOriginalPath" to asset.localOriginalPath.take(500),
            "thumbnailPath" to asset.thumbnailPath.take(500),
            "isFavorite" to asset.isFavorite,
            "isSavedToGallery" to asset.isSavedToGallery,
            "presetUsed" to asset.presetUsed.take(64),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        assetId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/assets")
    }

    fun observeAssets(userId: String): Flow<List<LibraryAsset>> = flow {
        val colRef = db.collection("users").document(userId).collection("assets")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LibraryAsset::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun getAsset(assetId: String): Result<LibraryAsset?> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("assets").document(assetId)
        docRef.get().await().toObject(
            LibraryAsset::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "users/${auth.currentUser?.uid}/assets/$assetId")
    }

    suspend fun toggleFavorite(assetId: String, favorite: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("assets").document(assetId)
        docRef.update(
            mapOf(
                "isFavorite" to favorite,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/assets/$assetId")
    }

    suspend fun markSavedToGallery(assetId: String, saved: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("assets").document(assetId)
        docRef.update(
            mapOf(
                "isSavedToGallery" to saved,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/assets/$assetId")
    }

    suspend fun renameAsset(assetId: String, newName: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanName = newName.trim().ifBlank { "Enhanced Photo" }.take(120)
        val docRef = db.collection("users").document(uid)
            .collection("assets").document(assetId)
        docRef.update(
            mapOf(
                "displayName" to cleanName,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/assets/$assetId")
    }

    suspend fun deleteAsset(assetId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("assets").document(assetId)
        docRef.delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/assets/$assetId")
    }

    suspend fun deleteAllUserCloudData(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        val assets = userDoc.collection("assets").get().await()
        for (doc in assets.documents) {
            doc.reference.delete().await()
        }
        val jobs = userDoc.collection("jobs").get().await()
        for (doc in jobs.documents) {
            doc.reference.delete().await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}")
    }
}
