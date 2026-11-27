import { Router } from "express";
import { getFirestore } from "firebase-admin/firestore";
import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";

export const sellerDocumentsRouter = Router();

sellerDocumentsRouter.get(
  "/documents",
  requireAuth,
  async (_req, res) => {
    const uid = res.locals.uid as string;

    try {
      const db = getFirestore();

      const seller = await db
        .collection("users")
        .doc(uid)
        .get();

      if (seller.data()?.sellerEnabled !== true) {
        sendApiError(
          res,
          403,
          "FORBIDDEN",
          "Seller permission required"
        );
        return;
      }

      // Never accept a sellerId from a query parameter.
      // The authenticated UID is the only owner filter.
      const result = await db
        .collection("documents")
        .where("sellerId", "==", uid)
        .limit(101)
        .get();

      const documents = result.docs
        .slice(0, 100)
        .map((snapshot) => {
          const data = snapshot.data();

          return {
            docId: snapshot.id,
            versionId: data.activeVersionId ?? "v1",
            title: data.title ?? "",
            category: data.category ?? "",
            subject: data.subject ?? "",
            description: data.description ?? "",
            priceVnd: data.priceVnd ?? 0,
            status: data.status ?? "DRAFT",
            createdAt:
              typeof data.createdAt?.toDate === "function"
                ? data.createdAt.toDate().toISOString()
                : null
          };
        })
        .sort((a, b) =>
          (b.createdAt ?? "").localeCompare(a.createdAt ?? "")
        );

      res.status(200).json({
        documents,
        truncated: result.size > 100
      });
    } catch (error) {
      console.error("Seller documents query failed", error);

      sendApiError(
        res,
        503,
        "SERVICE_UNAVAILABLE",
        "Unable to load seller documents"
      );
    }
  }
);