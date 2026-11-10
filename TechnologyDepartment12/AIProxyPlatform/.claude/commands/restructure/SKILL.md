---
name: restructure
description: "代码重构与架构升级编排：Research（含现有漏洞/风险基线）→ intent/规模分支（S0|S1|S2）→ 实现或交接。适合目标已清晰；重构前必须排查触达面内既有漏洞与行为洞。"
argument-hint: "<module / 痛点 / 重构或升级目标；可选 preserve|upgrade>"
---

# restructure

对齐 ask-matt **main flow** 的「结构债 / 架构升级」平行路径（sibling of `/develop`）：目标已经够清楚时，Research（**含漏洞/风险基线**）→ 钉死 **intent** → **自动规模判断** → S0 实现 / S1 单 ticket / S2 多 ticket。

**用语**：架构描述一律用 `/codebase-design` 词汇（**module / interface / depth / seam / adapter / leverage / locality**），不要用含糊的 component/service/boundary 替代。领域名词对齐 `CONTEXT.md`。

**硬规则 — 重构前先排洞：** 在爆炸半径内建立 **既有** 漏洞与风险基线，再改结构。禁止「结构变漂亮、洞原样留下且无人知情」，禁止搬家时**放大**已知洞。本 command **不是**全库渗透测试；范围=触达 module + 直接调用方/被依赖契约。

**不走本 command 时：**

| 情境 | 改用 |
|------|------|
| 要 deepen 哪、seam 放哪、是否 breaking 仍糊 | `/restructure-grill` |
| 不知从何下手，要扫 deepening 候选 | `/improve-codebase-architecture` → 选候选后再回本 command |
| 新功能交付（User-facing 行为新增） | `/develop` / `/develop-grill` |
| 纯访谈、不改代码 | `/grill-with-docs` / `/grill-me` |
| 设计手感要用 throwaway 验证 | `/prototype-design` / `/prototype-grill`（upgrade 途中也可选绕行，见规模分支前） |
| 已有 agent-ready ticket | 直接 `/implement` |
| 难复现 bug / regression（非结构债主线） | `/diagnosing-bugs`；若 bug 在触达面内且阻塞重构，先诊断再回本 command |
| 阻塞点在别人脑子里 | `/to-questionnaire` |
| 刚说的话没听懂 | `/wait-what` |
| 只有人能点的基建/密钥 | 实现途中 `/wizard` |

Research 达标且 **漏洞基线表已填处置** 后，**必须**走 **intent 门禁**再进规模分支；其余阶段过渡同样走门禁。禁止静默跨阶段、禁止软确认（"ok"/"good"）跳过门禁。

## Target: $ARGUMENTS

若 `$ARGUMENTS` 为空，先确认：要重构/升级的 **module 或痛点**（路径/症状均可）。若参数已含 `preserve` / `upgrade`，记为 intent 初值，仍须门禁确认。

### Intent（贯穿全程）

| Intent | 含义 | 验收底线 |
|--------|------|----------|
| **preserve** | 对外行为与契约不变；提 depth、抽 seam、降耦合、rename/拆包、可导航性 | 旧测 + 必要 characterization 全绿；**禁止**顺手改产品行为；可附 locality/leverage 验收 |
| **upgrade** | 允许行为/契约变化（换分层、迁移基础设施、改 API/模型） | **必须**列出 breaking surface + 迁移/兼容策略；无契约 diff 不得进 Implement |

- **默认倾向 preserve**。用户明确说升级/迁移/换框架，或 Research 推断为 upgrade 时，在门禁里钉死。
- 实现中发现其实在改行为 → **停**，回 intent/规模门禁；禁止静默扩大为 upgrade。

### 门禁（通用）

展示当前阶段结论后，**立刻**用 AskUserQuestion（一次一问）。禁止只写「停止/等待批准」或静默继续。

| 过渡 | 问题 | 继续 | 补充 |
|------|------|------|------|
| Research → 规模 | intent、目标与漏洞处置是否确认？ | `确认 intent=preserve (Recommended)` 或展示实际推断项 | `改为 upgrade` / `先处理 fix-first 漏洞` / `目标仍糊 → 改 /restructure-grill` |
| Plan → Implement（仅 S0） | 是否批准实现？ | `批准并继续 (Recommended)` | `需要补充` / `改走 S1` / `改走 S2` |
| Plan → Implement（多切片时） | 如何编排实现顺序？ | 见 **实现编排门禁** | — |
| Review → Commit（仅 S0） | 是否提交变更？ | `提交 (Recommended)` | `暂不提交` |

