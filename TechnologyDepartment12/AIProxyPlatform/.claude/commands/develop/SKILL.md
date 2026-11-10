---
name: develop
description: "单 session 功能构建：Research → Plan → Implement → Review。适合范围清晰、无需深度访谈的工作。"
argument-hint: "<feature description>"
---

# develop

对齐 ask-matt **main flow** 的「单 session 直建」捷径：想法已经够清楚，直接 Research → Plan → Implement → Review。

**不走本 command 时：**

| 情境 | 改用 |
|------|------|
| 想法模糊，需要访谈打磨 | `/develop-grill` |
| 还不确定方案，要先用 throwaway 原型验证 | `/prototype-design`（问题清晰）或 `/prototype-grill`（先访谈） |
| 一个 session 装不下的巨大/模糊 effort | `/wayfinder`，再汇入 main flow |
| 已有 agent-ready ticket | 直接 `/implement` |
| 难复现 bug / regression | `/diagnosing-bugs` |
| 阻塞点在别人脑子里，不在 codebase | `/to-questionnaire`，回收后再接本 command 或 `/develop-grill` |
| 刚说的话没听懂，需要重讲 | `/wait-what`（可叠在任何阶段） |
| 只有人能点的基建/密钥/第三方控制台 | 实现途中触发 `/wizard`，不要在 Plan 里假装 agent 能代劳 |

Research → Plan **无门禁**（评分达标或用户决定带着差距继续后直接进入）；其余阶段过渡必须走门禁，禁止自动进入下一阶段。

## Feature: $ARGUMENTS

若 `$ARGUMENTS` 为空，先确认要构建的功能描述。

### 门禁（通用）

展示当前阶段结论后，**立刻**用 AskUserQuestion（一次一问）。禁止只写「停止/等待批准」或静默继续。

| 过渡 | 问题 | 继续 | 补充 |
|------|------|------|------|
| Plan → Implement | 是否批准实现？ | `批准并继续 (Recommended)` | `需要补充` / `改走 multi-session` |
| Plan → Implement（多切片时） | 如何编排实现顺序？ | 见下方 **实现编排门禁** | — |
| Review → Commit | 是否提交变更？ | `提交 (Recommended)` | `暂不提交` |

- 只有选「继续」类选项才可进入下一阶段
- 「需要补充」→ 修订后重新提问；「改走 multi-session」→ 转入下方 **规模分支 B**
- 禁止凭 "ok" / "good" 等软确认跳过门禁

#### 实现编排门禁（仅当计划含多个可独立验证的切片时触发）

当 Plan 的 Approach 里存在**多个互不阻塞、可分别 demo 的切片**（典型场景：一处共享基础 + 若干独立上层改动，或多个并列模块），在「批准实现」之后、进入 Phase 3 之前，**立刻**用 AskUserQuestion 追问一次编排方式。一次一问，给出 Plan 中实际的切片名而不是泛指。

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

- 选定编排后**写入 Plan 的 Approach 顶部**作为「实现顺序」一行，Phase 3 据此执行
- 并行选项仅在不违反 Context hygiene（见下）的前提下可用；接近 smart zone 时强制降级为串行或在阶段边界做 phase-boundary 决策
- 并行 subagent 的产出必须回主 session 合并 typecheck + 相关测试，再进 Phase 4

### Context hygiene

对齐 ask-matt / `PHASE-BOUNDARIES.md`（`/ask-matt` 技能包内）：

1. **Research → Plan →（单 session）Implement**：尽量 **Continue** 在同一未中断 context window；下一阶段需要本阶段作 **primary source** 时，优先继续，不要中途 compact
2. **smart zone** 约 **150k tokens**。接近上限时在**阶段边界**决策，按序问：
   - 还能 Continue 且下一阶段装得下？→ 继续
   - 本窗口内容对下一步完全无关？→ `/clear`
   - 需要 **portability**（换 harness / 换目录 / 交给同事 / 中途分叉旁路）？→ `/handoff`
   - 任务可 AFK、可收紧 scope？→ **subagent**
   - 否则 → **`/compact`**（默认落地，带指令说明下一阶段要什么）
3. multi-session 路径：每个 ticket 的 `/implement` 从 **fresh session** 开始，tickets 之间 **`/clear`**；不要把上一个 ticket 的上下文拖进下一个
4. `/handoff` **窄用**：只买 portability。同 harness、同目录、还要人在回路时，优先 Continue / compact，不要默认 handoff

---

### Phase 1: Research

探索 codebase，摸清范围后再打分。目标是**可执行的范围感**，不是写论文。

**探索清单：**

1. **相关代码**：入口、路由/API、数据模型、UI 页面、测试与 fixtures
2. **已有模式**：同域 CRUD / 鉴权 / 列表筛选等可复用实现；记录可对照路径
3. **依赖与约束**：调用链、跨端/跨层约定、配置与 schema、权限与错误约定
4. **边界与验证**：空值/并发/权限/失败路径；现有测试或手动验收方式

