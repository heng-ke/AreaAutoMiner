# 去除 MiningContext 中间层：可行性分析与方案

**日期**：2026-08-10
**场景**：日常开发 - 架构重构方案（只读，未改代码）
**目标**：去掉 `MiningContext` 中间层，让各功能组件直接持有其真正需要的 state 对象

---

## 1. 现状分析：MiningContext 的真实依赖面

`MiningContext` 当前职责：持有 `client` + 7 个 state（Session/Region/Traversal/Facing/Movement/Breaking/Rollback）+ 8 个 accessor + `resetAll()`。

**全项目 grep 结果——直接依赖 MiningContext 的类只有 2 个：**

| 使用方 | 用途 | 明细 |
|--------|------|------|
| `controller/MiningController.java` | 运行时唯一消费者 | 构造参数 + 28 处 `context.xxx()` 调用 + `resetAll()`（startMining L68） |
| `di/MinerComponents.java` | 装配期唯一消费者 | `new MiningContext(client)`、8 处 `context.xxx()` 取值、`context()` accessor（L85，无外部调用） |

**关键事实**：
- `MiningController` 实际只用 5 个 state：`session/region/traversal/breaking/rollback`（**不用** movement/facing——这两个被 Helper 直接持有）。
- 其余 11 个类（MovementHelper/BlockFinder/BreakingHelper/CameraHelper/AreaIterator/AdvanceCoordinator/MiningCompletionService/RollbackDetector/SessionLifecycle/WalkRequester/SpatialMath）**已经直接依赖具体 state 对象**（构造参数即 state），与 MiningContext 无关。
- `resetAll()` 唯一调用点是 `startMining`。

**结论：可行。** MiningContext 本质是"装配期的 7 个 state 制造器 + 运行期 Controller 的访问代理"，中间层厚度很薄，去掉后依赖会显著更显式。

---

## 2. 方案对比

### 方案 A（推荐）：彻底去中间层 + StateResetter 归零 + state 包上移

**核心思路**：MinerComponents 直接 new 7 个 state 并注入各组件；MiningController 构造参数改为"它真正需要的" client + 5 个 state + StateResetter；新增 `StateResetter` 承担统一归零。

**改动清单**：

| 文件 | 改动 |
|------|------|
| `context/MiningContext.java` | 删除 |
| `context/state/*.java`（7 个） | 整体上移为顶层包 `state/`（`xyz.hengke.areaautominer.state`），package 声明同步 |
| `di/MinerComponents.java` | 删除 context 字段与 `context()` accessor；构造内 new 7 个 state 局部变量，逐个注入各组件 |
| `controller/MiningController.java` | 构造参数：`MiningContext context` → `MinecraftClient client + SessionState session + RegionState region + TraversalState traversal + BreakingState breaking + RollbackState rollback + StateResetter resetter`（共 16 参）；28 处 `context.xxx()` 改为直接字段 |
| 新增 `state/StateResetter.java` | 单一职责：持有 7 个 state，`reset()` 逐个调用（统一归零入口，防漏） |
| 11 个 state 引用文件 | import 路径 `context.state.X` → `state.X`（机械替换） |

**优点**：
- 依赖显式：Controller 构造签名直接暴露 5 个 state + client，不再有"上帝对象"
- 归零职责单一化：StateResetter 只管 reset 编排，Controller 不膨胀、不持有不用的 movement/facing
- 包结构自洽：`state/` 成为独立顶层包，"context" 命名不再残留

**缺点**：
- Controller 构造参数 10 → 16（长但全部显式，MinerComponents 一处装配可接受）
- 新增 1 个类（StateResetter，约 20 行）
- 11 个文件的 import 路径变更（机械、量大但零风险）

### 方案 B（保守）：去中间层但不新增类、state 包不上移

**核心思路**：与 A 相同去掉 MiningContext，但 `resetAll()` 由 Controller 自己实现（Controller 依赖全部 7 个 state），`context/state/` 包路径保持不变（import 零改动）。

**改动清单**：
- 删 `MiningContext.java`；MinerComponents 直接 new 7 个 state 注入；Controller 构造改为 client + **7 个 state** + 原组件；Controller 内部私有 `resetAll()` 逐个调用。
- state 包路径不动。

**优点**：改动面最小（无新类、无 import 批量变更）。
**缺点**：
- Controller 持有它运行时不用的 `movement/facing`（仅为归零）——与"真实找所需 state"的目标相悖
- `context/state/` 包名残留"context"（空壳父包）
- 归零逻辑散在 Controller，将来加新 state 容易漏

### 方案 C（不建议）：保留一个更薄的容器

把 MiningContext 改名为 `StateStore`/`StateRegistry` 继续做聚合访问——换汤不换药，违背"去掉中间层"意图。仅列出供对照。

---

## 3. 权衡与建议

| 维度 | 方案 A | 方案 B |
|------|--------|--------|
| 依赖显式化 | ✅ 彻底（Controller 只用 5 个 state） | ⚠️ 部分（Controller 被迫持 7 个） |
| 归零职责 | ✅ StateResetter 单一化 | ⚠️ 散在 Controller |
| 包结构自洽 | ✅ state 顶层包 | ❌ context 空壳残留 |
| 改动面 | 中（11 处 import 机械替换 + 新增 1 类） | 小 |
| 行为风险 | 零（纯重构，编译可验证） | 零 |
| 后续扩展性 | 好（加 state 时 StateResetter 一处同步） | 一般 |

**建议：方案 A。** 用户目标正是"对应功能真实找所需 state"，A 才完整达成（Controller 不持有它不需要的 movement/facing）；B 会留下"为归零而持有无用依赖"的妥协。改动虽涉及 11 处 import，但全部是 `context.state.` → `state.` 的机械替换，且可编译验证，风险可控。

**一个可选的微调**：若觉得 Controller 16 参过长，可把"client + 5 个 state + resetter"再聚合为一个 Controller 专用参数对象——但那就又回到某种 context，不推荐；16 参在组合根（MinerComponents）只有一处调用，显式优于聚合。

---

## 4. 验证方式

- `gradlew.bat compileJava --offline --rerun-tasks` → BUILD SUCCESSFUL（行为零变化，纯重构）
- grep 确认无 `MiningContext` / `context.` 残留（注意 `RegionRenderer` 里的 `context.consumers()` 是 WorldRenderContext 参数，与 MiningContext 无关，勿误删）
- 若做方案 A，README/README_CN 的目录树同步更新（context/ 行删除或改为 state/ 行）

---

> 本方案为只读产出，未改动代码。确认采用 A 或 B 后实施。
