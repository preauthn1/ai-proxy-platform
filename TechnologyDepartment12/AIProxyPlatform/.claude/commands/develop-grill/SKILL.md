---
name: develop-grill
description: "idea → ship 完整路径：Research → Grill →（可选 Prototype）→ 规模分支（Plan|to-spec/to-tickets）→ Implement → Review。"
argument-hint: "<feature description>"
---

# develop-grill

对齐 ask-matt **main flow**（idea → ship）的编排版：先摸清 codebase，再访谈打磨，再按规模选择单 session 实现或拆 ticket 分批实现。

**不走本 command 时：**

| 情境 | 改用 |
|------|------|
| 范围已清晰、无需访谈 | `/develop` |
| 目标是验证设计而非交付功能 | `/prototype-design` 或 `/prototype-grill`（本 command 内的 Prototype 仅作绕行） |
| 没有 codebase 的纯设计讨论 | `/grill-me` |
| 一个 session 都看不清路径的巨大 fog | `/wayfinder`，路径清晰后再 `/to-spec` 或本 command |
| 已有 agent-ready ticket | 直接 `/implement` |
| 缺的知识在别人那里 | `/to-questionnaire`，回收后再 Grill / to-spec |
| 刚说的话没听懂 | `/wait-what`（可叠在任何阶段） |
| 只有人能点的基建/密钥 | 实现途中 `/wizard` |

Research → Grill **无门禁**；Grill 之后的阶段过渡必须走门禁（Prototype 绕行除外，见下）。禁止静默跨阶段。

## Feature: $ARGUMENTS

若 `$ARGUMENTS` 为空，先确认要构建的功能描述。

### 门禁（通用）

展示当前阶段结论后，**立刻**用 AskUserQuestion（一次一问）。禁止只写「停止/等待批准」或静默继续。

| 过渡 | 问题 | 继续 | 补充 |
|------|------|------|------|
| Grill → 下一跳 | 共同理解是否足够继续？ | `进入下一阶段 (Recommended)` | `继续访谈` / `先 Prototype` |
| Plan → Implement | 是否批准实现？ | `批准并继续 (Recommended)` | `需要补充` / `改走 multi-session` |
| Plan → Implement（多切片时） | 如何编排实现顺序？ | 见下方 **实现编排门禁** | — |
| Spec/Tickets 发布前 | 拆分是否批准？ | 由 `/to-tickets` 自身 quiz 处理 | — |
| Review → Commit | 是否提交变更？ | `提交 (Recommended)` | `暂不提交` |

- 只有选「继续」类选项才可进入下一阶段
- 禁止凭 "ok" / "good" 等软确认跳过门禁
- Grill 结束前：不起草完整 Plan、不写实现产物（允许极短的「待确认假设」列表）
- **注意**：阶段门禁用 AskUserQuestion「一次一问」；**Grill 访谈本身**按下方 **frontier 轮次**（一轮可多问），二者不要混用

#### 实现编排门禁（仅当计划含多个可独立验证的切片时触发）

当 Plan 的 Approach 里存在**多个互不阻塞、可分别 demo 的切片**（典型场景：一处共享基础 + 若干独立上层改动，或多个并列模块），在「批准实现」之后、进入 Implement 之前，**立刻**用 AskUserQuestion 追问一次编排方式。一次一问，给出 Plan 中实际的切片名而不是泛指。

判定是否触发：

- 切片之间**有依赖**（如切片 B 依赖切片 A 改的契约）→ **不触发**，按依赖顺序串行，无需询问
- 切片之间**相互独立**（改切片 A 不影响切片 B）→ **触发**

问题模板（`切片A / 切片B / 切片C` 按实际切片名替换）：

> 计划包含 切片A / 切片B / 切片C 三处独立改动，希望按什么顺序实现？

选项（推荐项置顶并标注 Recommended；示例中 切片A 视为共享基础，切片B / 切片C 视为依赖它的独立切片，按实际情况调整）：

