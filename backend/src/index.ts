import { app } from "./app";
import { initializeFirebaseAdmin } from "./shared/firebaseAuth";

const port = Number(process.env.PORT ?? 3000);

if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error("Invalid PORT configuration");
}

if (
  process.env.NODE_ENV === "production" &&
  process.env.FIREBASE_AUTH_EMULATOR_HOST
) {
  throw new Error("Auth Emulator must not be enabled in production");
}

initializeFirebaseAdmin();

app.listen(port, () => {
  console.log(`UniShare backend listening on port ${port}`);
});
