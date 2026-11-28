import { onInit } from "firebase-functions/v2/core";
﻿import { onRequest } from "firebase-functions/v2/https";
import { app } from "./app";
import { initializeFirebaseAdmin } from "./shared/firebaseAuth";

// Initialize Firebase Admin SDK.
onInit(() => initializeFirebaseAdmin());

// Expose the existing Express API through Cloud Functions Gen 2.
export const api = onRequest(
    {
        region: "asia-southeast1",
        invoker: "public",
        minInstances: 0,
        maxInstances: 2,
        memory: "512MiB",
        timeoutSeconds: 60,
        serviceAccount: "unishare-backend@unishare-db44a.iam.gserviceaccount.com",
        secrets: ["SEPAY_WEBHOOK_SECRET", "GEMINI_API_KEY"]
    },
    app
);