| 选项 | 含义 | 适用 |
|------|------|------|
| `串行：切片A → 切片B → 切片C → code-review (Recommended)` | 依次实现，每步 typecheck/验证后再进入下一步，收尾统一 `/code-review` | 切片有隐含耦合、想逐步确认、或切片本身较重 |
| `并行：切片A → (切片B + 切片C 并行子代理) → code-review` | 共享基础先串行落地；独立切片派给并行 subagent 同时推进，合并后再 `/code-review` | 切片真正独立、各自规模适中、context window 充裕 |
| `全部并行：(切片A + 切片B + 切片C 并行子代理) → code-review` | 所有切片同时派给 subagent，收尾合并 review | 切片完全独立且无共享前置，追求最快交付 |
| `需要补充` | 对编排有疑问，回到 Plan 修订 | 依赖关系不清、切片划分不合理 |

约束：

- 选定编排后**写入 Plan 的 Approach 顶部**作为「实现顺序」一行，Implement 据此执行
- 并行选项仅在不违反 Context hygiene 的前提下可用；接近 smart zone 时强制降级为串行或在阶段边界决策
- 并行 subagent 的产出必须回主 session 合并 typecheck + 相关测试，再进 Review

### Context hygiene

对齐 ask-matt / PHASE-BOUNDARIES：

1. **Research → Grill →（Prototype 回收）→ to-spec / to-tickets 或 Plan**：尽量 **Continue** 在同一未中断 context window；grilling / spec / tickets 要建立在同一组 **primary source** 思考上
2. **smart zone** 约 **150k tokens**。接近上限时在**阶段边界**按序决策：Continue → `/clear`（下一步完全无关）→ `/handoff`（仅 portability：换 harness / 换目录 / 同事 / 中途分叉）→ subagent（可 AFK）→ **`/compact`**（默认）
3. **每个 `/implement`**（含 multi-session 的每个 ticket）从 **fresh session** 开始；tickets 之间 **`/clear`**，清空上一个 ticket 的上下文
4. `/handoff` 窄用。Prototype 绕行因**换目录 / 分叉旁路**而适合 handoff；同 harness 同目录只因 token 压力时优先 compact，不要默认 handoff
5. 不要在阶段**中途** compact；中途要分出去的探索用 subagent

---

### Phase 1: Research

探索 codebase，摸清范围后再打分。为 Grill 准备**事实材料**（路径、模式、约束），把**决策**留给 Grill。

**探索清单：**

1. **相关代码**：入口、路由/API、数据模型、UI 页面、测试与 fixtures
2. **已有模式**：同域 CRUD / 鉴权 / 列表筛选等可复用实现；记录可对照路径
3. **依赖与约束**：调用链、跨端/跨层约定、配置与 schema、权限与错误约定
4. **边界与验证**：空值/并发/权限/失败路径；现有测试或手动验收方式

**输出（进入 Grill 前必须展示）：**

- 相关文件/模块列表（尽量精确到路径）
- 可复用模式与关键约束
- 已知边界与**待确认决策**（明确标成 Grill 议题，不要自行拍板）
- 五维评分表

#### 评分体系

维度（每项 0-20，总分 0-100）：

| 维度 | 问题 | 高分标志 | 低分标志 |
|------|------|----------|----------|
| 范围清晰度 | 知道要改哪些文件？ | 能列出精确文件列表 | 只知道大致区域 |
| 模式熟悉度 | 有类似模式可参考？ | codebase 中有可直接参照的实现 | 需要从零设计 |
| 依赖感知 | 知道改了谁会影响什么？ | 完整的调用链和依赖图 | 不确定谁会受影响 |
| 边界情况 | 能识别边界情况？ | 列出了空值、并发、权限等边界 | 只考虑了 happy path |
| 测试策略 | 知道怎么验证？ | 有明确的手动或自动验证方法 | 验证方式模糊 |

**打分方法：**

