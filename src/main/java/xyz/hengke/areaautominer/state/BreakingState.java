package xyz.hengke.areaautominer.state;

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

    public void beginBreakSession() {
        this.firstBreakTick = true;
        this.breakTicks = 0;
    }

    public void reset() {
        firstBreakTick = false;
        breakTicks = 0;
    }
}