**输出（进入下一阶段前必须展示）：**

- 相关文件/模块列表（尽量精确到路径）
- 可复用模式与关键约束
- 已知边界与未决假设（写入 Plan 的 Risks / 待确认点）
- 五维评分表
- **规模判断**（见下）

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
3. 不通过时：点名最低维、差距、下一步行动（如「需要探索 X 的 API 边界」），而非笼统「需要更多研究」

**Research 决策：**

- 总分 >= 70：展示输出后进入 **规模分支**
- 总分 < 70：补最低维后重评；循环直到 >= 70，或说明无法提升的维度并询问是否带着差距继续
- 若未决点主要是**产品/设计决策**（而非 codebase 事实）：建议切换 `/develop-grill`，不要在 Plan 里用猜测填坑
- 若缺的是**别人的知识**（非本机/非本 repo）：先 `/to-questionnaire`，不要空猜

#### 规模分支（Research 后立刻判断）

用一句话给出判断，并据此分流：

| 判断 | 条件（经验） | 动作 |
|------|--------------|------|
| **A. 单 session** | 文件列表可控、一条 tracer bullet 能 demo、预估能在当前 window 内完成 | 进入 Phase 2 Plan |
| **B. multi-session** | 多条独立可 demo 的 vertical slices、或 Approach 明显超出单 window | 综合 Research 结论跑 `/to-spec` → `/to-tickets`，**本 command 在 tickets 发布后结束**，并给出「按 blockers-first，每 ticket 新 session + `/clear` 后跑 `/implement`」的交接说明 |
| **C. 需先打磨** | 目标/边界/验收仍高度主观 | 建议 `/develop-grill`（展示原因，用户确认后切换） |

不确定时默认 **A**，在 Plan 门禁保留「改走 multi-session」出口。

---

### Phase 2: Plan

仅 **规模分支 A** 进入本阶段。基于 Research 起草计划。**只描述要做什么，不写实现代码。** 计划须可执行、可审查：路径尽量精确，步骤可独立验证。

**起草要求：**

1. Goal 对齐功能描述与 Research 结论；Out of scope 写清不做的部分
2. 文件列表来自 Research；改/新建/可能删除分开写
3. Approach 按依赖排序（数据/API → 业务 → UI → 测试），优先 **tracer bullet**（窄而贯通的端到端路径），每步说明理由
4. Risks 写潜在问题与缓解；Test strategy 写手动/自动如何验收
5. Research 中的未决假设写入 Risks 或明确默认选择（并在计划中标出）
6. 若写着写着发现工作其实装不进单 session：停止硬塞，改走规模分支 B
7. 若步骤里出现「只有人能完成」的项（密钥、云控制台、一次性 cutover）→ 标出，实现时用 `/wizard`，不要写进 agent 可自动完成的步骤

展示完整计划后走门禁（Plan → Implement）。

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

---

### Phase 3: Implement

按门禁选定的**实现顺序**执行已批准计划（串行 / 并行 subagent / 全并行）；若门禁未触发编排问题，按 Approach 中依赖顺序串行执行。

- 在认可的 seams 上优先 `/tdd`（red-green 小步）
- 定期 typecheck / 单测；收尾跑相关测试套件
- **并行编排时**：每个 subagent 独立完成自己切片的 typecheck/单测；主 session 合并后再次跑全量 typecheck + 相关测试套件，确保切片间无隐性冲突
- **本 command 覆盖提交权**：实现阶段**不要**自动 commit（裸 `/implement` skill 会 commit；本编排故意延后）；把提交留给 Phase 4 门禁
- `/implement` 内的 `/code-review`（Standards + Spec 双轴）可在实现收尾时跑；其发现在 Phase 4 一并展示
- 撞到只有人能过的墙 → `/wizard`，不要卡住空转

若实现中发现计划错误：停下来修订 Plan（或回 Research），不要默默扩大范围。若发现切片其实有依赖（原以为可并行）：暂停并行，回门禁重选串行。

---

### Phase 4: Review & Commit

1. 用 `/code-review`（若实现阶段未跑）或汇总已有 review：固定点以来的 diff 做 **Standards + Spec** 双轴；有精确行号才报；`grep` 查 console.log / TODO / 密钥，只报实际命中；不报未验证猜测
2. 展示摘要：做了什么、测试结果、残留风险
3. 走提交门禁；**不自动提交**。用户选提交后再 commit

---

### 完成时

- **分支 A**：变更已 review，按用户选择提交或留下 diff
- **分支 B**：spec + tickets 已发布；给出 frontier ticket 列表与「新 session + `/clear` + `/implement <ticket>`」指引
- **分支 C**：已说明为何改走 `/develop-grill`，并等待用户切换