1. 逐维给分 + 一句话理由（引用具体路径/符号，避免空话）
2. 汇总总分与通过/不通过
3. 不通过时：点名最低维、差距、下一步行动

**决策：**

- 总分 >= 70：展示输出后**直接进入 Phase 2**（无门禁）
- 总分 < 70：补最低维后重评；循环直到 >= 70，或说明无法提升的维度并询问是否带着差距进入 Grill
- 若 fog 极大（连调查路径都看不见）：建议改 `/wayfinder`，而不是在本 command 里空转 Research

---

### Phase 2: Grill

使用 `/grill-with-docs`（内部是 `/grilling` + `/domain-modeling`）驱动访谈。

**访谈纪律（对齐更新后的 `/grilling`）：**

把议题画成 **design tree**：每个决策挂着依赖它的后续决策。

1. **按轮次（rounds）推进**，不是永远「一次只问一个」
2. 每轮只问当前 **frontier**：前置已定、现在就能答、且不依赖本轮其它未答问题的决策集合
3. **一轮内抛出整个 frontier**：编号，每题给推荐答案，然后**等用户答完本轮再进入下一轮**
4. 题型格式：

```text
❓ **Q1** - **<题目标题>**: <题干，可多段，可含选项>

➡️ <你的推荐答案>
```

5. **fact** 是 agent 的活：需要查 filesystem / codebase 时派 subagent，不要拿事实问用户；探索未回不阻塞本轮其它已就绪问题
6. **decision** 交给用户；frontier 清空前不要当「已经理解了」
7. 维持 `CONTEXT.md` / glossary / ADR 纪律（`/domain-modeling`：术语当场写入 CONTEXT，硬权衡才开 ADR）
8. 收尾输出**共同理解摘要**（问题、方案边界、关键决策、Out of scope、开放问题），再走门禁

**Grill → 下一跳门禁**选项语义：

| 用户选择 | 动作 |
|----------|------|
| `进入下一阶段` | 进入 Phase 2.5 判断（Prototype / 规模分支） |
| `继续访谈` | 回到 Grill，针对开放问题继续 |
| `先 Prototype` | 进入 Phase 2.5 Prototype 绕行 |

---

### Phase 2.5: Prototype 绕行（可选）

仅当某个问题**无法在对话里可靠解决**时绕行——典型：状态机/业务逻辑手感、必须亲眼看到的 UI。

流程（ask-matt Crossing sessions / handoff 因**换目录、分叉旁路**）：

1. `/handoff` 导出当前 idea thread（含 Grill 摘要与待验证问题）
2. 在 **fresh session** 基于 handoff 跑 **`/prototype-design`**（问题已清晰）或底层 `/prototype`；整段原型编排也可用 `/prototype-grill`（少见，本路径通常 Grill 已做过）
3. 再用 `/handoff` 把 **Capture verdict** 带回来；回到本 thread（或新 session 引用两份 handoff）后，把决策写回共同理解 / ADR
4. 原型本身按 `/prototype`：验证过的决策可吸收进正式设计；原型代码作 **primary source** 落在 `prototype/<name>` 等 **throwaway branch**（非 main），并在实现 issue 上留指针
5. 然后进入 **规模分支**——不要跳过规模判断直接写代码

纸面能说清的问题**不要** prototype。整段工作若从一开始就是「只探索设计」，应直接开 `/prototype-grill`，而不是本 command。

---

### Phase 3: 规模分支

共同理解稳定后（且 Prototype 如需要已回收），判断能否在**一个 context window** 内实现：

| 分支 | 何时 | 动作 |
|------|------|------|
| **A. 单 session** | 一条（或极少）tracer bullet 可 demo；预估当前 window 装得下 | Phase 3A Plan → Implement → Review |
| **B. multi-session** | 多条独立 vertical slices；或明显超出单 window | Phase 3B `/to-spec` → `/to-tickets`，然后**按 ticket 分 session `/implement`** |

拿不准时：若 Grill 摘要里的 User-facing 行为 ≥ 3 条可独立验收的切片，倾向 **B**。

