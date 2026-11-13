import { startMockServer } from "./mock-server";

async function globalSetup() {
  const server = await startMockServer();
  return async () => {
    await new Promise<void>((resolve, reject) => {
      server.close((err) => (err ? reject(err) : resolve()));
    });
  };
}

export default globalSetup;
