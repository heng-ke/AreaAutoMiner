package xyz.hengke.areaautominer.state;

/**
 * 挖掘状态：首 tick 标记与挖掘进度。
 */
public class BreakingState {
    private boolean firstBreakTick = false;
    private int breakTicks = 0;

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

    /**
     * 开始一次挖掘会话：首 tick 标记置 true（attackBlock 建立挖掘）+ 挖掘进度归零。
     * 进入 BREAKING 状态时统一初始化（方案 C2：由 MiningController 在状态转移时调用），
     * 消除 CameraHelper.finishFacing 与 BlockFinder.findNext 两处重复的跨域写入。
     */
    public void beginBreakSession() {
        this.firstBreakTick = true;
        this.breakTicks = 0;
    }

    public void reset() {
        firstBreakTick = false;
        breakTicks = 0;
    }
}
