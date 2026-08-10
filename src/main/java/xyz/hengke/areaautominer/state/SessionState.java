package xyz.hengke.areaautominer.state;

import xyz.hengke.areaautominer.model.MiningState;

/**
 * 会话状态：是否在挖掘、状态机当前状态。
 */
public class SessionState {
    private boolean isMining = false;
    private MiningState state = MiningState.IDLE;

    public boolean isMining() {
        return isMining;
    }

    public void setMining(boolean mining) {
        isMining = mining;
    }

    public MiningState getState() {
        return state;
    }

    public void setState(MiningState state) {
        this.state = state;
    }

    /** 会话归零：停止挖掘并回到 IDLE */
    public void reset() {
        isMining = false;
        state = MiningState.IDLE;
    }
}