- 只有选「继续」类选项才可进入下一阶段
- 「需要补充」→ 修订后重新提问
- 阶段门禁「一次一问」；不要与其它多问访谈格式混用

#### 实现编排门禁（仅 S0 且计划含多个可独立验证切片时）

切片**有依赖** → 不触发，按依赖串行。切片**相互独立** → 触发 AskUserQuestion，选项同 `/develop`（串行 / 基础串行+其余并行 / 全并行 / 需要补充）。选定后写入 Plan Approach 顶部「实现顺序」一行。接近 smart zone 时强制串行。并行 subagent 产出回主 session 合并 typecheck + 相关测试后再 Review。

### Context hygiene

对齐 ask-matt / PHASE-BOUNDARIES：

1. **Research →（S0）Plan → Implement**：尽量 **Continue** 同一 window；下一阶段需要本阶段作 primary source 时不要中途 compact
2. **smart zone ~150k**：阶段边界按序 Continue → `/clear` → `/handoff`（仅 portability）→ subagent → **`/compact`**
3. **S1/S2**：每个 ticket 的 `/implement` 从 **fresh session** 开始，tickets 之间 **`/clear`**
4. `/handoff` 窄用：只买 portability

### 强制 / 按需 skill

| 何时 | Skill |
|------|--------|
| 描述架构与加深方案 | `/codebase-design` 词汇与原则（deletion test、interface = test surface） |
| seam/interface 有争议、要双方案 | **按需** design-it-twice（`/codebase-design` 内并行子代理模式） |
| S0 实现 | 优先 `/tdd`；收尾 `/code-review`（Standards + Spec + 对照漏洞基线） |
| S1/S2 实现 | 新 session `/implement`（本 command 发布 ticket 后默认不自动开做） |
| 触达面内难复现缺陷阻塞重构 | 先 `/diagnosing-bugs`，结论写回基线处置 |
| 域名词变化 | 可 `/domain-modeling` 更新 `CONTEXT.md`；硬权衡才 ADR |

---

### 漏洞与风险基线（全程强制）

重构 = 搬动既有代码。**先发现触达面里已有的洞与行为风险，再决定锁住、先修、顺带修、或显式延期。**

#### 排查范围

- **In scope**：目标 module、其 interface 两侧最近调用方/适配器、本改动会重写的校验/序列化/鉴权路径、相关测试与配置
- **Out of scope**：全仓库无差别安全审计、与爆炸半径无关的历史 CVE 海洋（可记一笔「未扫」但不假装已覆盖）

#### 排查清单（Research 必做，可派 subagent）

按触达面逐项过；无命中写「未发现」+ 扫过的路径，禁止整表省略：

1. **鉴权 / 授权**：校验缺失、前后端不一致、越权（IDOR）、错误的「仅前端拦」
2. **注入与危险执行**：SQL/HQL/命令/模板拼接、不安全反序列化、动态 exec
3. **密钥与隐私**：硬编码凭证、token 进日志/响应、敏感字段未脱敏
4. **Web/API 经典面**（若相关）：SSRF、路径穿越、开放重定向、过宽 CORS、CSRF 缺口、调试后门
5. **校验关闭与不安全默认**：跳过校验的 flag、`permitAll` 过宽、HTTP 明文敏感通道
6. **已知缺陷信号**：`FIXME`/`SECURITY`/`HACK`/`CVE` 注释、失败或 `@Disabled` 安全相关测试、tracker 上关联 issue
7. **并发与状态洞**：竞态、非幂等写、TOCTOU（preserve 时必须能锁定或显式 accept）
8. **依赖与生成物**（轻量）：触达路径上明显过时/危险 API 用法；不强制跑完整 SCA，有工具则用、无则人工点名

安全敏感域（登录、权限、支付、上传、原生查询等）→ 清单从「抽查」升级为 **逐文件过鉴权与输入边界**。

#### 基线表（intent 门禁前必须展示）

```text
HAZARD BASELINE
| ID | 位置(路径/符号) | 类型 | 严重度(C/H/M/L) | 处置 | 说明 |
| H1 | ... | authz | H | fix-first | ... |
```

**处置枚举：**

