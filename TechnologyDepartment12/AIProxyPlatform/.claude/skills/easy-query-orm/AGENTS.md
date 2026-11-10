# AGENTS

Repository maintenance rules for this skill:

1. Keep `SKILL.md` frontmatter `description` at or below 1024 characters after
   YAML folded-line normalization. Target under 900 characters to leave margin.
2. Keep the frontmatter `description` focused on trigger coverage only:
   what the skill does, when to use it, and the highest-value capability
   buckets. Do not list every API name there.
3. Put routing logic, behavioral rules, and maintenance guidance in the
   `SKILL.md` body. Put detailed topic material in `references/`.
4. When adding a new capability, prefer:
   `description` summary -> `SKILL.md` routing -> `references/` detail.
   Do not expand the description with long API inventories.
5. Before commit, re-measure the `SKILL.md` description length. Use this
   PowerShell snippet from repo root:

```powershell
$c = Get-Content SKILL.md -Raw
if($c -match 'description:\s*>-\s*(?<d>(?s).*?)\n---'){
  $m = $matches['d'] -replace '\r?\n\s*',' '
  $m = $m.Trim()
  $m.Length
}
```

6. If `SKILL.md` routing or positioning changes materially, check whether
   `agents/openai.yaml` still matches the public skill framing.
7. Keep symbol/package lookup skill-local. For long-tail type names, expand
   `references/symbol-imports.md` instead of bloating `SKILL.md`, scattering
   guessed packages across topic references, or adding machine-specific helper
   scripts.
8. Do not add extra maintenance docs beyond this file unless there is a clear
   repository-level instruction need.
9. For local skill optimization, use standard `SkillOpt` only. In this
   repository the maintained path is the built-in `officeqa` env under
   `skillopt/configs/officeqa/`; do not extend `SkillOpt` source code or
   reintroduce the removed custom `skillqa` path.
10. Keep `SkillOpt` model credentials out of tracked yaml. Provide them by
    environment variables such as `QWEN_CHAT_API_KEY`,
    `OPTIMIZER_QWEN_CHAT_API_KEY`, `TARGET_QWEN_CHAT_API_KEY`, plus
    `QWEN_CHAT_MODEL`, `OPTIMIZER_QWEN_CHAT_MODEL`,
    `TARGET_QWEN_CHAT_MODEL`.
11. For the standard `qwen_chat` backend, this `SkillOpt` version reads
    `qwen_chat_*` config keys and `QWEN_CHAT_*` environment variables, not the
    older `qwen_*` naming. If the base URL is not recognized, the backend can
    fall back to `http://localhost:8000/v1`.
12. Keep `officeqa` assets aligned to upstream easy-query source and docs.
    Use `skillopt/data/officeqa_split/` for short-answer train/val/test items,
    and point retrieval at upstream source/doc directories through
    `data_dirs`; do not copy the skill's own text into a fake local corpus.
13. Standard verified run command shape:

```powershell
$env:PYTHONUTF8="1"
$env:PYTHONIOENCODING="utf-8"
$env:QWEN_CHAT_BASE_URL="https://api.xiaomimimo.com/v1"
$env:OPTIMIZER_QWEN_CHAT_BASE_URL="https://api.xiaomimimo.com/v1"
$env:TARGET_QWEN_CHAT_BASE_URL="https://api.xiaomimimo.com/v1"
$env:QWEN_CHAT_MODEL="mimo-v2.5-pro"
$env:OPTIMIZER_QWEN_CHAT_MODEL="mimo-v2.5-pro"
$env:TARGET_QWEN_CHAT_MODEL="mimo-v2.5-pro"
$env:QWEN_CHAT_API_KEY="..."
$env:OPTIMIZER_QWEN_CHAT_API_KEY="..."
$env:TARGET_QWEN_CHAT_API_KEY="..."
skillopt-train --config skillopt/configs/officeqa/easy-query-orm-mimo-fast.yaml --cfg-options env.out_root=skillopt/runs/<new-run-name>
```

14. Before committing optimization work, keep `skillopt/runs/` untracked and
    absorb only generalizable improvements back into `SKILL.md`; do not copy
    evaluation-format hacks or run artifacts into the installed skill.
