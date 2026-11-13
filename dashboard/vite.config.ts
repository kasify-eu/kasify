import { execSync } from "child_process";
import { readFileSync } from "fs";
import { fileURLToPath } from "url";
import { reactRouter } from "@react-router/dev/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";

function getAppVersion(): string {
  const { version } = JSON.parse(readFileSync("./package.json", "utf-8"));
  const commit = process.env.SOURCE_COMMIT;
  if (commit) {
    return `v${version}-${commit.slice(0, 7)}`;
  }
  try {
    const hash = execSync("git rev-parse --short HEAD").toString().trim();
    return `v${version}-${hash}`;
  } catch {
    return `v${version}-dev`;
  }
}

const workspaceRoot = fileURLToPath(new URL("..", import.meta.url));

export default defineConfig({
  plugins: [tailwindcss(), reactRouter(), tsconfigPaths()],
  define: {
    __APP_VERSION__: JSON.stringify(getAppVersion()),
  },
  server: {
    fs: {
      allow: [workspaceRoot],
    },
  },
});
