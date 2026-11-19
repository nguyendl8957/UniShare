const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require("@firebase/rules-unit-testing");

const { doc, setDoc } = require("firebase/firestore");
const {
  ref,
  uploadBytes,
  getBytes
} = require("firebase/storage");

const projectId = "demo-unishare";
const bucket = "gs://demo-unishare.firebasestorage.app";

test("A04 staging upload security", async (t) => {
  const env = await initializeTestEnvironment({
    projectId,
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

  const uidA = "a04SellerA";
  const uidB = "a04SellerB";
  const docId = "a04_upload_fixture";
  const versionId = "v1";

  const prefix = `staging/${uidA}/${docId}/${versionId}`;

  const sellerA = env.authenticatedContext(uidA).storage(bucket);
  const sellerB = env.authenticatedContext(uidB).storage(bucket);
  const guest = env.unauthenticatedContext().storage(bucket);

  const pdfBytes = new Uint8Array([
    37, 80, 68, 70, 45, 49, 46, 52
  ]);

  const imageBytes = new Uint8Array([
    137, 80, 78, 71, 13, 10, 26, 10
  ]);

  try {
    // Tao du lieu bang context bo qua Rules.
    // Khong su dung project Firebase that.
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();

      await setDoc(doc(db, "users", uidA), {
        sellerEnabled: true
      });

      await setDoc(doc(db, "users", uidB), {
        sellerEnabled: true
      });

      await setDoc(doc(db, "documents", docId), {
        sellerId: uidA,
        status: "DRAFT",
        activeVersionId: versionId
      });
    });

    await t.test("Seller A uploads valid PDF", async () => {
      await assertSucceeds(
        uploadBytes(
          ref(sellerA, `${prefix}/pdf/source.pdf`),
          pdfBytes,
          { contentType: "application/pdf" }
        )
      );
    });

    await t.test("Seller A uploads valid preview", async () => {
      await assertSucceeds(
        uploadBytes(
          ref(sellerA, `${prefix}/previews/1.png`),
          imageBytes,
          { contentType: "image/png" }
        )
      );
    });

    await t.test("Seller B cannot upload into A staging", async () => {
      await assertFails(
        uploadBytes(
          ref(sellerB, `${prefix}/previews/2.png`),
          imageBytes,
          { contentType: "image/png" }
        )
      );
    });

    await t.test("Invalid PDF MIME is rejected", async () => {
      await assertFails(
        uploadBytes(
          ref(sellerA, `${prefix}/pdf/other.pdf`),
          pdfBytes,
          { contentType: "text/plain" }
        )
      );
    });

    await t.test("Invalid preview slot is rejected", async () => {
      await assertFails(
        uploadBytes(
          ref(sellerA, `${prefix}/previews/6.png`),
          imageBytes,
          { contentType: "image/png" }
        )
      );
    });

    await t.test("Guest cannot read Seller staging", async () => {
      await assertFails(
        getBytes(
          ref(guest, `${prefix}/pdf/source.pdf`)
        )
      );
    });

    await t.test("Client cannot read private PDF", async () => {
      await assertFails(
        getBytes(
          ref(sellerA, "private/a04_test/v1/source.pdf")
        )
      );
    });

    // Bao dam file da upload co the doc boi dung Seller.
    await t.test("Seller A reads own staging PDF", async () => {
      const bytes = await assertSucceeds(
        getBytes(
          ref(sellerA, `${prefix}/pdf/source.pdf`)
        )
      );

      assert.ok(bytes.byteLength > 0);
    });
  } finally {
    await env.cleanup();
  }
});