import { defineConfig } from "vite-plus";

const isInVueVbenAdmin = (file: string): boolean =>
  file.replace(/\\/g, "/").includes("/apps/vue-vben-admin/");

export default defineConfig({
  staged: {
    // Function task (lint-staged API) instead of a plain command string so
    // that we can drop paths inside `apps/vue-vben-admin/` before they are
    // handed to `vp check --fix`. The sub-app has its own formatting/lint
    // setup and is expected to be handled by its own pipeline, not by the
    // root workspace's rules. Without this filter, `vp check --fix` would
    // receive the excluded file list and fail because the `ignorePatterns`
    // below would silently drop every input file.
    "**/*": (files: readonly string[]) => {
      const filtered = files.filter((f) => !isInVueVbenAdmin(f));
      if (filtered.length === 0) return [];
      // lint-staged treats each entry of a returned array as a separate
      // command (not as a single argv), so we must return one shell-quoted
      // string. `JSON.stringify` produces a safely-escaped, double-quoted
      // path for each file.
      return `pnpm exec vp check --fix ${filtered.map((f) => JSON.stringify(f)).join(" ")}`;
    },
  },
  fmt: {
    ignorePatterns: ["apps/vue-vben-admin/**", "apps/vue-vben-admin", "**/.*/**", "**/.*"],
  },
  lint: {
    ignorePatterns: ["apps/vue-vben-admin/**", "apps/vue-vben-admin", "**/.*/**", "**/.*"],
    jsPlugins: [{ name: "vite-plus", specifier: "vite-plus/oxlint-plugin" }],
    rules: { "vite-plus/prefer-vite-plus-imports": "error" },
    options: { typeAware: true, typeCheck: true },
  },
  run: {
    cache: true,
  },
});
