package xyz.hengke.areaautominer.state;

import xyz.hengke.areaautominer.model.MiningState;

import java.util.EnumMap;
import java.util.Map;

public class SessionState {
    private boolean isMining = false;
    private MiningState state = MiningState.IDLE;
    private final Map<MiningState, Runnable> enterActions = new EnumMap<>(MiningState.class);

    public boolean isMining() {
        return isMining;
    }

    public void setMining(boolean mining) {
        isMining = mining;
    }

    public MiningState getState() {
        return state;
    }
    public void onEnter(MiningState state, Runnable action) {
        enterActions.put(state, action);
    }
    public void transitionTo(MiningState newState) {
        if (newState == this.state) return;
        this.state = newState;
        Runnable action = enterActions.get(newState);
        if (action != null) action.run();
    }

    public void reset() {
        isMining = false;
        state = MiningState.IDLE;
    }
}
