import test from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

import {
  authorizePdfRead,
  DocumentAccessError
} from "../src/documents/entitlement";

test("A06 PDF entitlement authorization", async () => {
  if (
    process.env.FIREBASE_PROJECT_ID !== "demo-unishare" ||
    process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080"
  ) {
    throw new Error("A06 test requires Firestore Emulator");
  }

  if (getApps().length === 0) {
    initializeApp({ projectId: "demo-unishare" });
  }

  const db = getFirestore();
  const id = randomUUID().replace(/-/g, "").slice(0, 16);
  const ownerUid = `a06_owner_${id}`;
  const otherUid = `a06_other_${id}`;
  const docId = `a06_doc_${id}`;
  const versionId = "v1";

  const entitlementRef = db
    .collection("entitlements")
    .doc(ownerUid)
    .collection("items")
    .doc(docId);

  const versionRef = db
    .collection("documents")
    .doc(docId)
    .collection("versions")
    .doc(versionId);

  async function denied(
    uid: string,
    status: number,
    code: string
  ) {
    await assert.rejects(
      authorizePdfRead(uid, docId, db),
      (error: unknown) =>
        error instanceof DocumentAccessError &&
        error.statusCode === status &&
        error.code === code
    );
  }

  try {
    await versionRef.set({
      pdfPath: `private/${docId}/${versionId}/source.pdf`,
      pdfGeneration: "123456789",
      sizeBytes: 1024
    });

    await entitlementRef.set({
      orderId: "ord_1234567890abcdef",
      versionId,
      grantedAt: new Date().toISOString()
    });

    const authorized = await authorizePdfRead(
      ownerUid, docId, db
    );

    assert.equal(authorized.uid, ownerUid);
    assert.equal(authorized.versionId, versionId);
    console.log("PASS: Authorized buyer");

    await denied(otherUid, 403, "ENTITLEMENT_REQUIRED");
    console.log("PASS: Other buyer denied");

    await entitlementRef.update({
      revokedAt: new Date().toISOString()
    });

    await denied(ownerUid, 403, "ENTITLEMENT_REVOKED");
    console.log("PASS: Revoked buyer denied");

    await entitlementRef.update({
      revokedAt: null,
      versionId: "v_missing"
    });

    await denied(ownerUid, 404, "DOCUMENT_VERSION_NOT_FOUND");
    console.log("PASS: Missing version denied");

    await entitlementRef.update({ versionId });

    await versionRef.update({
      pdfPath: "private/wrong-document/v1/source.pdf"
    });

    await denied(ownerUid, 404, "PDF_NOT_AVAILABLE");
    console.log("PASS: Wrong PDF path denied");
  } finally {
    await Promise.allSettled([
      entitlementRef.delete(),
      versionRef.delete()
    ]);
  }
});
