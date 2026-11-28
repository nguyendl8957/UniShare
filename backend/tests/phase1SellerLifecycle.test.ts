import { test } from "node:test";
import assert from "node:assert/strict";
import request from "supertest";
import { getFirestore } from "firebase-admin/firestore";
import { randomUUID } from "node:crypto";

import { app } from "../src/app";
import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";

async function signup(): Promise<{ uid: string; token: string }> {
  const response = await fetch(
    "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ returnSecureToken: true })
    }
  );

  assert.equal(response.status, 200);

  const result = await response.json() as {
    localId: string;
    idToken: string;
  };

  return { uid: result.localId, token: result.idToken };
}

test("Phase 1 Seller metadata and lifecycle", async (t) => {
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8080");
  assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, "127.0.0.1:9099");

  initializeFirebaseAdmin();

  const db = getFirestore();
  const seller = await signup();
  const other = await signup();

  const docId = "p1_" + randomUUID().replace(/-/g, "");
  const documentRef = db.collection("documents").doc(docId);
  const catalogRef = db.collection("catalog").doc(docId);
  const versionRef = documentRef.collection("versions").doc("v1");

  const auth = (token: string) => `Bearer ${token}`;

  const metadata = (
    title: string,
    priceVnd: number,
    originalTitle = "Original Document",
    originalPrice = 15000,
    originalDescription = "Original description"
  ) => ({
    expectedVersionId: "v1",
    title,
    category: "notes",
    subject: "programming",
    description: "Seller integration test",
    priceVnd,
    expectedMetadata: {
      title: originalTitle,
      category: "notes",
      subject: "programming",
      description: originalDescription,
      priceVnd: originalPrice
    }
  });

  try {
    await db.collection("users").doc(seller.uid).set({
      sellerEnabled: true,
      displayName: "Seller Test",
      school: "Test University",
      faculty: "Computer Science",
      email: "private@example.invalid"
    });

    await db.collection("users").doc(other.uid).set({
      sellerEnabled: true
    });

    await documentRef.set({
      sellerId: seller.uid,
      title: "Original Document",
      category: "notes",
      subject: "programming",
      description: "Original description",
      priceVnd: 15000,
      activeVersionId: "v1",
      status: "ARCHIVED"
    });

    await versionRef.set({
      pdfPath: `private/${docId}/v1/source.pdf`,
      previewPaths: [`previews/${docId}/v1/page1.png`]
    });

    await t.test("401 requires authentication", async () => {
      const response = await request(app)
        .patch(`/v1/documents/${docId}/metadata`)
        .send(metadata("Updated Document", 18000));

      assert.equal(response.status, 401);
    });

    await t.test("403 rejects another Seller", async () => {
      const response = await request(app)
        .patch(`/v1/documents/${docId}/metadata`)
        .set("Authorization", auth(other.token))
        .send(metadata("Updated Document", 18000));

      assert.equal(response.status, 403);
    });

    await t.test("metadata on archived doc stays private", async () => {
      const response = await request(app)
        .patch(`/v1/documents/${docId}/metadata`)
        .set("Authorization", auth(seller.token))
        .send(metadata("Updated Document", 18000));

      assert.equal(response.status, 200, JSON.stringify(response.body));
      assert.equal(response.body.status, "ARCHIVED");

      const document = await documentRef.get();
      const catalog = await catalogRef.get();

      assert.equal(document.get("priceVnd"), 18000);
      assert.equal(document.get("title"), "Updated Document");
      assert.equal(catalog.exists, false);
    });

    await t.test("unarchive recreates matching catalog", async () => {
      const response = await request(app)
        .patch(`/v1/documents/${docId}`)
        .set("Authorization", auth(seller.token))
        .send({
          action: "unarchive",
          expectedVersionId: "v1"
        });

      assert.equal(response.status, 200, JSON.stringify(response.body));
      assert.equal(response.body.status, "PUBLISHED");

      const document = await documentRef.get();
      const catalog = await catalogRef.get();

      assert.equal(document.get("status"), "PUBLISHED");
      assert.equal(catalog.exists, true);
      assert.equal(catalog.get("sellerId"), seller.uid);
      assert.equal(catalog.get("priceVnd"), 18000);
      assert.equal(catalog.get("title"), "Updated Document");
    });

    await t.test("published metadata updates both records", async () => {
      const response = await request(app)
        .patch(`/v1/documents/${docId}/metadata`)
        .set("Authorization", auth(seller.token))
        .send(metadata("Final Published Title", 21000, "Updated Document", 18000, "Seller integration test"));

      assert.equal(response.status, 200, JSON.stringify(response.body));

      const document = await documentRef.get();
      const catalog = await catalogRef.get();

      assert.equal(document.get("priceVnd"), 21000);
      assert.equal(catalog.get("priceVnd"), 21000);
      assert.equal(catalog.get("title"), "Final Published Title");
    });

    await t.test("409 rejects stale metadata without overwriting", async () => {
      const response = await request(app)
        .patch(`/v1/documents/${docId}/metadata`)
        .set("Authorization", auth(seller.token))
        .send(
          metadata(
            "Stale Overwrite Attempt",
            25000,
            "Updated Document",
            18000,
            "Seller integration test"
          )
        );

      assert.equal(response.status, 409, JSON.stringify(response.body));

      const document = await documentRef.get();
      const catalog = await catalogRef.get();

      assert.equal(document.get("title"), "Final Published Title");
      assert.equal(document.get("priceVnd"), 21000);
      assert.equal(catalog.get("title"), "Final Published Title");
      assert.equal(catalog.get("priceVnd"), 21000);
    });
    await t.test("Guest can view the public Seller profile without receiving private fields", async () => {
      const response = await request(app)
        .get(`/v1/seller/${seller.uid}/public-profile`);

      assert.equal(response.status, 200, JSON.stringify(response.body));
      assert.equal(response.body.sellerId, seller.uid);
      assert.equal(response.body.displayName, "Seller Test");
      assert.equal(response.body.listings.length, 1);
      assert.equal(response.body.listings[0].docId, docId);
      assert.equal(response.body.email, undefined);
      assert.equal(response.body.bankAccount, undefined);
    });

    await t.test("archive removes public catalog", async () => {
      const response = await request(app)
        .patch(`/v1/documents/${docId}`)
        .set("Authorization", auth(seller.token))
        .send({
          action: "archive",
          expectedVersionId: "v1"
        });

      assert.equal(response.status, 200, JSON.stringify(response.body));
      assert.equal(response.body.status, "ARCHIVED");

      const catalog = await catalogRef.get();
      assert.equal(catalog.exists, false);
    });

    await t.test("public profile excludes archived docs", async () => {
      const response = await request(app)
        .get(`/v1/seller/${seller.uid}/public-profile`)
        .set("Authorization", auth(other.token));

      assert.equal(response.status, 200, JSON.stringify(response.body));
      assert.equal(response.body.listings.length, 0);
    });
  } finally {
    await Promise.allSettled([
      catalogRef.delete(),
      versionRef.delete(),
      documentRef.delete(),
      db.collection("users").doc(seller.uid).delete(),
      db.collection("users").doc(other.uid).delete()
    ]);
  }
});