| 处置 | 含义 | 何时用 |
|------|------|--------|
| **fix-first** | 实现结构改动**之前**必须修（或单独先开修复 ticket 并 block 本重构） | C/H 默认可选；不修则不得进 S0 Implement / 不得把结构票标 ready |
| **lock** | 用测试/characterization/断言钉住现状；重构 **不得恶化** | preserve 下暂不修、但行为必须保持的洞或怪癖 |
| **fix-in-scope** | 纳入本次 Plan/Acceptance，与结构改动一起交付 | 修洞与 deepen 同一 seam、边际成本低 |
| **defer** | 明确 **Out of scope**，留下 issue/ticket 指针与理由 | 仅 M/L 或用户书面接受残留；**C/H 禁止静默 defer** |

**硬规则：**

- 存在未处置（表空处置列）的条目 → **不得**进规模执行
- **C/H + 未定** → 门禁必须出现 `先处理 fix-first 漏洞`；用户确认 defer 时写入 Out of scope 与残留风险，并建议独立修复票
- **preserve** 不得借重构引入**新**洞；**upgrade** 新暴露面必须进 Breaking surface 与 Test strategy
- `lock` 项：Test strategy / ticket Acceptance 必须有对应验证；Review 对照基线做 **未恶化** 检查
- 实现中新发现的洞 → 追加基线行并重走处置，禁止默默忽略

---

### Phase 1: Research

探索 codebase，摸清**爆炸半径、漏洞基线、行为锁定、迁移路径**后再打分。目标是可执行的重构/升级范围感，不是写论文。

**探索清单：**

1. **目标 module**：当前 interface / 实现边界、shallow 点、调用方
2. **爆炸半径**：跨包引用、共享类型/schema、测试与 fixtures、生成代码
3. **漏洞与风险基线**（见上节）：触达面清单 + 基线表 + 每条处置
4. **行为锁定**：现有测试能否钉住行为与 `lock` 项；缺口是否要先补 characterization
5. **迁移线索**：能否 expand–contract；是否 **wide refactor**（单点机械改、半径跨多包、无法一条 vertical 保持绿）
6. **约束**：ADR、跨端约定、权限/错误约定；upgrade 时的 breaking surface 候选
7. **上游发现**：用户未点名方向且热点不明 → 建议先 `/improve-codebase-architecture`；已点名则跳过扫描

**输出（intent 门禁前必须展示）：**

- 相关文件/module 列表（尽量精确到路径）
- 推断 **intent**（preserve|upgrade）+ 一句话理由
- 调用链 / 爆炸半径摘要
- **HAZARD BASELINE 表**（可无高危命中，但必须有扫描结论）
- 行为锁定手段（测例路径或待补 characterization；映射到 `lock`/`fix-*` 项）
- upgrade 时：breaking surface 草案（可空但须标明「待补」）
- 是否 **wide** + 初步迁移策略（直接替换 / expand–contract）
- **重构五维**评分表
- **规模判断** S0 | S1 | S2（见下）

#### 评分体系（重构五维）

维度（每项 0–20，总分 0–100）：

| 维度 | 问题 | 高分标志 | 低分标志 |
|------|------|----------|----------|
| 目标清晰度 | 改哪个 module/seam？intent？ | 路径 + intent 可钉 | 只知道「代码很乱」 |
| 依赖 / 爆炸半径 | 谁调用、跨包影响？ | 调用链与受影响面清楚 | 不确定谁会炸 |
| 行为与风险锁定 | 如何证明行为不坏、已知洞不恶化？ | 基线表齐全 + 测试/characterization/契约对应处置 | 未排洞或只能「跑一下看看」 |
| 迁移策略 | preserve 的替换步骤或 upgrade 的 expand–contract？含 fix-first 是否前置？ | 步骤可写进 Plan/ticket | 只有终态幻想 |
| 验证策略 | typecheck/单测/集成/安全相关回归？ | 可执行命令与范围，含基线 `lock`/`fix-in-scope` 项 | 验证方式模糊 |

**打分方法：**

1. 逐维给分 + 一句话理由（引用路径/符号）
2. 汇总总分与通过/不通过
3. 不通过：点名最低维、差距、下一步（补探索 / 补排洞 / 补测 / 切 grill），非笼统「再研究」
4. **「行为与风险锁定」< 10 或基线表缺失** → 视为未通过，先补排洞，不进门禁

**Research 决策：**

