package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;

public class WalkCycleBreaker {
    public static final int MAX_WALK_CYCLE_COUNT = 3;

    private BlockPos lastWalkTarget = null;
    private int walkCycleCount = 0;

    public boolean recordWalkAttempt(BlockPos targetPos) {
        if (targetPos != null && targetPos.equals(lastWalkTarget)) {
            walkCycleCount++;
        } else {
            walkCycleCount = 0;
            lastWalkTarget = targetPos;
        }
        return walkCycleCount >= MAX_WALK_CYCLE_COUNT;
    }

    public void reset() {
        lastWalkTarget = null;
        walkCycleCount = 0;
    }
}
