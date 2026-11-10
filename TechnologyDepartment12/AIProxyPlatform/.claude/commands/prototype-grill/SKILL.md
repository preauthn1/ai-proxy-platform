---
name: prototype-grill
description: "原型前先打磨问题：Research → Grill → Frame → Build → Play → Capture。适合设计目标模糊、需要访谈后再做 throwaway 原型。"
argument-hint: "<模糊的设计主题 / 想探索的方向>"
---

# prototype-grill

`/prototype-design` 的访谈加强版：先把「该回答什么问题」磨清楚，再构建 throwaway 原型。仍**不交付生产功能**；答案留下，原型代码默认作 **primary source** 进 throwaway branch（非 main）。

对齐 ask-matt：Grill 打磨想法 → 需要可运行答案时走 Prototype → 用 `/handoff` 在 session 间桥接 → 结论回到 main flow。

**不走本 command 时：**

| 情境 | 改用 |
|------|------|
| 问题已经一句话说清，直接做原型 | `/prototype-design` |
| 要正式实现功能 | `/develop` / `/develop-grill` |
| 只需访谈、不需要可运行物 | `/grill-with-docs`（有 repo）或 `/grill-me`（无 repo） |
| 路径都看不清的巨大 fog | `/wayfinder` |
| 缺的知识在别人那里 | `/to-questionnaire` |
| 刚说的话没听懂 | `/wait-what` |

Research → Grill **无门禁**；Grill 之后进入 Frame/Build 必须走门禁。

## Theme: $ARGUMENTS

若 `$ARGUMENTS` 为空，先确认想探索的主题或痛点（允许模糊，Grill 会收紧）。

### 门禁（通用）

展示当前阶段结论后，**立刻**用 AskUserQuestion（一次一问）。

| 过渡 | 问题 | 继续 | 补充 |
|------|------|------|------|
| Grill → Frame | 是否已收成可 prototype 的问题？ | `框定并构建 (Recommended)` | `继续访谈` / `不需要原型，结束` |
| Frame/Research → Build | 问题与分支是否确认？ | `开始构建 (Recommended)` | `需要补充` / `回 Grill` |
| Play → Capture | 是否已得到可记录的结论？ | `记录结论 (Recommended)` | `继续玩 / 改原型` |
| Capture → 收尾 | 原型代码怎么处理？ | `提交到 throwaway branch 作 primary source (Recommended)` | `丢弃工作区改动，只保留 decision` / `暂留本地` |

- 禁止软确认跳过门禁
- Grill 收成「其实纸面已够」时：允许选 `不需要原型，结束`，输出决策摘要即可
- **禁止**把本 command 滑成 `/develop`（无 Capture、无 throwaway 纪律就写生产代码）
- **注意**：阶段门禁「一次一问」；**Grill 访谈**按 frontier 轮次（一轮可多问）

### Context hygiene

1. Research → Grill → Frame：尽量 **Continue** 同一 window，不中途 compact
2. 接近 **smart zone（~150k）**：在阶段边界决策（Continue → clear → handoff → subagent → compact）
3. Capture 后若进实现：`/handoff`（portability），**新 session** 跑 `/develop` / `/develop-grill`
4. 原型与实现分 session，避免 throwaway 与 production 混写

### 硬规则

与 `/prototype-design` 相同：throwaway、一问一答（一个设计问题）、易启动（Logic=单 HTML / UI=项目 runner）、默认不持久化、跳过 polish、暴露 state、main 只留 decision、原型默认进 throwaway branch 作 primary source。

---

### Phase 1: Research（轻量事实）

为 Grill 准备材料，**不**拍板视觉或状态模型。

**探索清单：**

1. 相关页面/模块/用户流程入口
2. 现有 UI 模式或 domain 状态约定（可对照路径）
3. 已知约束（权限、数据形态、设计系统）
4. 从代码与产品语言里露出的**张力**（互相打架的假设、说不清的状态名）——标成 Grill 议题

**输出：**

- 相关路径与可复用模式
- **待确认决策列表**（留给 Grill）
- 初步猜测：更像 Logic 问题还是 UI 问题（仅作假设，Grill 后可改）

不做五维生产评分；若连宿主都找不到且主题过宽，建议 `/wayfinder` 或收窄主题后再来。

Research 展示后**直接进入 Grill**（无门禁）。

---

### Phase 2: Grill

使用 `/grill-with-docs`（有 codebase）驱动；核心纪律对齐更新后的 `/grilling`：

- **design tree + frontier 轮次**：一轮抛出整个 frontier（编号题 + 推荐答案），等用户答完再进入下一轮——**不是**永远一次只问一题
- 题型：

```text
❓ **Q1** - **<题目标题>**: <题干>

➡️ <推荐答案>
```

