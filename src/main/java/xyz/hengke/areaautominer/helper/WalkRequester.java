package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.state.MovementState;

public class WalkRequester {
    public enum Result {
        WALKING,
        SKIPPED
    }

    private final MovementState movement;

    public WalkRequester(MovementState movement) {
        this.movement = movement;
    }

    public Result requestWalkOrSkip(BlockPos targetPos, double playerX, double playerZ) {
        if (movement.startWalkingToBlock(targetPos, playerX, playerZ)) {
            return Result.SKIPPED;
        }
        return Result.WALKING;
    }
}
