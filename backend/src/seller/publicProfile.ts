import { Router } from "express";
import { getFirestore } from "firebase-admin/firestore";
import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";

export const sellerPublicProfileRouter = Router();

sellerPublicProfileRouter.get(
  "/:sellerId/public-profile",
  requireAuth,
  async (req, res) => {
    const sellerId = req.params.sellerId;

    if (
      typeof sellerId !== "string" ||
      !/^[A-Za-z0-9_-]{1,150}$/.test(sellerId)
    ) {
      sendApiError(res, 400, "INVALID_REQUEST", "Invalid seller ID");
      return;
    }

    try {
      const db = getFirestore();

      const sellerSnapshot = await db
        .collection("users")
        .doc(sellerId)
        .get();

      const seller = sellerSnapshot.data();

      if (
        !sellerSnapshot.exists ||
        seller?.sellerEnabled !== true
      ) {
        sendApiError(res, 404, "NOT_FOUND", "Seller not found");
        return;
      }

      // Only catalog entries are public listings.
      // Archived documents are intentionally excluded.
      const catalogSnapshot = await db
        .collection("catalog")
        .where("sellerId", "==", sellerId)
        .limit(51)
        .get();

      if (catalogSnapshot.size > 50) {
        sendApiError(
          res,
          503,
          "SERVICE_UNAVAILABLE",
          "Seller has too many listings for this endpoint"
        );
        return;
      }

      const listings = catalogSnapshot.docs.map((snapshot) => {
        const data = snapshot.data();

        return {
          docId: snapshot.id,
          title:
            typeof data.title === "string" ? data.title : "",
          categoryLabel:
            typeof data.categoryId === "string"
              ? data.categoryId
              : "",
          priceVnd:
            Number.isSafeInteger(data.priceVnd) &&
            data.priceVnd >= 0
              ? data.priceVnd
              : 0
        };
      });

      res.status(200).json({
        sellerId,
        displayName:
          typeof seller.displayName === "string"
            ? seller.displayName
            : "",
        school:
          typeof seller.school === "string"
            ? seller.school
            : "",
        faculty:
          typeof seller.faculty === "string"
            ? seller.faculty
            : "",
        listings
      });
    } catch (error) {
      console.error("Public Seller profile query failed");

      sendApiError(
        res,
        503,
        "SERVICE_UNAVAILABLE",
        "Unable to load Seller profile"
      );
    }
  }
);