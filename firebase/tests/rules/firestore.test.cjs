const { test } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");

const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} = require("@firebase/rules-unit-testing");

const {
  doc,
  getDoc,
  setDoc,
  updateDoc
} = require("firebase/firestore");

const rulesPath = path.resolve(
  __dirname,
  "../../firestore.rules"
);

test("UniShare Firestore Security Rules", async (t) => {
  const env = await initializeTestEnvironment({
    projectId: "demo-unishare",
    firestore: {
      host: "127.0.0.1",
      port: 8080,
      rules: fs.readFileSync(rulesPath, "utf8")
    }
  });

  try {
    await env.clearFirestore();

    // Tạo dữ liệu kiểm thử, bỏ qua Rules trong bước seed.
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "users", "userA"), {
        displayName: "User A",
        school: "UTH",
        faculty: "CNTT"
      });

      await setDoc(doc(context.firestore(), "users", "userB"), {
        displayName: "User B",
        school: "UTH",
        faculty: "CNTT"
      });
    });

    const guest = env.unauthenticatedContext().firestore();
    const userA = env.authenticatedContext("userA").firestore();

    await t.test("Guest cannot read private profile", async () => {
      await assertFails(getDoc(doc(guest, "users", "userA")));
    });

    await t.test("Owner can read own profile", async () => {
      await assertSucceeds(getDoc(doc(userA, "users", "userA")));
    });

    await t.test("User A cannot read User B profile", async () => {
      await assertFails(getDoc(doc(userA, "users", "userB")));
    });

    await t.test("User A cannot modify User B profile", async () => {
      await assertFails(
        updateDoc(doc(userA, "users", "userB"), {
          displayName: "Hacked"
        })
      );
    });

    await t.test("Owner can update allowed fields", async () => {
      await assertSucceeds(
        updateDoc(doc(userA, "users", "userA"), {
          displayName: "Updated User A"
        })
      );
    });

    await t.test("Owner cannot grant seller permission", async () => {
      await assertFails(
        updateDoc(doc(userA, "users", "userA"), {
          sellerEnabled: true
        })
      );
    });

    await t.test("Client cannot grant PDF entitlement", async () => {
      await assertFails(
        setDoc(doc(userA, "entitlements", "fake-access"), {
          buyerId: "userA",
          docId: "document-001",
          status: "PAID"
        })
      );
    });

  } finally {
    await env.cleanup();
  }
});