- 总分 >= 70 **且** 基线表每条已有处置 **且** 无未解决的 C/H `fix-first` 阻塞（或用户已确认残留）→ 展示输出 → **intent 门禁** → 规模分支执行
- 总分 < 70：补最低维后重评；或说明无法提升的维度并询问是否带着差距继续
- 有 C/H `fix-first`：先修或先开阻塞票；不把「纯结构 S0」与未修高危绑在同一把梭里
- 未决主要是 **seam/深度/产品契约/漏洞处置取舍** 主观决策 → 建议 `/restructure-grill`
- 缺别人的知识 → `/to-questionnaire`
- 缺 deepening 候选且用户愿意扫全库 → `/improve-codebase-architecture`

#### 规模分支（intent 确认后执行）

| 级别 | 条件（经验） | 动作 |
|------|--------------|------|
| **S0 单 session** | 文件列表可控；一条可验证重构切片装进当前 window；**非**默认 wide | Phase 2 Plan → Implement → Review |
| **S1 单 ticket** | 装不进当前 window，但可收成 **一张** agent-ready ticket（一条 deepen / 一个 migrate batch / 极少见的整条短 expand–contract） | Phase 3B：直接发布 1 ticket；**本 command 结束**并给 implement 指引 |
| **S2 多 ticket** | 多条独立切片；或 **wide**（默认）；或 migrate 批次 >1 / contract 需独立验收 | Phase 3C：`/to-spec` → `/to-tickets`；发布后结束并给 frontier 指引 |
| **需先打磨** | 目标/seam/intent 仍高度主观 | 建议 `/restructure-grill`（展示原因，用户确认后切换） |

**Wide 规则：**

- 判定为 wide → **默认 S2**（expand → migrate×N → contract，blocking edges 交给 `/to-tickets`）
- **例外**：整条 expand–contract 能在**一个** session 验证完毕 → 允许 S0 或 S1（ticket/Plan 必须写清三阶段验收）
- 禁止在 S0「一把梭」万点 rename 而不保绿

拿不准时 **偏保守升档**（S0→S1→S2）。S0 的 Plan 门禁保留「改走 S1/S2」出口。

**可选 Prototype 绕行（少见）：** 仅当 intent=upgrade 且新分层/状态手感无法在对话与测试计划里说清时，可 `/handoff` → `/prototype-design` 或 `/prototype-grill` → 回收 verdict 后再做规模判断。纸面与 expand–contract 能说清的 **不要** prototype。

---

### Phase 2: Plan（仅 S0）

**只描述要做什么，不写实现代码。** 路径尽量精确；步骤可独立验证。

**起草要求：**

1. 写明 **Intent** 与（upgrade 时）**Breaking surface** + 迁移策略
2. **Hazard baseline 摘要**：fix-first（须已完成或作为 Approach 第 0 步）、lock、fix-in-scope、defer（defer 进 Out of scope）
3. Goal / Out of scope；Out of scope 明确排除「顺手新功能」（那是 develop）及已 defer 的洞
4. 文件列表来自 Research；改 / 新建 / 删除分开
5. Approach 按依赖排序；优先 **小步可绿**（**fix-first → 行为/lock 测试 → expand → 搬 → contract → fix-in-scope 收尾验证**）
6. Risks + Test strategy（preserve：行为与 lock 项；upgrade：兼容期与回归；**显式「基线未恶化」检查**）
7. 写着发现装不进单 session 或其实是 wide → 改 S1/S2，不硬塞
8. 仅人能完成的步骤标 `/wizard`

```text
PLAN: [Restructure Name]

Intent: preserve | upgrade
Breaking surface: [upgrade 必填；preserve 写 N/A]
Migration: [直接替换 | expand–contract 步骤]

Hazard baseline:
- fix-first: [H# … 已完成 | 本 Plan 第 0 步]
- lock: [H# … → 对应测试]
- fix-in-scope: [H# …]
- defer: [H# … → issue 指针]

Goal: [一句话]
Out of scope: [明确不做的；含 defer 漏洞]

Files to modify:
1. path/file.ts - [改什么 / 为什么]

New files:
1. path/new.ts - [用途]

Approach:
0. [若有 fix-first：先修并验证]
1. [行为锁定 / lock 项测试]
2. [expand → 搬 → contract / deepen]
3. [fix-in-scope 与基线未恶化验证]

Risks:
- [问题] → [缓解]

Test strategy:
- [命令与范围；characterization；基线 lock/fix-in-scope 断言]
```

