package xyz.hengke.areaautominer.state;

/**
 * 会话级状态归零（去除 MiningContext 中间层后承载 resetAll 职责）。
 *
 * <p>由 MinerComponents 创建并注入 MiningController；开始新一轮挖掘前统一重置
 * 全部 6 个状态对象（回滚状态已随方案 B 移除），避免手工逐字段重置遗漏。
 * 单一职责：只做归零编排，不感知任何业务语义。</p>
 */
public class StateResetter {
    private final SessionState session;
    private final RegionState region;
    private final TraversalState traversal;
    private final FacingState facing;
    private final MovementState movement;
    private final BreakingState breaking;

    public StateResetter(SessionState session, RegionState region, TraversalState traversal,
                         FacingState facing, MovementState movement, BreakingState breaking) {
        this.session = session;
        this.region = region;
        this.traversal = traversal;
        this.facing = facing;
        this.movement = movement;
        this.breaking = breaking;
    }

    /** 会话级归零：全部 6 个状态对象 reset（开始新一轮挖掘前调用；回滚状态已随方案 B 移除） */
    public void resetAll() {
        session.reset();
        region.reset();
        traversal.reset();
        facing.reset();
        movement.reset();
        breaking.reset();
    }
}
