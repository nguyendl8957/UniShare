const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require("@firebase/rules-unit-testing");

const {
  doc,
  setDoc,
  deleteDoc
} = require("firebase/firestore");

const {
  ref,
  uploadBytes,
  getBytes
} = require("firebase/storage");

test("A04 public preview access", async (t) => {
  const env = await initializeTestEnvironment({
    projectId: "demo-unishare",
    firestore: {
      host: "127.0.0.1",
      port: 8080
    },
    storage: {
      host: "127.0.0.1",
      port: 9199,
      rules: fs.readFileSync(
        path.resolve(__dirname, "../../storage.rules"),
        "utf8"
      )
    }
  });

  const bucket = "gs://demo-unishare.firebasestorage.app";
  const docId = "a04_preview_access_test";
  const previewPath = `previews/${docId}/v1/1.png`;
  const unlistedPath = `previews/${docId}/v1/2.png`;

  const guestStorage =
    env.unauthenticatedContext().storage(bucket);

  const sellerStorage =
    env.authenticatedContext("sellerA").storage(bucket);

  const bytes = new Uint8Array([
    137, 80, 78, 71, 13, 10, 26, 10
  ]);

  try {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const storage = ctx.storage(bucket);

      await uploadBytes(
        ref(storage, previewPath),
        bytes,
        { contentType: "image/png" }
      );

      await uploadBytes(
        ref(storage, unlistedPath),
        bytes,
        { contentType: "image/png" }
      );
    });

    await t.test("Guest cannot read before publish", async () => {
      await assertFails(
        getBytes(ref(guestStorage, previewPath))
      );
    });

    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), "catalog", docId),
        {
          title: "A04 Public Preview Test",
          previewRefs: [previewPath],
          sellerId: "sellerA"
        }
      );
    });

    await t.test("Guest reads published preview", async () => {
      const result = await assertSucceeds(
        getBytes(ref(guestStorage, previewPath))
      );

      assert.deepEqual(
        Array.from(new Uint8Array(result)),
        Array.from(bytes)
      );
    });

    await t.test("Unlisted image stays private", async () => {
      await assertFails(
        getBytes(ref(guestStorage, unlistedPath))
      );
    });

    await t.test("Client cannot overwrite preview", async () => {
      await assertFails(
        uploadBytes(
          ref(sellerStorage, previewPath),
          bytes,
          { contentType: "image/png" }
        )
      );
    });

    await env.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(
        doc(ctx.firestore(), "catalog", docId)
      );
    });

    await t.test("Guest cannot read after archive", async () => {
      await assertFails(
        getBytes(ref(guestStorage, previewPath))
      );
    });

    await t.test("Private PDF is always denied", async () => {
      await assertFails(
        getBytes(
          ref(
            guestStorage,
            `private/${docId}/v1/source.pdf`
          )
        )
      );
    });
  } finally {
    await env.cleanup();
  }
});