展示后走 **Plan → Implement** 门禁（及必要时编排门禁）。

---

### Phase 3A: Implement（仅 S0）

按批准的实现顺序执行。

- **fix-first 未完成不得改结构**；preserve 时先补/跑 lock 与行为锁定测试再改结构
- 认可的 seams 上优先 `/tdd`
- 定期 typecheck / 单测；收尾跑相关套件 + **基线未恶化**核对
- 并行编排：各 subagent 自测 → 主 session 合并再全量相关测试
- **不要**自动 commit（提交权在 Review 门禁）
- 收尾可跑 `/code-review`；发现并入 Phase 4
- 撞人墙 → `/wizard`
- 发现计划错误、intent 漂移、**新漏洞或基线项恶化** → 停，修订 Plan / 追加基线 / 回门禁，不默默扩 scope

---

### Phase 3B: S1 单 ticket

不强制完整 `/to-spec`。直接起草 **一张** ready-for-agent ticket 并发布：

- **Local tracker**：`.scratch/<slug>/issues/01-<slug>.md`
- **真实 tracker**：一条 issue + `ready-for-agent`（若项目使用 triage 标签）

Ticket 必须含：Intent、Goal、Out of scope、Blocked by（fix-first 若拆成前序票则写依赖）、Acceptance（preserve/upgrade + **基线处置：lock 不恶化 / fix-in-scope 已修**）、验证命令、Hazard 摘要、（upgrade）Breaking surface 摘要。避免易过期的大段文件路径堆砌；关键 module 名可用领域词。

若 fix-first 需独立交付：先发 **阻塞票**（或 Blocked by 指向已有修复票），结构票不得标可开工直到阻塞解除。

发布后展示：

```text
下一步（fresh session）：
1. 打开新 session
2. /clear
3. /implement <ticket 引用>
```

仅当用户**明确要求**在本 session 做这张票时，才对这一张跑 `/implement`；默认本 command 在此结束。

写 ticket 时发现必须拆多张或 wide 多 batch → 升级 **S2**。

---

### Phase 3C: S2 多 ticket

仍在同一 window（未超 smart zone）时：

1. **`/to-spec`**：综合 Research（+ 任何 prototype verdict），**不再访谈**；测试 seams、intent/契约、**Hazard baseline 与处置**写清后发布
2. **`/to-tickets`**：tracer-bullet 或 **wide 的 expand–contract 链**；**fix-first 票 block 结构票**；声明 blocking edges；quiz 粒度与依赖后发布
3. **本 command 收尾**：列出 frontier，并给出每 ticket 新 session + `/clear` + `/implement` 指引

不要对已发布的 agent-ready tickets 再跑 `/triage`。不要在同一 session 连续 implement 多张票。

---

### Phase 4: Review & Commit（仅 S0）

1. `/code-review`（若未跑）或汇总：Standards + Spec；对照 Plan 的 Intent、Test strategy 与 **Hazard baseline**
2. **基线复核**：逐条确认 fix-first/fix-in-scope 已关闭、lock 未恶化、defer 仍在 Out of scope 且有指针；新引入洞 = 阻塞提交
3. 展示摘要：结构变化、测试结果、基线结果、残留风险、（upgrade）兼容期债务
4. 提交门禁；**不自动提交**

---

### 阶段衔接速查

```text
Research（事实 + 漏洞基线 + 重构五维 + intent 推断 + wide?）
  → intent 门禁（含漏洞处置确认）
    → 规模
         ├─ S0: Plan(含 Hazard) → 门禁 → Implement → Review(基线复核) → 提交门禁
         ├─ S1: 1 ticket（含基线 Acceptance）→ implement 指引（结束）
         ├─ S2: to-spec → to-tickets（fix-first block 结构）→ frontier 指引（结束）
         └─ 糊: 建议 /restructure-grill
```

### 完成时

- **S0**：已 review；基线复核通过；按用户选择提交或留 diff；intent 与验收对齐
- **S1**：1 ticket 已发布（含 Hazard Acceptance）；implement 指引已给出
- **S2**：spec + tickets 已发布（含 fix-first 依赖）；frontier 与 per-ticket implement 指引已给出
- 任一阶段因 context 压力退出：在边界做 Continue/clear/handoff/subagent/compact，并写明下一 session 恢复点（含基线表指针）
