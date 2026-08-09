package xyz.hengke.areaautominer.context.state;

import xyz.hengke.areaautominer.listener.MiningListener;
import xyz.hengke.areaautominer.model.MiningState;

/**
 * 会话状态：是否在挖掘、状态机当前状态、外部事件监听器。
 * listener 属于装配期注入，reset() 不会清掉。
 */
public class SessionState {
    private boolean isMining = false;
    private MiningState state = MiningState.IDLE;
    private MiningListener listener = null;

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

    public MiningListener getListener() {
        return listener;
    }

    public void setListener(MiningListener listener) {
        this.listener = listener;
    }

    /** 会话归零：停止挖掘并回到 IDLE（保留 listener） */
    public void reset() {
        isMining = false;
        state = MiningState.IDLE;
    }
}
