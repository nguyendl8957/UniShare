const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const {
  initializeTestEnvironment
} = require("@firebase/rules-unit-testing");

const {
  collection,
  doc,
  setDoc,
  getDocs,
  query,
  where,
  orderBy,
  documentId,
  limit,
  startAt,
  endAt,
  startAfter
} = require("firebase/firestore");

test("A05 catalog query integration", async (t) => {
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
    await env.clearFirestore();

    const seeds = [
      ["doc01", "Lập trình Kotlin", "lap trinh kotlin", "note", "programming", 10000],
      ["doc02", "Lập trình Java", "lap trinh java", "note", "programming", 10000],
      ["doc03", "Lập trình Python", "lap trinh python", "note", "programming", 20000],
      ["doc04", "Cơ sở dữ liệu", "co so du lieu", "project", "database", 30000],
      ["doc05", "Giải thuật", "giai thuat", "note", "programming", 40000]
    ];

    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();

      for (let i = 0; i < seeds.length; i++) {
        const [id, title, normalized, categoryId, subjectId, priceVnd] =
          seeds[i];

        await setDoc(doc(db, "catalog", id), {
          title,
          titleNormalized: normalized,
          categoryId,
          subjectId,
          priceVnd,
          sellerId: "sellerA",
          previewRefs: [],
          publishedAt: new Date(
            Date.UTC(2026, 0, i + 1)
          )
        });
      }

      await setDoc(doc(db, "documents", "draft01"), {
        title: "Private draft",
        status: "DRAFT"
      });
    });

    const db = env.unauthenticatedContext().firestore();
    const catalog = collection(db, "catalog");

    await t.test("Newest order", async () => {
      const result = await getDocs(
        query(
          catalog,
          orderBy("publishedAt", "desc"),
          orderBy(documentId()),
          limit(5)
        )
      );

      assert.deepEqual(
        result.docs.map((d) => d.id),
        ["doc05", "doc04", "doc03", "doc02", "doc01"]
      );
    });

    await t.test("Vietnamese normalized prefix", async () => {
      const result = await getDocs(
        query(
          catalog,
          orderBy("titleNormalized"),
          orderBy(documentId()),
          startAt("lap trinh"),
          endAt("lap trinh\uf8ff"),
          limit(5)
        )
      );

      assert.deepEqual(
        result.docs.map((d) => d.id),
        ["doc02", "doc01", "doc03"]
      );
    });

    await t.test("Category, subject and price range", async () => {
      const result = await getDocs(
        query(
          catalog,
          where("categoryId", "==", "note"),
          where("subjectId", "==", "programming"),
          where("priceVnd", ">=", 10000),
          where("priceVnd", "<=", 20000),
          orderBy("priceVnd", "asc"),
          orderBy(documentId()),
          limit(5)
        )
      );

      assert.deepEqual(
        result.docs.map((d) => d.id),
        ["doc01", "doc02", "doc03"]
      );
    });

    await t.test("Cursor pages have no duplicates", async () => {
      const base = query(
        catalog,
        orderBy("priceVnd", "asc"),
        orderBy(documentId())
      );

      const first = await getDocs(
        query(base, limit(2))
      );

      const second = await getDocs(
        query(
          base,
          startAfter(first.docs[first.docs.length - 1]),
          limit(2)
        )
      );

      assert.deepEqual(
        first.docs.map((d) => d.id),
        ["doc01", "doc02"]
      );

      assert.deepEqual(
        second.docs.map((d) => d.id),
        ["doc03", "doc04"]
      );
    });

    await t.test("Draft is not in public catalog", async () => {
      const result = await getDocs(
        query(catalog, limit(20))
      );

      assert.equal(
        result.docs.some((d) => d.id === "draft01"),
        false
      );
    });
  } finally {
    await env.cleanup();
  }
});