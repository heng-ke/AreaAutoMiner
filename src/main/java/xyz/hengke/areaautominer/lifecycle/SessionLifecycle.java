package xyz.hengke.areaautominer.lifecycle;

import xyz.hengke.areaautominer.state.SessionState;
import xyz.hengke.areaautominer.helper.InputHelper;
import xyz.hengke.areaautominer.helper.PathfindingHelper;
import xyz.hengke.areaautominer.model.MiningState;

/**
 * 会话收尾统一出口：停止挖掘 / 挖掘完成共用同一套 teardown，
 * 避免 stopMining() 与 forceCompleteMining() 两处逻辑漂移。
 */
public class SessionLifecycle {
    private final SessionState session;
    private final InputHelper inputHelper;
    private final PathfindingHelper pathfindingHelper;

    public SessionLifecycle(SessionState session, InputHelper inputHelper, PathfindingHelper pathfindingHelper) {
        this.session = session;
        this.inputHelper = inputHelper;
        this.pathfindingHelper = pathfindingHelper;
    }

    /** 结束挖掘会话：关闭挖掘状态、回到 IDLE、释放按键、清理寻路资源 */
    public void teardown() {
        session.setMining(false);
        session.setState(MiningState.IDLE);
        inputHelper.releaseAllKeys();
        pathfindingHelper.cleanup();
    }
}
