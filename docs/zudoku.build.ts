import type { ZudokuBuildConfig } from "zudoku";
import { removePaths } from "zudoku/processors/removePaths";

const config: ZudokuBuildConfig = {
  processors: [
    removePaths({
      paths: {
        "/": true,
        "/health": true,
        "/openapi.yaml": true,
        "/openapi.json": true,
      },
    }),
  ],
};

export default config;
