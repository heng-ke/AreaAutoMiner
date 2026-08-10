package xyz.hengke.areaautominer.config;

import me.shedaniel.autoconfig.ConfigData;
import xyz.hengke.areaautominer.model.MinerMod;

/**
 * 配置数值边界校验（方案 6）：从 MiningConfig.validatePostLoad 提取，可独立单测。
 *
 * <p>校验规则（P1 修复：F-02/F5/D4 三方共识）：
 * 手动编辑 config/areaautominer.json 可能写入 0/负值/NaN/Infinity，导致：
 * <ul>
 *   <li>maxAirSkipPerTick=0 → FINDING 每 tick 跳过 0 个空气即返回，游标永不前进（软锁死循环）</li>
 *   <li>maxReachSquared&lt;0 → 全部方块判 out-of-reach → 触发 F1 不可达死循环</li>
 *   <li>rollbackCheckInterval&lt;0 → 每 tick 都触发全量回滚扫描（性能）</li>
 * </ul>
 * 策略：有限值钳制到 [min,max] 保留用户意图；NaN/Infinity 无法比较，恢复默认值。</p>
 */
public final class MiningConfigValidator {
    private MiningConfigValidator() {
    }

    /** 加载后校验（由 MiningConfig.validatePostLoad 委托调用；AutoConfig 行为不变） */
    public static void validate(MiningConfig c) throws ConfigData.ValidationException {
        // 兜底：手动编辑 JSON 将 minerMod 置为 null 时恢复默认，避免 NPE
        if (c.minerMod == null) {
            c.minerMod = MinerMod.FROM_TOP_DOWN;
        }

        // 时序
        c.moveWaitTicks = clampInt(c.moveWaitTicks, 0, 100);            // 0 = 不等待（合法）
        c.maxAirSkipPerTick = clampInt(c.maxAirSkipPerTick, 1, 100);    // >=1 防软锁（D4）
        c.maxWalkTicks = clampInt(c.maxWalkTicks, 1, 10_000);           // >=1 防立即超时
        c.maxStuckTicks = clampInt(c.maxStuckTicks, 1, 1_000);
        c.maxBreakTicks = clampInt(c.maxBreakTicks, 1, 10_000);
        c.maxFaceTicks = clampInt(c.maxFaceTicks, 1, 1_000);
        // 距离
        c.selectionMaxDistance = clampDouble(c.selectionMaxDistance, 1.0, 256.0, 5.0);   // 选点射线必须为正
        c.maxReachSquared = clampDouble(c.maxReachSquared, 1.0, 1024.0, 20.25);          // >0 防全 out-of-reach（F5）
        c.arriveThreshold = clampDouble(c.arriveThreshold, 0.1, 64.0, 1.2);              // >0 防 checkArrive 恒命中
        c.maxVerticalDistance = clampDouble(c.maxVerticalDistance, 1.0, 128.0, 4.0);     // >0 防全 out-of-reach
        c.pathFollowRange = clampInt(c.pathFollowRange, 1, 128);        // >=1 防寻路失效；与内部 min(64,·) 一致
        // 重试
        c.maxWalkRetries = clampInt(c.maxWalkRetries, 0, 100);          // 0 = 不重试直接跳过（合法）
        // 挖掘
        c.minToolDurability = clampInt(c.minToolDurability, 0, 100_000); // 0 = 关闭耐久检查（合法）
        c.facingThresholdDegrees = clampDouble(c.facingThresholdDegrees, 0.1, 90.0, 5.0);
        c.reFacingThresholdDegrees = clampDouble(c.reFacingThresholdDegrees, 0.1, 180.0, 15.0);
        // 回滚
        c.maxRollbackRetries = clampInt(c.maxRollbackRetries, 0, 100);  // 0 = 不执行回滚重扫（合法）
        c.maxMinedPositions = clampInt(c.maxMinedPositions, 0, 1_000_000);  // 0 = 不记录（合法）；上限防内存膨胀
        c.rollbackCheckInterval = clampInt(c.rollbackCheckInterval, 1, 100_000); // >=1 防每 tick 全扫
    }

    /** 整数钳制到 [min, max]（有限 int 无需 NaN 检查） */
    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 浮点钳制到 [min, max]；NaN/Infinity 无法比较，恢复默认值 */
    private static double clampDouble(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
