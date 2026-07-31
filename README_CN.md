# AreaAutoMiner

一个客户端侧的 Minecraft Fabric 模组，可在游戏中选定一个长方体区域后自动挖掘其中所有方块。它会走到每个方块前、转向对准、然后破坏——全程模拟自然的类人输入，遵守原版触及距离限制。建议在单人游戏或者私人服务器中使用，在公开服务器中使用请提前与服务器管理员确认，若被反作弊检测，后果自负。

[English README](README.md)

## 功能特性

- **游戏内区域选择** —— 手持任意剑，右键方块选取点 1，潜行 + 右键选取点 2。
- **区域可视化预览** —— 绿色线框渲染选定的长方体（最远 256 格可见）。
- **两种挖掘模式** —— `FROM_TOP_DOWN`（从顶部向下）和 `FROM_BOTTOM_UP`（从底部向上）。
- **类人行为模拟** —— 平滑视角转动配合正弦波抖动、自然挥臂动画、模拟按键输入，以及严格的 4 格触及距离限制。
- **智能移动** —— 障碍物检测并自动跳跃、悬崖/坠落危险检测、岩浆与虚空规避、卡住检测以及行走重试。
- **生存模式感知** —— 正确使用 `attackBlock` + `updateBlockBreakingProgress` 累积进度、工具耐久检测，并支持创造模式瞬挖。
- **回滚检测** —— 定期重新扫描区域，重新挖掘被服务器回滚的方块。
- **安全停止** —— 玩家死亡、游戏暂停或断开连接时自动停止。
- **全可配置** —— 所有时序、距离与重试参数均可在游戏内通过 Mod Menu + Cloth Config 调整。

## 使用方法

| 操作 | 按键 / 输入 |
| --- | --- |
| 选取点 1 | 手持剑 + 右键方块 |
| 选取点 2 | 手持剑 + 潜行 + 右键方块 |
| 开始 / 停止挖掘 | 按 <kbd>K</kbd> |

两个点都选好后，按 <kbd>K</kbd> 开始。模组会遍历长方体内每一个方块，走到它面前、对准并破坏。再次按 <kbd>K</kbd> 停止。

> 剑仅用作选区工具，挖掘时无需手持。开始挖掘前请装备你实际要用的镐（或任意工具）。

## 环境要求

| 依赖 | 版本 |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | >= 0.19.3 |
| Fabric API | *（1.21.11 对应版本） |
| Java | >= 21 |
| Mod Menu | >= 12.0.0（建议） |
| Cloth Config API | 已内置 |

## 安装步骤

