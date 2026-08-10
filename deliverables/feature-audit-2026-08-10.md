# 全量功能审计与取舍建议

> 审计日期：2026-08-10 17:58 ｜ 依据：当前 45 个源文件逐一核对（含 8/10 新增的 state/ 包重构：MiningContext 已移除、StateResetter 接管 resetAll、Controller 直接注入状态对象、MiningListener 接口已删除）
> 目的：功能取舍决策支持。每项给出【保留 / 精简 / 降级 / 移除】建议 + 原因 + 权衡。

---

## 一、总览：按价值分层

| 分层 | 功能数 | 建议 |
|------|--------|------|
| 核心挖掘链路 | 8 | 全部保留 |
| 安全与健壮性 | 7 | 全部保留 |
| 生命周期与交互 | 5 | 全部保留 |
| 选区与渲染 | 3 | 全部保留 |
| 消息与通知 | 2 | 全部保留 |
| 架构基建 | 3 | 全部保留 |
| 配置体系 | 21 项 | 保留（含 2 项低频） |
| **回滚检测体系** | **1 大项（6+ 类支撑）** | **降级默认关闭（推荐）或移除（可选）** |

**一句话结论：全项目只有"回滚检测"一个真正值得取舍的功能点，其余均为核心价值或低成本基建，建议全保留。**

---

## 二、核心挖掘链路（8 项）— ✅ 全部保留

| # | 功能 | 代码位置 | 保留原因 |
|---|------|---------|---------|
| 1 | 状态机调度 | `MiningController.tick()` | 模组本体，无替代方案 |
| 2 | 方块选择（空气跳过+可达性） | `BlockFinder` | 遍历入口，核心 |
| 3 | 蛇形遍历（双模式） | `AreaIterator` | README 声明特性（TOP_DOWN/BOTTOM_UP），核心 |
| 4 | A* 寻路 | `PathfindingHelper` | "类人行为"差异化卖点，vanilla 复用成本低 |
| 5 | 行走驱动（节点行走+贪心直走） | `MovementHelper` | 核心；贪心直走是 8/9 新增的性能优化（近距跳转 O(1)，避免 A* 短跳放大），**必须保留** |
| 6 | 视角转向（指数平滑+鼠标介入） | `CameraHelper` | "增量追踪式平滑视角"卖点 + 反作弊友好（鼠标物理位移检测）；帧级平滑依赖 START_MAIN 回调 |
| 7 | 挖掘执行（创造/生存） | `BreakingHelper` | 模组本体 |
| 8 | 推进协调 | `AdvanceCoordinator` | 多轮重构收敛的编排单点，行为已验证 |

## 三、安全与健壮性（7 项）— ✅ 全部保留

| # | 功能 | 代码位置 | 保留原因 |
|---|------|---------|---------|
| 9 | 危险检测（岩浆/虚空） | `DangerChecker` | 玩家生命安全保护，玩家死亡即会话终止的根因防护 |
| 10 | 卡住检测（累计位移判据） | `MovementHelper.updateStuckDetection` | 顶墙/地形卡死逃生，无它模组会原地空转 |
| 11 | 超时/重试体系 | `MovementHelper` 多级 | 行走/转向/挖掘/空气跳过各有超时，防死循环的骨架 |
| 12 | 工具耐久 | `ToolDurabilityGuard` | 生存模式体验（耐久见底自动停），README 特性 |
| 13 | 可达性判定 | `ReachChecker` | 与原版 4.5 格交互距离一致，防"穿墙挖" |
| 14 | 不可达断路器 | `WalkCycleBreaker` + `WalkRequester` | F1 死循环修复，3 次连续不可达即跳过 |
| 15 | 挖掘会话初始化+状态转移检测 | `BreakingState.beginBreakSession` + Controller lastState | C2 方案，消除跨域写入 |

## 四、生命周期与交互（5 项）— ✅ 全部保留

| # | 功能 | 代码位置 | 保留原因 |
|---|------|---------|---------|
| 16 | 玩家死亡停止 | `PlayerLifecycleManager` | 死亡后继续挖=无效+危险，必须停 |
| 17 | GUI 暂停 + 白名单 | 同上（isNonIntrusiveScreen） | 8/10 上午新增：背包/聊天框不暂停（模拟按键不受焦点影响），其余 GUI 暂停 |
| 18 | 断线停止 | `AreaAutoMinerClient` DISCONNECT | 离开服务器必须收尾 |
| 19 | K 键切换（可改键） | Fabric KeyBinding | 基本交互，可改键是标准能力 |
| 20 | 幂等 stopMining(reason) | `MiningController` | 避免"停止+原因"双消息，调用方幂等 |

## 五、选区与渲染（3 项）— ✅ 全部保留

| # | 功能 | 代码位置 | 保留原因 |
|---|------|---------|---------|
| 21 | 剑选点（pos1/pos2） | `SelectionTool` | 区域输入方式，无替代 |
| 22 | 区域绿色线框 + 目标红色高亮 | `RegionRenderer` | 低成本（vanilla drawOutline 复用），交互反馈价值高 |
| 23 | 选区射线距离配置 | `selectionMaxDistance` | 选点体验微调，保留 |

