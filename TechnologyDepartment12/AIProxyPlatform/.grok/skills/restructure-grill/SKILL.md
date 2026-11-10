---
name: restructure-grill
description: "重构/架构升级完整路径：Research（含漏洞基线）→ Grill（含风险处置）→（可选 Prototype）→ 规模分支（S0|S1|S2）。适合 seam、intent、breaking、既有漏洞处置未钉死时。"
argument-hint: "<模糊的结构债 / 升级方向 / module 区域>"
---

# restructure-grill

`/restructure` 的访谈加强版：先摸清 codebase 与 **既有漏洞/风险基线**，再把 **intent、seam、深度目标、爆炸半径、漏洞处置、迁移策略** 磨清楚，再按规模选择 S0 单 session / S1 单 ticket / S2 多 ticket。

对齐 ask-matt idea→ship 编排，但是 **结构债 / 架构升级** 语义（sibling of `/develop-grill`，不是功能交付）。

**用语**：`/codebase-design` 的 **module / interface / depth / seam / adapter / leverage / locality**；领域词对齐 `CONTEXT.md`。

**硬规则 — 重构前先排洞：** 与 `/restructure` 相同——触达面内建立 **HAZARD BASELINE**，每条必须有处置（fix-first / lock / fix-in-scope / defer）。禁止结构美化掩盖已知洞；C/H 不得静默 defer。

**不走本 command 时：**

| 情境 | 改用 |
|------|------|
| 目标已清晰、无需访谈 | `/restructure` |
| 要先扫 deepening 候选报告 | `/improve-codebase-architecture`，选完再本 command 或 `/restructure` |
| 新功能 | `/develop` / `/develop-grill` |
| 没有 codebase 的纯设计 | `/grill-me` |
| 路径都看不清的巨大 fog | `/wayfinder`，清晰后再来 |
| 已有 agent-ready ticket | 直接 `/implement` |
| 触达面难复现缺陷阻塞 | `/diagnosing-bugs` 后把结论写回基线 |
| 刚说的话没听懂 | `/wait-what` |

Research → Grill **无门禁**；Grill 之后的阶段过渡必须走门禁（Prototype 绕行除外，见下）。禁止静默跨阶段。

## Target: $ARGUMENTS

若 `$ARGUMENTS` 为空，先确认想处理的结构债或升级方向（允许模糊，Grill 会收紧）。

### Intent（贯穿全程）

| Intent | 含义 | 验收底线 |
|--------|------|----------|
| **preserve** | 行为与契约不变 | 旧测 + characterization；禁止顺手改产品行为；可附 depth/可导航验收 |
| **upgrade** | 允许契约/行为变 | **强制** breaking surface + 迁移/兼容策略 |

- **默认倾向 preserve**；upgrade 须在 Grill 或门禁中显式钉死
- 实现中 intent 漂移 → **停**，回门禁

### 门禁（通用）

展示当前阶段结论后，**立刻**用 AskUserQuestion（一次一问）。

| 过渡 | 问题 | 继续 | 补充 |
|------|------|------|------|
| Grill → 下一跳 | 共同理解是否足够继续？（含漏洞处置） | `进入下一阶段 (Recommended)` | `继续访谈` / `先 Prototype` / `先处理 fix-first 漏洞` |
| Plan → Implement（S0） | 是否批准实现？ | `批准并继续 (Recommended)` | `需要补充` / `改走 S1 或 S2` |
| Plan → Implement（多切片） | 实现顺序？ | 见 **实现编排门禁** | — |
| Spec/Tickets 发布前 | 拆分是否批准？ | S2 由 `/to-tickets` quiz 处理 | — |
| Review → Commit（S0） | 是否提交？ | `提交 (Recommended)` | `暂不提交` |

- 禁止软确认跳过门禁
- Grill 结束前：不起草完整 Plan、不写实现产物（允许极短「待确认假设」列表）
- **阶段门禁**一次一问；**Grill 访谈**按 frontier 轮次（一轮可多问）——二者不要混用

#### 实现编排门禁（仅 S0 多独立切片）

与 `/restructure` / `/develop` 相同：有依赖则串行不询问；相互独立则问串行 / 部分并行 / 全并行 / 需要补充。写入 Plan「实现顺序」。并行结果回主 session 合并验证。

### Context hygiene

1. **Research → Grill →（Prototype 回收）→ 规模 / Plan / to-spec**：尽量 **Continue** 同一 window
2. **smart zone ~150k**：阶段边界 Continue → clear → handoff（portability）→ subagent → compact
3. **每个 `/implement`**（S1/S2 每张票）**fresh session** + tickets 间 **`/clear`**
4. Prototype 绕行因换目录/分叉适合 `/handoff`；同目录仅 token 压力时优先 compact

### 强制 / 按需 skill

