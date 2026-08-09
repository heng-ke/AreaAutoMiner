package xyz.hengke.areaautominer.context;

import net.minecraft.client.MinecraftClient;
import xyz.hengke.areaautominer.context.state.BreakingState;
import xyz.hengke.areaautominer.context.state.FacingState;
import xyz.hengke.areaautominer.context.state.MovementState;
import xyz.hengke.areaautominer.context.state.RegionState;
import xyz.hengke.areaautominer.context.state.RollbackState;
import xyz.hengke.areaautominer.context.state.SessionState;
import xyz.hengke.areaautominer.context.state.TraversalState;

/**
 * 组合根：持有共享基础设施（client）与按领域内聚的 7 个状态对象。
 *
 * <p>各 Helper 不再依赖"整个 context 的全部 getter/setter"，而是只依赖自己
 * 需要的状态对象；会话归零统一走 {@link #resetAll()}，避免手工逐字段重置遗漏。</p>
 */
public class MiningContext {
    private final MinecraftClient client;

    private final SessionState session = new SessionState();
    private final RegionState region = new RegionState();
    private final TraversalState traversal = new TraversalState();
    private final FacingState facing = new FacingState();
    private final MovementState movement = new MovementState();
    private final BreakingState breaking = new BreakingState();
    private final RollbackState rollback = new RollbackState();

    public MiningContext(MinecraftClient client) {
        this.client = client;
    }

    public MinecraftClient getClient() {
        return client;
    }

    public SessionState session() {
        return session;
    }

    public RegionState region() {
        return region;
    }

    public TraversalState traversal() {
        return traversal;
    }

    public FacingState facing() {
        return facing;
    }

    public MovementState movement() {
        return movement;
    }

    public BreakingState breaking() {
        return breaking;
    }

    public RollbackState rollback() {
        return rollback;
    }

    /** 会话级状态归零（开始新一轮挖掘前调用）。listener 由 SessionState 保留。 */
    public void resetAll() {
        session.reset();
        region.reset();
        traversal.reset();
        facing.reset();
        movement.reset();
        breaking.reset();
        rollback.reset();
    }
}