## 六、消息与通知（2 项）— ✅ 全部保留

| # | 功能 | 代码位置 | 保留原因 |
|---|------|---------|---------|
| 24 | 消息统一出口+跳过去重 | `NotificationService` + `BlockEventReporter.reportSkipped` | 8/10 上午统一出口改造完成；去重防连续跳块刷屏 |
| 25 | 文案集中管理 | `Messages` | i18n 前置 |

## 七、架构基建（3 项）— ✅ 全部保留

| # | 功能 | 代码位置 | 保留原因 |
|---|------|---------|---------|
| 26 | 手动 DI 组合根 | `MinerComponents` | 装配唯一入口，新增依赖一次接线 |
| 27 | 状态归零 | `StateResetter` | 替代已删的 MiningContext.resetAll，7 状态统一 reset |
| 28 | 会话收尾 | `SessionLifecycle.teardown` | 停止/完成共用出口，防逻辑漂移 |

## 八、配置体系（21 项）— ✅ 保留（2 项低频提示）

| 组 | 项 | 评估 |
|----|----|------|
| 时序 6 | moveWaitTicks / maxAirSkipPerTick / maxWalkTicks / maxStuckTicks / maxBreakTicks / maxFaceTicks | 超时类必须保留；**moveWaitTicks（默认 3）与 maxAirSkipPerTick（默认 5）为低频微调**，保留默认值即可，不建议删（删除影响老 JSON 兼容） |
| 距离 5 | selectionMaxDistance / maxReachSquared / arriveThreshold / maxVerticalDistance / pathFollowRange | 全部核心（触及距离/到达判定/寻路范围） |
| 重试 1 | maxWalkRetries | 核心 |
| 挖掘 4 | minerMod / minToolDurability / facingThresholdDegrees / reFacingThresholdDegrees | 全部核心（双模式/耐久/转向判定） |
| 回滚 4 | enableRollbackDetection / maxRollbackRetries / maxMinedPositions / rollbackCheckInterval | **随回滚体系一起决策（见下）** |
| 调试 1 | debug | 零成本保留 |

> 补充：`MiningConfigValidator` 边界钳制（防手动编辑 JSON 越界）与配置体系绑定，**保留**——它消除了"用户改坏配置导致死循环"的整类问题，收益大于其 40 行成本。

## 九、🔴 最大取舍点：回滚检测体系

**现状（约 6 个类 + 4 项配置 + 3 条消息支撑）：**
- `RollbackDetector`：每 20 tick（可配）遍历已挖集合，发现被重新填充的方块 → 返回命中
- `RollbackState.minedPositions`：每次挖掉方块记录位置（`Set<BlockPos>`，上限 50000，**常驻内存**）
- `MiningController.respondToRollback`：中断主流程 → 保存恢复点 → 游标跳回 → 重挖
- `AdvanceCoordinator` 恢复点逻辑：挖完回滚方块后跳回主遍历中断点
- `MiningCompletionService`：completeMining 时 `verifyAllBlocksMined` 全量遍历 + `resetToStart` 重扫
- 消息：ROLLBACK_DETECTED / ROLLBACK_MISS_RESCAN / MINING_COMPLETE_WITH_ROLLBACK

**价值评估：**
- README 声明"仅用于单人世界和私人多人服务器"——**单人世界不存在回滚源**（区块由本地保存）；唯一真实场景是私人服务器上的服务端回滚/反作弊机制
- 即便在私人服务器，回滚属低频事件

**成本评估：**
- 内存：挖 50000 方块后集合常驻（即使区域只挖几千方块，记录数=挖掉数）
- 每 tick 周期扫描 O(n)（20 tick 一次）
- 完成时全量校验 O(n) + 可能整区重扫
- **主流程中断-跳回-恢复逻辑是模组复杂度最高的一块**（resume 点在 Controller/AdvanceCoordinator 两处协作），也是 8/9 轮重构里最易出回归的部分
- 即使 `enableRollbackDetection=false`，`onBlockMined` 仍无条件执行 `rollback.addMinedPosition(...)` 与 `breaking.setLastMinedPos(...)`（`MiningCompletionService.onBlockMined` L97-99）——**记录开销无法通过配置关闭**（现有行为）

**建议（三选一）：**

- **方案 A（推荐）：降级为默认关闭 + 记录按开关跳过**
  - `enableRollbackDetection` 默认值 true → **false**
  - `MiningCompletionService.onBlockMined` 增加 `if (config.enableRollbackDetection)` 才记录（或独立开关）——关闭时零记录开销
  - 保留全部代码/配置/消息，用户可自行开启（私人服务器场景）
  - 权衡：代码复杂度不减少，但**默认运行路径的扫描与记录开销归零**，风险最小
