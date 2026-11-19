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
  collection,
  doc,
  getDoc,
  getDocs,
  limit,
  query,
  setDoc
} = require("firebase/firestore");

test("A05 Firestore catalog security", async (t) => {
  const env = await initializeTestEnvironment({
    projectId: "demo-unishare",
    firestore: {
      host: "127.0.0.1",
      port: 8080,
      rules: fs.readFileSync(
        path.resolve(__dirname, "../../firestore.rules"),
        "utf8"
      )
    }
  });

  try {
    await env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();

      await setDoc(doc(db, "catalog", "doc_test_01"), {
        title: "Lập trình Kotlin",
        titleNormalized: "lap trinh kotlin",
        categoryId: "note",
        subjectId: "programming",
        priceVnd: 20000,
        sellerId: "sellerA",
        previewRefs: [],
        publishedAt: new Date()
      });

      await setDoc(doc(db, "documents", "draft_test_01"), {
        title: "Tài liệu chưa xuất bản",
        status: "DRAFT"
      });
    });

    const guest = env.unauthenticatedContext().firestore();

    await t.test("Guest reads public catalog", async () => {
      const snapshot = await assertSucceeds(
        getDoc(doc(guest, "catalog", "doc_test_01"))
      );

      assert.equal(snapshot.data().title, "Lập trình Kotlin");
    });

    await t.test("Guest queries catalog with limit", async () => {
      const snapshot = await assertSucceeds(
        getDocs(query(collection(guest, "catalog"), limit(5)))
      );

      assert.ok(snapshot.size >= 1);
    });

    await t.test("Guest cannot list without limit", async () => {
      await assertFails(
        getDocs(collection(guest, "catalog"))
      );
    });

    await t.test("Guest cannot read draft", async () => {
      await assertFails(
        getDoc(doc(guest, "documents", "draft_test_01"))
      );
    });

    await t.test("Guest cannot write catalog", async () => {
      await assertFails(
        setDoc(doc(guest, "catalog", "illegal_write"), {
          title: "Unauthorized"
        })
      );
    });

    await t.test("Guest cannot query over 20 items", async () => {
      await assertFails(
        getDocs(query(collection(guest, "catalog"), limit(21)))
      );
    });
  } finally {
    await env.cleanup();
  }
});
