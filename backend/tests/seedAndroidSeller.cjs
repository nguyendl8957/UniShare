const assert = require("node:assert/strict");
const { initializeApp, getApps } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

async function main() {
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(
    process.env.FIREBASE_AUTH_EMULATOR_HOST,
    "127.0.0.1:9099"
  );
  assert.equal(
    process.env.FIRESTORE_EMULATOR_HOST,
    "127.0.0.1:8080"
  );

  if (getApps().length === 0) {
    initializeApp({ projectId: "demo-unishare" });
  }

  const email = "a04-seller-smoke@example.test";
  const password = "EmulatorOnly_12345!";

  let user;

  try {
    user = await getAuth().getUserByEmail(email);
  } catch (error) {
    if (error.code !== "auth/user-not-found") throw error;

    user = await getAuth().createUser({
      email,
      password
    });
  }

  await getFirestore().collection("users").doc(user.uid).set({
    displayName: "A04 Smoke Seller",
    school: "Demo University",
    faculty: "IT",
    sellerEnabled: true
  });

  const db = getFirestore();
  const batch = db.batch();
  const otherSellerId = "c04_legacy_seller";
  batch.set(db.collection("users").doc(otherSellerId), {
    sellerEnabled: true, displayName: "Lê Bảo Ngọc", school: "", faculty: ""
  });
  for (const [docId, owner] of [
    ["c04_legacy_first", user.uid],
    ["c04_legacy_second", user.uid],
    ["c04_legacy_other", otherSellerId]
  ]) {
    batch.set(db.collection("catalog").doc(docId), {
      title: `C04 Legacy ${docId}`, priceVnd: 10_000, sellerId: owner
    });
  }
  await batch.commit();
  console.log("A04 emulator Seller fixture ready");
  console.log("Project: demo-unishare");
  console.log("UID:", user.uid);
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});