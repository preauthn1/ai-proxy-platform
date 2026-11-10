// Root commitlint config for the monorepo workspace.
// Without this, commitlint 19+ reports [empty-rules] even on well-formed
// conventional messages; it no longer auto-loads @commitlint/config-conventional.
// The vue-vben-admin submodule carries its own override
// (apps/vue-vben-admin/.commitlintrc.js) that pulls @vben/commitlint-config.
export default {
  extends: ['@commitlint/config-conventional'],
};
