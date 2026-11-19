import { applicationDefault, getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import type { Request, Response, NextFunction } from "express";
import { sendApiError } from "./httpErrors";

const projectId = process.env.FIREBASE_PROJECT_ID;

export function initializeFirebaseAdmin(): void {
    if (!projectId) {
        throw new Error("FIREBASE_PROJECT_ID is required");
    }

    if (getApps().length === 0) {
        initializeApp({
            credential: applicationDefault(),
            projectId
        });
    }
}

export async function requireAuth(
    req: Request,
    res: Response,
    next: NextFunction
): Promise<void> {
    const authorization = req.header("Authorization");

    if (!authorization?.startsWith("Bearer ")) {
        sendApiError(res, 401, "UNAUTHENTICATED", "Authentication required");
        return;
    }

    const idToken = authorization.slice(7).trim();

    if (!idToken) {
        sendApiError(res, 401, "UNAUTHENTICATED", "Authentication required");
        return;
    }

    try {
        const decoded = await getAuth().verifyIdToken(idToken);
        res.locals.uid = decoded.uid;
        next();
    } catch {
        sendApiError(res, 401, "UNAUTHENTICATED", "Authentication required");
    }
}