package xyz.hengke.areaautominer.state;

import xyz.hengke.areaautominer.model.MiningState;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 会话状态：持有当前枚举状态、enter/exit 动作表与合法转移表（guard）。
 *
 * <p>transitionTo 执行完整语义：非法转移拒绝并告警 → exit(旧态) → 置新态 → enter(新态)。
 * 合法转移表见 {@link #ALLOWED_TRANSITIONS}，新增状态或转移必须先登记，否则运行期被拒绝。</p>
 */
public class SessionState {
    private boolean isMining = false;
    private MiningState state = MiningState.IDLE;
    private final Map<MiningState, Runnable> enterActions = new EnumMap<>(MiningState.class);
    private final Map<MiningState, Runnable> exitActions = new EnumMap<>(MiningState.class);
    private BiConsumer<MiningState, MiningState> illegalTransitionHandler = null;

    /**
     * 合法转移表：显式声明允许的转移，其余一律拒绝。
     * 所有状态均可经 teardown 回到 IDLE（stopMining / 完成 / 玩家死亡 / 界面打开）。
     */
    private static final Map<MiningState, Set<MiningState>> ALLOWED_TRANSITIONS = new EnumMap<>(MiningState.class);

    static {
        ALLOWED_TRANSITIONS.put(MiningState.IDLE, Set.of(MiningState.FINDING_BLOCK));
        ALLOWED_TRANSITIONS.put(MiningState.FINDING_BLOCK, Set.of(
                MiningState.BREAKING, MiningState.FACING_BLOCK, MiningState.WALKING_TO_BLOCK, MiningState.IDLE));
        ALLOWED_TRANSITIONS.put(MiningState.WALKING_TO_BLOCK, Set.of(
                MiningState.FACING_BLOCK, MiningState.FINDING_BLOCK, MiningState.IDLE));
        ALLOWED_TRANSITIONS.put(MiningState.FACING_BLOCK, Set.of(
                MiningState.BREAKING, MiningState.IDLE));
        ALLOWED_TRANSITIONS.put(MiningState.BREAKING, Set.of(
                MiningState.FINDING_BLOCK, MiningState.FACING_BLOCK, MiningState.WALKING_TO_BLOCK, MiningState.IDLE));
    }

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

    public void onExit(MiningState state, Runnable action) {
        exitActions.put(state, action);
    }

    public void setIllegalTransitionHandler(BiConsumer<MiningState, MiningState> handler) {
        this.illegalTransitionHandler = handler;
    }

    /**
     * 状态转移：guard 校验 → exit(旧态) → 置新态 → enter(新态)。
     * 同态转移直接返回（不重复触发动作）；非法转移被拒绝并回调告警 handler。
     */
    public void transitionTo(MiningState newState) {
        if (newState == this.state) return;

        Set<MiningState> allowed = ALLOWED_TRANSITIONS.get(this.state);
        if (allowed == null || !allowed.contains(newState)) {
            if (illegalTransitionHandler != null) {
                illegalTransitionHandler.accept(this.state, newState);
            }
            return;
        }

        Runnable exit = exitActions.get(this.state);
        if (exit != null) exit.run();
        this.state = newState;
        Runnable enter = enterActions.get(newState);
        if (enter != null) enter.run();
    }

    public void reset() {
        isMining = false;
        state = MiningState.IDLE;
    }
}