- **方案 B：彻底移除**
  - 删除 RollbackDetector / minedPositions / verifyAllBlocksMined / resume 逻辑 / 4 配置 / 3 消息，`completeMining` 直接 teardown
  - 收益：移除模组最大复杂度块，Controller/AdvanceCoordinator/CompletionService 各减负，代码可读性显著提升
  - 代价：永久失去"防服务器回滚"能力（README 该特性章节需删除）
  - 前提：确认用户不在私人服务器使用或可接受无此能力
- **方案 C：保持现状**
  - 适合"私人服务器回滚是刚需"的用户

## 十、次要发现（非功能取舍）

| 项 | 说明 | 建议 |
|----|------|------|
| README_CN L124 引用已删除的 `context/MiningContext.java` | 文档与技术现状不一致（MiningContext 已重构为 state/ 包 + StateResetter） | 更新文档（低优先） |
| `MiningController` 构造参数 16 个 | LoD1 直接注入方案的结果，依赖面显式但较长 | 可接受；若追求精简可引入记录类参数对象（非必要） |
| `MiningController.respondToRollback` 有 `//TODO 人工review` 标记 | 用户遗留待审标记 | 建议在本次取舍确认后一并 review（若方案 B 则直接删除） |

## 十一、汇总决策表

| 功能 | 建议 | 一句话原因 |
|------|------|-----------|
| 核心挖掘链路（8） | ✅ 保留 | 模组本体 |
| 安全健壮性（7） | ✅ 保留 | 玩家保护+防死循环 |
| 生命周期/交互（5） | ✅ 保留 | 安全收尾+基本交互 |
| 选区/渲染（3） | ✅ 保留 | 输入方式+低成本反馈 |
| 消息/通知（2） | ✅ 保留 | 已收敛+去重 |
| 架构基建（3） | ✅ 保留 | 已重构到可接受状态 |
| 配置 21 项 | ✅ 保留 | 核心为主，低频项留默认即可 |
| **回滚检测** | **🔴 A 降级默认关闭 / B 移除 / C 现状** | **复杂度最高、价值最窄的功能** |
| MiningListener 接口 | 已删除（用户已处理） | — |
| 文档 MiningContext 引用 | 更新 | 一致性 |

---

## 十二、说明

- 本报告为只读审计，未改动任何代码。
- 功能取舍需用户决策：重点确认**回滚检测三选一（A/B/C）**；其余建议全保留。
- 若选 A（降级）：改动约 2 行（配置默认值 + onBlockMined 开关）；若选 B（移除）：改动面涉及 6+ 文件，需单独排期。

---

## 十三、方案 B 实施完成记录（2026-08-10 18:16，用户确认执行方案 B：彻底移除回滚检测体系）

**编译验证：`compileJava --offline` + `remapJar` 均 BUILD SUCCESSFUL。残留检查：全项目 `Rollback/rollback/ROLLBACK/回滚` 仅剩 javadoc 说明（无代码引用）。**

### 删除（2 个类文件）
- `service/RollbackDetector.java`（检测 + 计时）
- `state/RollbackState.java`（已挖集合/重试计数/恢复点）

### 修改（8 个源文件 + 2 lang + 2 README）
| 文件 | 变更 |
|------|------|
| `MiningController` | 移除 rollback/rollbackDetector 依赖（构造 16→14）与 respondToRollback 方法（含 `//TODO 人工review` 标记随删） |
| `AdvanceCoordinator` | 移除 RollbackState 依赖与恢复点跳回逻辑（依赖 4→3），advancePosition 回归纯推进 |
| `MiningCompletionService` | 移除回滚校验/重扫/verifyAllBlocksMined/resetToStart 调用；completeMining 直接 teardown+完成消息；onBlockSkipped 委托 BlockEventReporter（保留去重）；onBlockMined 删除（推进由 AdvanceCoordinator 直接处理）；依赖 9→3（client/config/rollback/session/breaking/areaIterator 全移除） |
| `StateResetter` | 7 状态 → 6（移除 rollback.reset） |
| `AreaIterator` | 删除回滚专属的 seek/resetToStart 方法（无消费者） |
| `BreakingState` | 删除无消费者的 lastMinedPos 字段 |
| `MiningConfig` | 删除 4 项回滚配置（17 项配置） |
| `MiningConfigValidator` | 删除对应校验 + javadoc 回滚条目 |
| `Messages` | 删除 3 条回滚消息（MINING_COMPLETE_WITH_ROLLBACK/ROLLBACK_MISS_RESCAN/ROLLBACK_DETECTED） |
| `zh_cn.json` / `en_us.json` | 删除回滚分类 + 4 项 key + 4 条 tooltip（共 9 条/文件） |
| `README.md` / `README_CN.md` | 删除回滚特性/配置章节；MiningContext 引用更新为 MinerComponents+StateResetter；state 描述 7→6 |

### 结果
- 状态对象：7 → **6**；配置文件：21 → **17** 项；源文件：45 → **43**
- 每次挖块的内存记录（上限 50000 条）、周期扫描、完成全量校验、主流程中断-恢复协作逻辑——全部消除
- 完成路径：`completeMining()` 从"回滚校验 → 可能整区重扫"简化为直接 teardown + 完成消息