1. 为 Minecraft 1.21.11 安装 [Fabric Loader](https://fabricmc.net/)。
2. 下载 [Fabric API](https://modrinth.com/mod/fabric-api)，放入 `mods` 文件夹。
3. （建议）安装 [Mod Menu](https://modrinth.com/mod/modmenu) 以访问配置界面。
4. 将 AreaAutoMiner 的 `.jar` 文件放入 `mods` 文件夹。
5. 启动游戏。

## 配置

打开 **Mods → AreaAutoMiner → Config**（需安装 Mod Menu + Cloth Config），或直接编辑 `config/areaautominer.json`。

### 时序配置

| 选项 | 默认值 | 说明 |
| --- | --- | --- |
| 面向等待 ticks | 15 | 标准转向的等待时间（ticks） |
| 短面向等待 ticks | 4 | 短距离转向的等待时间 |
| 移动等待 ticks | 3 | 移动后等待稳定的时间 |
| 最大行走 ticks | 200 | 到达目标位置的最大时间 |
| 最大卡住 ticks | 20 | 判定为卡住的时间 |
| 最大挖掘 ticks | 400 | 挖掘单个方块的最大时间 |

### 距离配置

| 选项 | 默认值 | 说明 |
| --- | --- | --- |
| 最大到达距离平方 | 16.0 | 玩家到达目标的最大水平距离平方（4 格） |
| 到达阈值 | 1.2 | 到达目标位置的距离阈值 |
| 坠落危险阈值 | 3.0 | 判定为有坠落危险的高度差 |
| 最大垂直距离 | 4.0 | 玩家与目标方块的最大垂直距离 |

### 重试配置

| 选项 | 默认值 | 说明 |
| --- | --- | --- |
| 最大行走重试次数 | 2 | 行走失败后跳过方块前的重试次数 |
| 最大面向重试次数 | 2 | 强制开始挖掘前的重试次数 |
| 每 tick 最大空气跳过数 | 5 | 每 tick 跳过的空气方块数量 |

### 调试配置

| 选项 | 默认值 | 说明 |
| --- | --- | --- |
| 调试模式 | false | 启用调试日志输出 |
| 挖掘模式 | 从顶部向下 | `从顶部向下` / `从底部向上` |
| 回滚检测 | true | 重新扫描被服务器回滚的方块 |
| 最大回滚重试次数 | 3 | 回滚后重新挖掘的最大次数 |
| 回滚检测间隔 | 20 | 两次回滚检测间隔（ticks，20 = 1 秒） |

## 工作原理

挖掘器以每 tick 状态机的形式运行，由 [MiningController](src/main/java/xyz/hengke/areaautominer/controller/MiningController.java) 驱动：

```
IDLE → FINDING_BLOCK → WALKING_TO_BLOCK → FACING_BLOCK → BREAKING →（循环）→ IDLE
```

1. **FINDING_BLOCK** —— [BlockFinder](src/main/java/xyz/hengke/areaautominer/finder/BlockFinder.java) 跳过空气方块，检查触及距离/垂直距离/视线，然后开始行走或直接进入转向。
2. **WALKING_TO_BLOCK** —— [MovementHelper](src/main/java/xyz/hengke/areaautominer/helper/MovementHelper.java) 通过模拟按键引导玩家走向方块，跳跃越过障碍，并跳过过于危险的目标。
3. **FACING_BLOCK** —— [CameraHelper](src/main/java/xyz/hengke/areaautominer/helper/CameraHelper.java) 平滑地将视角转向目标，伴随随收敛逐渐衰减的动态抖动。
4. **BREAKING** —— [BreakingHelper](src/main/java/xyz/hengke/areaautominer/helper/BreakingHelper.java) 破坏方块（创造模式瞬挖，或生存模式 `attackBlock` + `updateBlockBreakingProgress`），方块变为空气后推进到下一个。

共享的 [MiningContext](src/main/java/xyz/hengke/areaautominer/context/MiningContext.java) 保存所有运行时状态，[AreaIterator](src/main/java/xyz/hengke/areaautominer/helper/AreaIterator.java) 按所选挖掘模式遍历长方体。

## 项目结构

```
src/main/java/xyz/hengke/areaautominer/
├── AreaAutoMiner.java            # 通用入口
├── AreaAutoMinerClient.java      # 客户端入口（输入、渲染、tick）
├── config/                       # 配置模型、Cloth Config 界面、Mod Menu 集成
├── context/                      # MiningContext —— 共享运行时状态
├── controller/                   # MiningController —— 状态机驱动
├── finder/                       # BlockFinder —— 下一方块选择
├── helper/                       # 相机、移动、挖掘、输入、区域迭代、空间工具
├── listener/                     # MiningListener —— 事件回调
├── model/                        # MinerMod 与 MiningState 枚举
├── render/                       # RegionRenderer —— 区域线框
└── service/                      # 通知与完成服务
```

## 技术说明

- **纯客户端** —— 无服务端组件，可在原版服务器上安全使用。
- **访问扩展器** —— 扩展了 `KeyBinding.setPressed`，使移动可以通过游戏原生物理模拟按键实现，而非直接传送坐标。
- **无 Mixin** —— 模组仅依赖 Fabric 事件与访问扩展器。

## 构建

```bash
./gradlew build
```

重映射后的模组 jar 位于 `build/libs/`。需要 JDK 21。

## 许可证

源代码基于 **MIT License** 授权。详见 [LICENSE](LICENSE)。
