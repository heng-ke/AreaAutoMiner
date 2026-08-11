package xyz.hengke.areaautominer.config;

import me.shedaniel.autoconfig.ConfigData;
import xyz.hengke.areaautominer.model.MinerMod;

public final class MiningConfigValidator {
    private MiningConfigValidator() {
    }
    public static void validate(MiningConfig c) throws ConfigData.ValidationException {
        if (c.minerMod == null) {
            c.minerMod = MinerMod.FROM_TOP_DOWN;
        }

        // 时序
        c.moveWaitTicks = clampInt(c.moveWaitTicks, 0, 100);
        c.maxAirSkipPerTick = clampInt(c.maxAirSkipPerTick, 1, 100);
        c.maxWalkTicks = clampInt(c.maxWalkTicks, 1, 10_000);
        c.maxStuckTicks = clampInt(c.maxStuckTicks, 1, 1_000);
        c.maxBreakTicks = clampInt(c.maxBreakTicks, 1, 10_000);
        c.maxFaceTicks = clampInt(c.maxFaceTicks, 1, 1_000);
        // 距离
        c.selectionMaxDistance = clampDouble(c.selectionMaxDistance, 1.0, 256.0, 5.0);
        c.maxReachSquared = clampDouble(c.maxReachSquared, 1.0, 1024.0, 20.25);
        c.arriveThreshold = clampDouble(c.arriveThreshold, 0.1, 64.0, 1.2);
        c.maxVerticalDistance = clampDouble(c.maxVerticalDistance, 1.0, 128.0, 4.0);
        c.pathFollowRange = clampInt(c.pathFollowRange, 1, 128);
        // 重试
        c.maxWalkRetries = clampInt(c.maxWalkRetries, 0, 100); 
        // 挖掘
        c.minToolDurability = clampInt(c.minToolDurability, 0, 100_000);
        c.facingThresholdDegrees = clampDouble(c.facingThresholdDegrees, 0.1, 90.0, 5.0);
        c.reFacingThresholdDegrees = clampDouble(c.reFacingThresholdDegrees, 0.1, 180.0, 15.0);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
