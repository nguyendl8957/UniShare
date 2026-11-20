import { getFirestore, type Firestore } from "firebase-admin/firestore";

export type AuthorizedPdf = {
  uid: string;
  docId: string;
  versionId: string;
  orderId: string;
  pdfPath: string;
  pdfGeneration: string;
  sizeBytes: number;
};

export class DocumentAccessError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string
  ) {
    super(code);
    this.name = "DocumentAccessError";
  }
}

const VALID_ID = /^[a-zA-Z0-9_-]{1,150}$/;
const VALID_GENERATION = /^[1-9][0-9]*$/;

export async function authorizePdfRead(
  uid: string,
  docId: string,
  db: Firestore = getFirestore()
): Promise<AuthorizedPdf> {
  if (!uid || !VALID_ID.test(docId)) {
    throw new DocumentAccessError(400, "INVALID_REQUEST");
  }

  const entitlementRef = db
    .collection("entitlements")
    .doc(uid)
    .collection("items")
    .doc(docId);

  const entitlementSnapshot = await entitlementRef.get();

  if (!entitlementSnapshot.exists) {
    throw new DocumentAccessError(403, "ENTITLEMENT_REQUIRED");
  }

  const entitlement = entitlementSnapshot.data()!;

  if (entitlement.revokedAt != null) {
    throw new DocumentAccessError(403, "ENTITLEMENT_REVOKED");
  }

  const versionId = entitlement.versionId;
  const orderId = entitlement.orderId;

  if (
    typeof versionId !== "string" ||
    !VALID_ID.test(versionId) ||
    typeof orderId !== "string" ||
    !orderId.startsWith("ord_")
  ) {
    throw new DocumentAccessError(403, "INVALID_ENTITLEMENT");
  }

  const versionSnapshot = await db
    .collection("documents")
    .doc(docId)
    .collection("versions")
    .doc(versionId)
    .get();

  if (!versionSnapshot.exists) {
    throw new DocumentAccessError(404, "DOCUMENT_VERSION_NOT_FOUND");
  }

  const version = versionSnapshot.data()!;

  const expectedPath =
    `private/${docId}/${versionId}/source.pdf`;

  const pdfPath = version.pdfPath;
  const pdfGeneration = String(version.pdfGeneration ?? "");
  const sizeBytes = version.sizeBytes;

  if (
    pdfPath !== expectedPath ||
    !VALID_GENERATION.test(pdfGeneration) ||
    !Number.isSafeInteger(sizeBytes) ||
    sizeBytes <= 0
  ) {
    throw new DocumentAccessError(404, "PDF_NOT_AVAILABLE");
  }

  return {
    uid,
    docId,
    versionId,
    orderId,
    pdfPath,
    pdfGeneration,
    sizeBytes
  };
}