- fact 派 subagent 查 codebase，不阻塞本轮其它就绪问题；decision 交给用户
- `/domain-modeling`：术语当场进 `CONTEXT.md`，硬权衡才 ADR
- 重点磨到能 prototype 的粒度，而不是磨成完整 PRD

**Grill 在本 command 的完成标准（与 develop-grill 不同）：**

不必完成全部产品边界，但必须能写出：

1. **可检验的 QUESTION**（一个，不是五个）
2. **SUCCESS 判据**（怎样算答到了）
3. **Logic vs UI** 倾向及理由
4. **Out of scope**（本原型不碰什么）

收尾展示「共同理解 → 原型问题」摘要，再走 **Grill → Frame** 门禁。

| 用户选择 | 动作 |
|----------|------|
| `框定并构建` | 进入 Phase 3 |
| `继续访谈` | 留在 Grill |
| `不需要原型，结束` | 输出决策摘要与建议的 main-flow 下一步；**结束本 command** |

---

### Phase 3: Frame + 就绪检查

把 Grill 结论写成固定格式：

```text
QUESTION: [一句话]
SUCCESS:  [verdict 判据]
BRANCH:   Logic | UI
HOST:     [Logic: HTML 落地路径；UI: module/路由 + sub-shape A/B]
OUT OF SCOPE: [...]
RUN (draft): [Logic: 打开哪个 HTML；UI: 项目 runner 命令]
```

快速就绪检查（不必重做完整生产 Research）：

| 检查 | 失败时 |
|------|--------|
| QUESTION 可 yes/no 或选出 variant | 回 Grill |
| BRANCH 与问题匹配 | 纠正分支或回 Grill |
| HOST 存在或可建 throwaway 且标记清晰 | 补 5 分钟探索 |
| 仍是**一个**问题 | 拆问题：只 prototype 最不确定的那一个 |

展示 Frame 后走 **Frame → Build** 门禁。

---

### Phase 4: Build

与 `/prototype-design` Phase 2 相同：按 skill 名加载并执行 `/prototype`（不写文件系统路径）。

1. 打开 `/prototype` 的 `SKILL.md`
2. 在**该 skill 自己的目录**内读分支文件并全量遵守：

- **Logic** → `/prototype` 的 `LOGIC.md`：单文件 HTML + pure module + free-play + guided walkthroughs
- **UI** → `/prototype` 的 `UI.md`：默认 3 个结构不同的 variants + `?variant=` + 浮动条；优先宿主页

给出启动方式 → 进入 Play。

---

### Phase 5: Play

1. 用户按启动方式驱动；你只做小步修改
2. 记录「惊讶点」与偏好（尤其 UI：选了哪个 variant、要偷哪部分）
3. 能给出 verdict 后走 **Play → Capture** 门禁

---

### Phase 6: Capture

```text
PROTOTYPE CAPTURE
Question:  [...]
Verdict:   [...]
Evidence:  [操作 / 选中的 variant / 推翻的假设]
Grill decisions retained: [访谈中仍成立的决策]
Invalidated by prototype: [跑完后作废的假设]
Carry forward:
  - [...]
Discard from main:
  - [...]
Primary source branch:
  - prototype/<slug>（默认）
Next:
  - [ ] /handoff → 新 session /develop 或 /develop-grill
  - [ ] 结论已写入 CONTEXT/ADR（若 Grill 启用了 docs）
  - [ ] 无需实现
```

**Capture → 收尾** 门禁：

| 选项 | 行为 |
|------|------|
| 提交到 throwaway branch 作 primary source | **默认**；非 main 保留 + issue/handoff 指针 |
| 丢弃工作区改动，只保留 decision | decision 进 handoff / ADR / issue |
| 暂留本地 | 不 commit，提醒仍是 throwaway |

若 Grill 写过 `CONTEXT.md` / ADR：把 **Verdict** 与作废假设同步回去，避免文档仍描述未验证设计。

---

### 阶段衔接速查

```text
Research（事实与张力）
  → Grill（frontier 轮次 → 一个可检验问题）
    → 门禁 → Frame（QUESTION + BRANCH + HOST）
      → 门禁 → Build（throwaway：Logic HTML / UI variants）
        → Play
          → 门禁 → Capture（verdict）
            → 门禁 → throwaway branch（默认）| 丢弃 | 暂留
              → handoff → main flow（可选）
```

### 完成时

- 有 QUESTION → Verdict 闭环；访谈假设与原型证据已对齐
- 原型代码按用户选择处理；main 无未标记的实验代码
- 下一步（实现 / 停止 / 再 prototype 另一个问题）已说清；若再 prototype **另一个**问题，新开本 command 或 `/prototype-design`，不要在同一原型上叠问题
