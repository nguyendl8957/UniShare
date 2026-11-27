import { Router } from "express";
import {
  FieldValue,
  getFirestore
} from "firebase-admin/firestore";

import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";

export const sellerEnrollmentRouter = Router();

/**
 * Buyer self-enrollment into Seller.
 *
 * Firebase ID token is the sole source of UID.
 * The client cannot set sellerEnabled directly.
 * No payment details or payout permissions are granted here.
 */
sellerEnrollmentRouter.post(
  "/enroll",
  requireAuth,
  async (req, res) => {
    const uid = res.locals.uid as string;

    const body = req.body;

    if (
      !body ||
      typeof body !== "object" ||
      Array.isArray(body) ||
      Object.keys(body).length !== 1 ||
      body.acceptTerms !== true
    ) {
      sendApiError(
        res,
        400,
        "INVALID_REQUEST",
        "Seller terms must be accepted"
      );
      return;
    }

    try {
      const db = getFirestore();
      const userRef = db.collection("users").doc(uid);

      const result = await db.runTransaction(
        async (transaction) => {
          const snapshot = await transaction.get(userRef);

          if (!snapshot.exists) {
            return "PROFILE_REQUIRED" as const;
          }

          const profile = snapshot.data()!;

          if (
            typeof profile.displayName !== "string" ||
            profile.displayName.trim().length < 2 ||
            typeof profile.school !== "string" ||
            !profile.school.trim() ||
            typeof profile.faculty !== "string" ||
            !profile.faculty.trim()
          ) {
            return "PROFILE_INCOMPLETE" as const;
          }

          if (profile.sellerEnabled === true) {
            return "ACTIVE" as const;
          }

          transaction.update(userRef, {
            sellerEnabled: true,
            sellerTermsAcceptedAt:
              FieldValue.serverTimestamp()
          });

          return "ENROLLED" as const;
        }
      );

      if (result === "PROFILE_REQUIRED") {
        sendApiError(
          res,
          404,
          "NOT_FOUND",
          "User profile not found"
        );
        return;
      }

      if (result === "PROFILE_INCOMPLETE") {
        sendApiError(
          res,
          400,
          "INVALID_REQUEST",
          "Complete your profile before becoming a seller"
        );
        return;
      }

      res.status(200).json({
        sellerEnabled: true,
        status: "ACTIVE"
      });
    } catch (error) {
      console.error("Seller enrollment failed", error);

      sendApiError(
        res,
        503,
        "SERVICE_UNAVAILABLE",
        "Unable to enroll seller"
      );
    }
  }
);