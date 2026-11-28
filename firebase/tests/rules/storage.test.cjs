const { test, after } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");

const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require("@firebase/rules-unit-testing");

const {
  ref,
  getBytes,
  uploadBytes,
  deleteObject,
  listAll
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

test("Avatar storage is private, immutable and restricted to bounded JPEG uploads", async () => {
  const env = await setup();
  const owner = env.authenticatedContext("avatarOwner").storage(bucket);
  const other = env.authenticatedContext("anotherUser").storage(bucket);
  const guest = env.unauthenticatedContext().storage(bucket);
  const prefix = "avatars/avatarOwner";
  const path = prefix + "/55baaf43-9d1c-4489-81d1-b49acad18ec5.jpg";
  const otherPath = prefix + "/55baaf43-9d1c-4489-81d1-b49acad18ec6.jpg";
  const data = new Uint8Array([255, 216, 255, 217]);
  const jpeg = { contentType: "image/jpeg" };

  await assertSucceeds(uploadBytes(ref(owner, path), data, jpeg));
  await assertSucceeds(getBytes(ref(owner, path)));
  await assertFails(getBytes(ref(guest, path)));
  await assertFails(getBytes(ref(other, path)));
  await assertFails(uploadBytes(ref(other, otherPath), data, jpeg));
  await assertFails(uploadBytes(ref(guest, otherPath), data, jpeg));
  await assertFails(uploadBytes(ref(owner, path), data, jpeg));
  await assertFails(uploadBytes(ref(owner, otherPath), data, { contentType: "image/png" }));
  await assertFails(uploadBytes(ref(owner, otherPath), new Uint8Array(1024 * 1024 + 1), jpeg));
  await assertFails(uploadBytes(ref(owner, prefix + "/arbitrary.jpg"), data, jpeg));
  await assertFails(listAll(ref(owner, prefix)));
  await assertFails(deleteObject(ref(other, path)));
  await assertSucceeds(deleteObject(ref(owner, path)));
});