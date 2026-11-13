package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.core.data.QltyFirestoreRepository
import com.example.core.domain.AppPreferences
import com.example.core.domain.LibraryAsset
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class QltyRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = QltyFirestoreRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile("Alice", ALICE_EMAIL, null)
        }
        assertTrue(result.isSuccess)
        assertEquals(uid, result.getOrThrow().userId)
    }

    @Test
    fun saveAndObservePreferences_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = QltyFirestoreRepository(firestore)

        val saveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.savePreferences(
                AppPreferences(
                    userId = uid,
                    defaultProcessingMode = "cloud",
                    autoSave = true,
                    defaultFormat = "PNG",
                    defaultResolution = "4K",
                    stripLocationMetadata = true,
                    cloudAiConsentGranted = true
                )
            )
        }
        assertTrue(saveRes.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePreferences(uid).first { it.autoSave }
        }
        assertEquals("PNG", observed.defaultFormat)
        assertTrue(observed.cloudAiConsentGranted)
    }

    @Test
    fun createCompleteJobAndAsset_authenticatedOwner_emitsInLibrary() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = QltyFirestoreRepository(firestore)

        val jobId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createJob(
                sourceName = "sakura.jpg",
                sourceChecksum = "sha256_demo",
                mode = "local",
                modelId = "local_lanczos_unsharp_v1",
                presetId = "upscale_4k",
                inputWidth = 1200,
                inputHeight = 900,
                format = "JPEG"
            ).getOrThrow()
        }

        val completeRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.completeJob(jobId, outputWidth = 3840, outputHeight = 2880)
        }
        assertTrue(completeRes.isSuccess)

        val assetId = "ast_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val createAssetRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createAsset(
                LibraryAsset(
                    id = assetId,
                    userId = uid,
                    jobId = jobId,
                    displayName = "Sakura 4K",
                    mode = "local",
                    resolutionLabel = "4K",
                    inputWidth = 1200L,
                    inputHeight = 900L,
                    outputWidth = 3840L,
                    outputHeight = 2880L,
                    format = "JPEG",
                    byteSize = 240000L,
                    localOutputPath = "out.jpg",
                    localOriginalPath = "orig.jpg",
                    thumbnailPath = "thumb.jpg",
                    isFavorite = false,
                    isSavedToGallery = false,
                    presetUsed = "upscale_4k"
                )
            )
        }
        assertTrue(createAssetRes.isSuccess)

        val assets = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeAssets(uid).first { list -> list.any { it.id == assetId } }
        }
        assertTrue(assets.any { it.id == assetId && it.displayName == "Sakura 4K" })
    }

    @Test
    fun observeAssets_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        signInTestUser(BOB_EMAIL)
        val bobRepo = QltyFirestoreRepository(firestore)

        val error = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeAssets(aliceUid).first()
            }
            null
        } catch (e: Exception) {
            e
        }
        val firestoreError = checkNotNull(error?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $error"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreError.code)
    }

    @Test
    fun observeAssets_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = QltyFirestoreRepository(firestore)

        val error = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeAssets("some_uid_123").first()
            }
            null
        } catch (e: Exception) {
            e
        }
        val firestoreError = checkNotNull(error?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $error"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreError.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_qlitey@test.com"
        const val BOB_EMAIL = "bob_qlitey@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
