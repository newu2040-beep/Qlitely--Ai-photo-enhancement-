const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user profile, jobs, or assets", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("assets").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("jobs").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("preferences").doc("app").get());
});

test("Authenticated owner can create and update their own profile, cannot escalate planId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set({
    userId: ALICE_UID,
    displayName: "Alice",
    email: "alice@example.com",
    photoUrl: "https://example.com/a.jpg",
    planId: "free",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  }));

  await assertSucceeds(profileRef.update({
    displayName: "Alice Updated",
    updatedAt: nowTs(),
  }));

  // Escalating planId must fail
  await assertFails(profileRef.update({
    planId: "pro",
    updatedAt: nowTs(),
  }));

  // Ghost field injection must fail
  await assertFails(profileRef.update({
    isAdmin: true,
    updatedAt: nowTs(),
  }));
});

test("Cross-user access to profile, preferences, jobs, and assets is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      planId: "free",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
    await db.collection("users").doc(BOB_UID).collection("assets").doc("asset_1").set({
      userId: BOB_UID,
      jobId: "job_1",
      displayName: "Mountain",
      mode: "local",
      resolutionLabel: "4K",
      inputWidth: 1000,
      inputHeight: 1000,
      outputWidth: 3840,
      outputHeight: 3840,
      format: "JPEG",
      byteSize: 120000,
      localOutputPath: "out.jpg",
      localOriginalPath: "orig.jpg",
      thumbnailPath: "thumb.jpg",
      isFavorite: false,
      isSavedToGallery: true,
      presetUsed: "natural",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("assets").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("assets").doc("asset_1").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("assets").doc("asset_1").delete());
});

test("Owner can manage preferences, jobs with terminal lock, and library assets", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  const prefRef = aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("app");
  await assertSucceeds(prefRef.set({
    userId: ALICE_UID,
    defaultProcessingMode: "local",
    autoSave: false,
    defaultFormat: "JPEG",
    defaultResolution: "4K",
    stripLocationMetadata: true,
    cloudAiConsentGranted: false,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  }));

  await assertSucceeds(prefRef.update({
    autoSave: true,
    cloudAiConsentGranted: true,
    updatedAt: nowTs(),
  }));

  const jobRef = aliceDb.collection("users").doc(ALICE_UID).collection("jobs").doc("job_1");
  await assertSucceeds(jobRef.set({
    userId: ALICE_UID,
    sourceName: "sakura.jpg",
    sourceChecksum: "abc123",
    mode: "cloud",
    modelId: "gemini-nano-banana-2.1",
    presetId: "ai_enhance",
    status: "processing",
    progressStage: "Enhancing in Cloud AI",
    inputWidth: 1200,
    inputHeight: 900,
    outputWidth: 0,
    outputHeight: 0,
    format: "JPEG",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  }));

  await assertSucceeds(jobRef.update({
    status: "completed",
    progressStage: "Enhancement complete",
    outputWidth: 2400,
    outputHeight: 1800,
    updatedAt: nowTs(),
  }));

  // Terminal lock prevents updating completed job
  await assertFails(jobRef.update({
    status: "failed",
    progressStage: "Tampered",
    updatedAt: nowTs(),
  }));

  const assetRef = aliceDb.collection("users").doc(ALICE_UID).collection("assets").doc("asset_1");
  await assertSucceeds(assetRef.set({
    userId: ALICE_UID,
    jobId: "job_1",
    displayName: "Sakura Bloom",
    mode: "cloud",
    resolutionLabel: "2x",
    inputWidth: 1200,
    inputHeight: 900,
    outputWidth: 2400,
    outputHeight: 1800,
    format: "JPEG",
    byteSize: 450000,
    localOutputPath: "out.jpg",
    localOriginalPath: "orig.jpg",
    thumbnailPath: "thumb.jpg",
    isFavorite: false,
    isSavedToGallery: false,
    presetUsed: "ai_enhance",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  }));

  await assertSucceeds(assetRef.update({
    isFavorite: true,
    updatedAt: nowTs(),
  }));

  await assertSucceeds(assetRef.update({
    displayName: "Sakura Bloom 2x",
    isSavedToGallery: true,
    updatedAt: nowTs(),
  }));

  // Cannot mutate immutable jobId on asset
  await assertFails(assetRef.update({
    jobId: "job_other",
    updatedAt: nowTs(),
  }));

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("assets").get());
});
