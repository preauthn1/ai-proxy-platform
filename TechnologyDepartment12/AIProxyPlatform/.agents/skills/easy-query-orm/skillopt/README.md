# SkillOpt Assets

This directory stores local SkillOpt assets used to optimize `SKILL.md`.

Structure:

- `configs/_base_/`: shared model and training defaults
- `configs/officeqa/`: standard configs for the built-in `officeqa` env
- `data/officeqa_split/`: train/val/test short-answer QA items

Notes:

- This repository now follows the standard `SkillOpt` workflow by reusing the built-in `officeqa` env instead of a source extension.
- `officeqa` still uses short-answer EM/F1 style evaluation, so dataset items should stay narrow, uniquely scorable, and grounded in the upstream easy-query source and docs named by the config `data_dirs`.
- API keys should stay in environment variables such as `QWEN_CHAT_API_KEY`, `OPTIMIZER_QWEN_CHAT_API_KEY`, and `TARGET_QWEN_CHAT_API_KEY` rather than in tracked yaml files.
- Model names for the standard `qwen_chat` backend should also be provided by environment variables such as `QWEN_CHAT_MODEL`, `OPTIMIZER_QWEN_CHAT_MODEL`, and `TARGET_QWEN_CHAT_MODEL`.
- A successful standard run was verified with `skillopt-train --config skillopt/configs/officeqa/easy-query-orm-mimo-fast.yaml`.

Excluded from git:

- `runs/`: training outputs and rollout artifacts

These files are maintenance assets for this repository. They are not part of the installed Codex skill surface.