#### 3A. Plan → Implement → Review（单 session）

**Plan**（只描述做什么，不写实现代码）：

1. Goal / Out of scope 对齐 Grill 共同理解
2. 文件列表来自 Research，并反映 Grill 决策
3. Approach 按依赖排序，优先窄而贯通的 tracer bullet
4. Risks + Test strategy；Grill 改过的范围要覆盖 Research 中过时假设
5. 若起草中发现装不进单 session → 改走 3B，不要硬塞
6. 人才能完成的步骤标 `/wizard`，不要写进 agent 自动步骤

```text
PLAN: [Feature Name]

Goal: [一句话]
Out of scope: [明确不做的]

Files to modify:
1. path/file.ts - [改什么 / 为什么]

New files:
1. path/new.ts - [用途]

Approach:
1. [步骤与理由]
2. [步骤与理由]

Risks:
- [问题] → [缓解]

Test strategy:
- [如何验证；涉及多端时分别写]
```

展示计划后走 **Plan → Implement** 门禁。

**Implement**：按门禁选定的**实现顺序**执行已批准计划（串行 / 并行 subagent / 全并行）；若门禁未触发编排问题，按 Approach 中依赖顺序串行执行。用 `/implement` 执行（优先 `/tdd`）；**并行编排时**每个 subagent 独立完成自己切片的 typecheck/单测，主 session 合并后再次跑全量 typecheck + 相关测试套件。**不要**在本 command 里自动 commit（裸 `/implement` 会 commit；本编排延后到 Review 门禁）。若实现中发现切片其实有依赖（原以为可并行）：暂停并行，回门禁重选串行。撞到人墙 → `/wizard`。

**Review & Commit**：`/code-review`（Standards + Spec 双轴；实现阶段已跑则汇总）；展示摘要后走提交门禁；**不自动提交**。

#### 3B. to-spec → to-tickets → 分 session implement（multi-session）

仍在**同一 context window**（若未超 smart zone）中：

1. **`/to-spec`**：综合 Grill + Research（+ Prototype 决策），**不再访谈**；确认测试 seams 后发布 spec（`ready-for-agent`）
2. **`/to-tickets`**：拆成 tracer-bullet tickets，声明 **blocking edges**；quiz 用户粒度与依赖后发布
   - Local tracker：`.scratch/<feature>/issues/` 每 ticket 一文件
   - 真实 tracker：native blocking / 文内 Blocked by
3. **本 command 在此收尾（推荐）**：列出 frontier（blockers 已完成、可立即开工的 tickets），并给出：

```text
下一步（每个 ticket 一个 fresh session）：
1. 打开新 session
2. /clear（不要沿用上一个 ticket 的上下文）
3. /implement <ticket 引用>
4. 完成后取下一条 frontier ticket
不要在同一 session 连续 implement 多个 tickets。
```

仅当用户**明确要求**在本 session 做第一个 ticket 时，才对 **一条** frontier ticket 跑 `/implement`；做完后仍建议 clear/新 session 再继续。

**不要**对 `/to-tickets` 产出的 tickets 再跑 `/triage`——它们已是 agent-ready。

---

### 阶段衔接速查

```text
Research（事实） 
  → Grill（design tree + frontier 轮次，stateful docs）
    → [可选] Prototype 绕行（handoff 来回；throwaway branch 作 primary source）
      → 规模判断
           ├─ A: Plan → 门禁 → Implement → Review → 提交门禁
           └─ B: to-spec → to-tickets →（新 session + /clear）implement × N
```

### 完成时

- **3A**：变更已 review，按用户选择提交或留下 diff；CONTEXT/ADR 已更新（若 Grill 写过）
- **3B**：spec + tickets 已发布；frontier 与 per-ticket `/implement` 指引已给出
- 任一阶段因 context 压力退出：已在边界做 Continue/clear/handoff/subagent/compact 决策，并写明下一 session 应从哪一阶段/哪个 artifact 恢复
