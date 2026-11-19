const { test, after } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");

const {
  initializeTestEnvironment,
  assertFails
} = require("@firebase/rules-unit-testing");

const {
  ref,
  getBytes,
  uploadBytes
} = require("firebase/storage");

const projectId = "demo-unishare";
const bucket = "gs://demo-unishare.firebasestorage.app";

let testEnv;

async function setup() {
  if (!testEnv) {
    testEnv = await initializeTestEnvironment({
      projectId,
      storage: {
        host: "127.0.0.1",
        port: 9199,
        rules: fs.readFileSync(
          path.resolve(__dirname, "../../storage.rules"),
          "utf8"
        )
      }
    });
  }

  return testEnv;
}

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

test("Guest cannot read private PDF", async () => {
  const env = await setup();
  const storage = env.unauthenticatedContext().storage(bucket);

  await assertFails(
    getBytes(ref(storage, "private/doc1/v1/file.pdf"))
  );
});

test("Seller A cannot read private PDF directly", async () => {
  const env = await setup();
  const storage = env.authenticatedContext("sellerA").storage(bucket);

  await assertFails(
    getBytes(ref(storage, "private/doc1/v1/file.pdf"))
  );
});

test("Seller B cannot read Seller A staging", async () => {
  const env = await setup();
  const storage = env.authenticatedContext("sellerB").storage(bucket);

  await assertFails(
    getBytes(ref(storage, "staging/sellerA/doc1/v1/file.pdf"))
  );
});

test("Client cannot upload into private directory", async () => {
  const env = await setup();
  const storage = env.authenticatedContext("sellerA").storage(bucket);

  await assertFails(
    uploadBytes(
      ref(storage, "private/doc1/v1/file.pdf"),
      new Uint8Array([37, 80, 68, 70]),
      { contentType: "application/pdf" }
    )
  );
});

test("Unpublished preview is not publicly readable", async () => {
  const env = await setup();
  const storage = env.unauthenticatedContext().storage(bucket);

  await assertFails(
    getBytes(ref(storage, "previews/doc1/v1/cover.jpg"))
  );
});