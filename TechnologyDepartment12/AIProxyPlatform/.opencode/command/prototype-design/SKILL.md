---
name: prototype-design
description: "专为原型设计：Frame → Research → Build → Play → Capture。用 throwaway 原型回答一个设计问题，不交付生产功能。"
argument-hint: "<要回答的设计问题 / 原型主题>"
---

# prototype-design

对齐 ask-matt 的 **prototype 绕行**：用一次性代码回答**一个**设计问题。保留答案，默认不把原型代码合进 main。

与 `/develop` 的区别：**develop 交付功能**；本 command **验证假设**。问题答完就结束，或把 decision 交接回 main flow。

**不走本 command 时：**

| 情境 | 改用 |
|------|------|
| 问题本身模糊，不知道该 prototype 什么 | `/prototype-grill` |
| 已决定方案，要正式实现 | `/develop` 或 `/develop-grill` |
| 纸面访谈就能说清，无需可运行物 | `/grill-with-docs` / `/grill-me` |
| 只想直接跑底层 skill、自己编排 | `/prototype` |

Frame → Research **无门禁**（问题清晰且评分达标后直接 Build）；Build 完成后的 Play / Capture 必须走门禁。

## Question: $ARGUMENTS

若 `$ARGUMENTS` 为空，先确认：**这个原型要回答什么问题？**（一句话，可检验。）

### 门禁（通用）

展示当前阶段结论后，**立刻**用 AskUserQuestion（一次一问）。禁止只写「停止/等待批准」或静默继续。

| 过渡 | 问题 | 继续 | 补充 |
|------|------|------|------|
| Research → Build | 问题与分支是否确认？ | `开始构建 (Recommended)` | `需要补充` / `问题不对，重框` |
| Play → Capture | 是否已得到可记录的结论？ | `记录结论 (Recommended)` | `继续玩 / 改原型` |
| Capture → 收尾 | 原型代码怎么处理？ | `丢弃工作区改动，只保留 decision (Recommended)` | `提交到 throwaway branch` / `暂留本地` |

- 只有选「继续」类选项才可进入下一阶段
- 禁止凭 "ok" / "good" 等软确认跳过门禁
- **禁止**在本 command 里顺手做成生产实现（那是 `/develop`）

### Context hygiene

- Frame → Research → Build → Play：尽量同一 window；接近 smart zone 时用 `/handoff`，不要硬撑
- Capture 之后若要进正式实现：用 `/handoff` 导出 decision，**新 session** 跑 `/develop` 或 `/develop-grill`
- 原型 session 与实现 session 分开，避免 throwaway 代码与 production 改动搅在一起

### 硬规则（贯穿全程）

1. **Throwaway from day one**：命名/路径标明 prototype；贴近将被使用的 module 或 page，但不伪装成 production
2. **一个问题**：不顺带解决第二个设计问题；范围漂移就停下来重框
3. **一条命令可运行**：匹配项目现有 runner（`pnpm` / `vp` / `python` 等）
4. **默认不持久化**：内存 state；若问题本身关于 persistence，用可擦的 scratch
5. **跳过 polish**：无 tests、无多余抽象、error handling 只求能跑
6. **暴露 state**：每次 action（Logic）或 variant 切换（UI）后能看见完整相关 state
7. **Main 只留 decision**：验证过的结论可写回 ADR / issue / handoff；原型代码默认不进 main

---

### Phase 0: Frame（框定问题）

写代码前必须写清：

```text
QUESTION: [一句话可检验的问题]
SUCCESS:  [怎样算「答到了」——用户能说出的 verdict]
OUT OF SCOPE: [本原型明确不碰的]
```

然后选分支（AskUserQuestion，若用户已在参数里说清可直接采用并展示）：

| 分支 | 何时 | 产物形状 |
|------|------|----------|
| **Logic** | state / 业务规则 / 数据形状 / API 手感 | 可运行 terminal TUI + 可搬运的 pure logic module |
| **UI** | 长什么样 / 布局与信息层级 | 同路由多 variant + `?variant=` + 浮动切换条 |

选错分支会浪费整个 session。后端模块/状态机 → 默认 Logic；页面/组件 → 默认 UI。模糊时问用户，不要猜完就开干。

**完成标准：** QUESTION / SUCCESS / 分支三者已展示且无歧义 → 进入 Research。

---

### Phase 1: Research（轻量，服务原型）

目标不是生产级改动清单，而是：**宿主在哪、可复用什么、原型怎么挂上去**。

#### Logic 探索清单

1. 相关 domain 类型、现有 reducer / service / 状态约定
2. 关键 edge cases（从代码与产品语言里挖）
3. 宿主语言与 runner（不要为原型引入新 runtime）

#### UI 探索清单

1. **宿主页**（优先 sub-shape A）：能否嵌在现有路由？真实 header/sidebar/data 比真空页更能暴露问题
2. 组件库与样式体系（Tailwind / shadcn / Vben 等）与可对照的同类页面
3. 路由约定；仅当确实无宿主时才考虑 throwaway route（sub-shape B，path/文件名含 `prototype`）

#### 原型就绪评分（每项 0-20，总分 0-100）

