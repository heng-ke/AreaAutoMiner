package xyz.hengke.areaautominer.context.state;

import net.minecraft.util.math.BlockPos;

/**
 * 挖掘状态：首 tick 标记、挖掘进度与最近挖掉的方块。
 */
public class BreakingState {
    private boolean firstBreakTick = false;
    private int breakTicks = 0;
    private BlockPos lastMinedPos = null;

    public boolean isFirstBreakTick() {
        return firstBreakTick;
    }

    public void setFirstBreakTick(boolean firstBreakTick) {
        this.firstBreakTick = firstBreakTick;
    }

    public int getBreakTicks() {
        return breakTicks;
    }

    public void setBreakTicks(int breakTicks) {
        this.breakTicks = breakTicks;
    }

    public BlockPos getLastMinedPos() {
        return lastMinedPos;
    }

    public void setLastMinedPos(BlockPos lastMinedPos) {
        this.lastMinedPos = lastMinedPos;
    }

    public void reset() {
        firstBreakTick = false;
        breakTicks = 0;
        lastMinedPos = null;
    }
}
