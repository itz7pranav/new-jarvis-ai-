const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

function pastTimestamp() {
  return new Date(Date.now() - 1000);
}

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validProfilePayload(uid) {
  return {
    userId: uid,
    displayName: "Alice",
    email: "alice@example.com",
    preferredModel: "gemini-3.8-flash",
    themeMode: "dark",
    memoryPermissionGranted: true,
    autoSpeakResponses: false,
    wakeWordEnabled: false,
    speechRate: 1.0,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validConversationPayload(uid) {
  return {
    userId: uid,
    title: "Quantum Mechanics Help",
    modelId: "gemini-3.8-flash",
    lastMessagePreview: "Explain wave-particle duality",
    messageCount: 1,
    pinned: false,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validMessagePayload(uid, convId) {
  return {
    userId: uid,
    conversationId: convId,
    role: "user",
    text: "Explain wave-particle duality simply.",
    attachmentType: "none",
    isError: false,
    clientOrder: 1700000000000,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validMemoryPayload(uid) {
  return {
    userId: uid,
    category: "preference",
    content: "Prefers concise explanations with Python code examples.",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

test("Unauthenticated user: cannot read or write profiles, conversations, messages, or memories", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
});

test("Owner can create and update their own UserProfile, ghost fields rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfilePayload(ALICE_UID)));

  // Shadow update with ghost field must fail
  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );

  // Valid partial update must succeed
  await assertSucceeds(
    profileRef.update({
      preferredModel: "gemini-4-argon",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's profile, conversations, messages, or memories", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set(validConversationPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_1")
      .set(validMessagePayload(ALICE_UID, "conv_1"))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .set(validMemoryPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_2")
      .set(validMemoryPayload(BOB_UID))
  );
});

test("Orphaned message without parent conversation is rejected, valid conversation+message succeeds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Orphan message without conversation doc must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("missing_conv")
      .collection("messages")
      .doc("msg_1")
      .set(validMessagePayload(ALICE_UID, "missing_conv"))
  );

  // Create conversation then message succeeds
  const convRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("conversations")
    .doc("conv_2");
  await assertSucceeds(convRef.set(validConversationPayload(ALICE_UID)));
  await assertSucceeds(
    convRef.collection("messages").doc("msg_1").set(validMessagePayload(ALICE_UID, "conv_2"))
  );

  // Cannot mutate immutable role or conversationId on message update
  await assertFails(
    convRef.collection("messages").doc("msg_1").update({
      role: "model",
      updatedAt: pastTimestamp(),
    })
  );

  // Can update message text (e.g., streaming completion or message edit)
  await assertSucceeds(
    convRef.collection("messages").doc("msg_1").update({
      text: "Updated prompt text",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Invalid model ID or oversized ID poisoning is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badConv = validConversationPayload(ALICE_UID);
  badConv.modelId = "gpt-99-fake";
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_bad")
      .set(badConv)
  );

  const invalidId = "a".repeat(150);
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc(invalidId)
      .set(validMemoryPayload(ALICE_UID))
  );
});