| 维度 | 问题 | 高分标志 | 低分标志 |
|------|------|----------|----------|
| 问题可检验性 | SUCCESS 是否可判定？ | 用户能说 yes/no 或选出 variant | 目标仍是「看看感觉」 |
| 分支匹配度 | Logic/UI 是否选对？ | 产物形状直接回答 QUESTION | 用 UI 答状态机或相反 |
| 宿主清晰度 | 挂在哪、怎么跑？ | 有明确 page/module + 一条 run 命令设想 | 无处安放或需新基建 |
| 约束已知度 | 设计系统/domain 边界？ | 知道必须遵守的 pattern | 会发明第二套 UI/模型 |
| 范围克制度 | 是否仍是一个问题？ | Out of scope 清楚 | 已滑向 mini-product |

**决策：**

- >= 70：展示输出后走 **Research → Build** 门禁
- < 70：补最低维；若主要是「问题说不清」→ 建议 `/prototype-grill`
- 若其实不需要可运行物 → 建议改访谈 skill，不要硬做原型

**Research 输出（门禁前必须展示）：**

- QUESTION / 分支 /（UI 时）sub-shape A 或 B 与宿主路径
- 可复用模式与关键约束
- 评分表
- 拟定 run 命令

---

### Phase 2: Build

门禁通过后，按分支**加载并执行** `/prototype`（与 `/tdd`、`/grilling` 一样按 skill 名解析，不写文件系统路径；各 CLI 的 skill 根目录可能不同）。

1. 用当前环境的 skill 机制打开 `/prototype` 的 `SKILL.md`
2. 在**该 skill 自己的目录**内读分支文件并全量遵守（`LOGIC.md` / `UI.md` 与 `SKILL.md` 同目录）：

- **Logic** → `/prototype` 的 `LOGIC.md`
  - 先写问题 comment/README
  - pure logic 与 TUI 分离；TUI 只是薄壳
  - 一帧内：当前 state + 快捷键；每 action 整帧重绘
- **UI** → `/prototype` 的 `UI.md`
  - 默认 **3** 个**结构上**不同的 variants（最多 5）；禁止只换配色的假对比
  - 优先现有路由 + `?variant=`；浮动底栏切换
  - 保留真实 data fetching，只替换渲染子树（sub-shape A）

Build 过程中**不**写生产抽象、**不**接真库（除非问题就是 persistence）、**不**写测试。

**完成标准：** 给出一条可复制的 run 命令，并说明用户第一步该做什么 → 进入 Play。

---

### Phase 3: Play

把控制权交给用户：

1. 贴出 run 命令与操作提示（Logic：按哪些键；UI：如何切 variant / URL）
2. 用户驱动；你根据反馈**小步**改原型（加 action、调 variant），不重启成新功能
3. 捕捉高价值时刻：「这不该发生」「我以为会是 X」「选 B 但要 A 的导航」

当用户能给出 verdict，或明确说还要改时，走 **Play → Capture** 门禁。

- `记录结论` → Phase 4
- `继续玩 / 改原型` → 留在 Play，改完再问

---

### Phase 4: Capture

原型的价值是 **answer**，不是仓库里的实验代码。

**必须产出（展示给用户）：**

```text
PROTOTYPE CAPTURE
Question:  [原问题]
Verdict:   [验证后的结论——可执行的决策，不是感想]
Evidence:  [哪些操作/variant 支撑了结论]
Carry forward:
  - [可搬进正式实现的决策：state shape / reducer / 选定 layout 要点…]
  - [若有 decision-dense snippet（状态机、类型），可摘录并标注来自 prototype]
Discard:
  - [明确扔掉的：TUI 壳 / 落选 variants / 临时路由…]
Next:
  - [ ] 回 main flow：/develop 或 /develop-grill（新 session + handoff）
  - [ ] 或：结论已够，无需实现
```

然后走 **Capture → 收尾** 门禁处理代码：

| 选项 | 行为 |
|------|------|
| 丢弃工作区改动，只保留 decision | 不 commit 原型；decision 写入 handoff / 用户指定的 ADR 或 issue 注释 |
| 提交到 throwaway branch | 在 **非 main** 分支 commit 原型作 primary source；在 issue/handoff 留 branch 指针；**不**合入 main |
| 暂留本地 | 不 commit；提醒用户工作区仍是 throwaway |

Logic 特例：验证过的 pure module **可以**作为 carry-forward 的候选，但仍须经正式 `/develop` 路径进入 production，不在本 command 里「顺便合进主流程」。

---

### 阶段衔接速查

```text
Frame（一个可检验问题 + Logic|UI）
  → Research（宿主 / 约束 / 就绪评分）
    → 门禁 → Build（throwaway，一条命令可跑）
      → Play（用户驱动，小步改）
        → 门禁 → Capture（verdict + carry-forward）
          → 门禁 → 丢弃 | throwaway branch | 暂留
            → （可选）handoff → 新 session /develop*
```

### 完成时

- 有书面 **Verdict** 与 **Carry forward**
- 原型代码已按用户选择丢弃 / 进 throwaway branch / 暂留
- 若要继续实现：已提示用 `/handoff` + 新 session 的 `/develop` 或 `/develop-grill`，**不要**在本 session 无门禁切去写生产代码
