import { defineConfig } from "vite-plus";

export default defineConfig({
  test: {
    include: ["tests/**/*.test.ts"],
  },
  lint: {
    options: {
      typeAware: false,
      typeCheck: false,
    },
  },
});