| 何时 | Skill |
|------|--------|
| Grill | `/grill-with-docs`（`/grilling` + `/domain-modeling`） |
| 架构用语与加深 | `/codebase-design`；seam 争议时 **按需** design-it-twice |
| 触达面难复现缺陷 | `/diagnosing-bugs`，结论回写基线 |
| S0 实现 | `/tdd` + `/code-review`（含基线复核） |
| S1/S2 | 新 session `/implement` |
| 术语/ADR | Grill 中当场更新 `CONTEXT.md`；硬权衡才 ADR |

漏洞基线的 **清单、表格式、处置枚举、C/H 规则** 与 `/restructure` 的「漏洞与风险基线」一节相同；本文件不重复粘贴细则，执行时按该节（或同文镜像 skill）遵守。

---

### Phase 1: Research

为 Grill 准备**事实材料**（路径、调用链、**漏洞基线草稿**、测试缺口、ADR），把 **决策**（含每条洞的处置）留给 Grill。

**探索清单：**

1. 相关 module 与调用方；shallow / 摩擦点
2. 爆炸半径（跨包、schema、生成代码、测试）
3. **漏洞与风险基线扫描**（鉴权、注入、密钥、Web 经典面、已知 FIXME/SECURITY、并发洞、危险 API）→ 填 HAZARD 表草稿；处置可标 `待 Grill`
4. 现有测试与 characterization 缺口（尤其对应可疑洞）
5. 是否像 wide refactor；有无 expand–contract 先例
6. 用户未点名且热点不明 → 可建议 `/improve-codebase-architecture` 作上游；不阻塞「已有区域」的 Research
7. 从代码与文档露出的**张力**（分层假设打架、说不清的 seam、**修洞 vs 纯搬家**冲突）→ 标成 Grill 议题

**输出（进入 Grill 前必须展示）：**

- 相关路径 / module 列表
- 可复用模式与 ADR 约束
- **HAZARD BASELINE 草稿**（允许处置=待 Grill，但不得整表缺失）
- 推断 intent 初值（默认 preserve）与 wide 初判
- **待确认决策**列表（Grill 议题，含高危处置，不自行拍板）
- 重构五维评分表

#### 评分体系（重构五维，0–20×5）

| 维度 | 问题 | 高分 | 低分 |
|------|------|------|------|
| 目标清晰度 | module/seam/intent？ | 可列出路径与候选 seam | 只有情绪化痛点 |
| 依赖 / 爆炸半径 | 谁会炸？ | 调用链清楚 | 未知扩散 |
| 行为与风险锁定 | 基线是否扫过？如何证明不恶化？ | 基线草稿 + 测例/契约线索 | 未排洞 |
| 迁移策略 | 怎么搬？fix-first 是否前置？ | 有步骤假设 | 只有终态 |
| 验证策略 | 怎么验？ | 命令可写 | 模糊 |

**决策：**

- >= 70 且已有基线草稿：展示后**直接进 Phase 2 Grill**（无门禁）
- 「行为与风险锁定」< 10 或无基线草稿：先补排洞再 Grill
- < 70：补最低维后重评；或带着差距问用户是否进 Grill
- fog 极大（连调查路径都无）→ 建议 `/wayfinder` 或先 `/improve-codebase-architecture`

---

### Phase 2: Grill

使用 `/grill-with-docs` 驱动。

**访谈纪律：**

1. 议题画成 **design tree**；按 **rounds** 推进
2. 每轮只问当前 **frontier**（前置已定、不依赖本轮其它未答题）
3. 一轮抛出整个 frontier：编号 + 推荐答案，**等用户答完再下一轮**
4. 题型：

```text
❓ **Q1** - **<题目标题>**: <题干，可含选项>

➡️ <推荐答案>
```

5. **fact** 派 subagent 查 codebase，不拿事实问用户；探索未回不阻塞本轮其它就绪题
6. **decision** 交给用户；frontier 清空前不当「已经理解」
7. `/domain-modeling`：术语当场进 `CONTEXT.md`；硬权衡才 ADR
8. 架构方案争议：按需 design-it-twice，再把选择收回 Grill
9. **漏洞处置是决策不是事实**：agent 扫出条目与证据；fix-first / lock / fix-in-scope / defer 由用户在 frontier 确认（C/H 推荐 fix-first 或 fix-in-scope）

**本 command 建议覆盖的决策族（按树展开，不必一轮问完）：**

- Intent：preserve vs upgrade
- 目标 module 与 seam 位置；加深后的 interface 形状
- **Hazard 每条处置**（尤其 C/H）；defer 必须进 Out of scope + 指针
- Out of scope（禁止顺手做功能；已 defer 的洞）
- 行为锁定 / breaking surface（与 lock 项对齐）
- 迁移：直接替换 vs expand–contract；是否 wide；fix-first 是否拆阻塞票
- 验收与回滚/兼容期；**基线未恶化**如何验

**收尾**：展示**共同理解摘要**（问题、intent、seam/方案边界、爆炸半径、**已确认 HAZARD 表**、迁移、验收、Out of scope、开放问题）→ **Grill → 下一跳**门禁。

共同理解不足条件：仍有 C/H 处置为「待定」→ 不得选「进入下一阶段」，应继续访谈或 `先处理 fix-first 漏洞`。

| 用户选择 | 动作 |
|----------|------|
| `进入下一阶段` | Phase 2.5 判断 → 规模分支 |
| `继续访谈` | 留在 Grill |
| `先 Prototype` | Phase 2.5 Prototype 绕行 |

---

### Phase 2.5: Prototype 绕行（可选）

仅当 **intent=upgrade** 且某个结构/分层问题**无法在对话里可靠解决**时（典型：新 module 边界手感、必须看见的交互分层）。preserve 的纯搬家 **默认不** prototype。

流程：

1. `/handoff` 导出 Grill 摘要与待验证问题
2. Fresh session：`/prototype-design` 或 `/prototype-grill`
3. `/handoff` 带回 **Capture verdict**；写回共同理解 / ADR
4. 原型代码 throwaway branch 作 primary source；再进**规模分支**，不跳过规模判断直接写生产重构

纸面与测试计划能说清的不要 prototype。整段工作若从一开始只是探索 UI/状态机，应直接 `/prototype-*`，不是本 command。

---

### Phase 3: 规模分支

共同理解稳定后（Prototype 如需已回收），判断规模：

| 级别 | 何时 | 动作 |
|------|------|------|
| **S0 单 session** | 一条可验证切片装进当前 window；非默认 wide | Phase 3A Plan → Implement → Review |
| **S1 单 ticket** | 需换 session，但仍是一张 agent-ready 票 | Phase 3B 发布 1 ticket 后结束 |
| **S2 多 ticket** | 多切片；或 **wide 默认**；多 batch migrate | Phase 3C to-spec → to-tickets 后结束 |

**Wide：** 默认 S2；仅当整条 expand–contract 单 session 可验完 → 允许 S0/S1。拿不准偏保守升档。

#### 3A. Plan → Implement → Review（S0）

Plan 模板与纪律同 `/restructure`（必须含 Intent、Breaking surface、Migration、**Hazard baseline 四类处置**、先 fix-first/锁测再搬）。展示后 Plan 门禁 → 编排门禁（若需）→ Implement（`/tdd`；fix-first 完成前不改结构；不自动 commit）→ `/code-review` + **基线复核** → 提交门禁。

```text
PLAN: [Restructure Name]

Intent: preserve | upgrade
Breaking surface: [...]
Migration: [...]

Hazard baseline:
- fix-first: [...]
- lock: [...]
- fix-in-scope: [...]
- defer: [...]

Goal: [...]
Out of scope: [...; 含 defer]

Files to modify:
1. ...

Approach:
0. [fix-first 若有]
1. [lock / 行为锁定]
2. [结构迁移]
3. [fix-in-scope + 基线未恶化]

Risks:
- ...

Test strategy:
- [...; 基线项]
```

#### 3B. S1 单 ticket

直接 1 张 ready-for-agent ticket（`.scratch/<slug>/issues/01-….md` 或真实 tracker）。含 Intent、Acceptance（**含基线 lock/fix-in-scope**）、验证命令、Hazard 摘要、upgrade 契约摘要；fix-first 拆票时写 Blocked by。发布后：

```text
下一步（每个 ticket 一个 fresh session）：
1. 打开新 session
2. /clear
3. /implement <ticket 引用>
```

默认本 command 结束。写票时发现要拆 → 升 S2。

#### 3C. S2：to-spec → to-tickets

1. `/to-spec`：Grill + Research（+ Prototype），不再访谈；**Hazard 与处置**写入 spec
2. `/to-tickets`：vertical 或 wide expand–contract；**fix-first block 结构票**；blocking edges；quiz 后发布
3. 列出 frontier + per-ticket `/implement` 指引；**不要**同一 session 连续多票

---

### 阶段衔接速查

```text
Research（事实 + 漏洞基线草稿 + 五维 + Grill 议题）
  → Grill（design tree；钉死每条 Hazard 处置 + intent/seam）
    → [可选] Prototype 绕行（upgrade 手感；handoff 来回）
      → 规模
           ├─ S0: Plan(含 Hazard) → Implement → Review(基线复核) → 提交门禁
           ├─ S1: 1 ticket（含基线 Acceptance）→ implement 指引
           └─ S2: to-spec → to-tickets（fix-first block）→ frontier 指引
```

### 完成时

- **S0**：已 review；基线复核通过；按选择提交或留 diff；CONTEXT/ADR 若 Grill 写过则已更新
- **S1/S2**：ticket(s) 已发布（含 Hazard）；fresh session implement 指引已给出
- context 压力退出：阶段边界决策 + 恢复点说明（含基线表指针）
