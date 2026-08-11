package xyz.hengke.areaautominer.lifecycle;

import xyz.hengke.areaautominer.state.SessionState;
import xyz.hengke.areaautominer.helper.InputHelper;
import xyz.hengke.areaautominer.helper.PathfindingHelper;
import xyz.hengke.areaautominer.model.MiningState;

public class SessionLifecycle {
    private final SessionState session;
    private final InputHelper inputHelper;
    private final PathfindingHelper pathfindingHelper;

    public SessionLifecycle(SessionState session, InputHelper inputHelper, PathfindingHelper pathfindingHelper) {
        this.session = session;
        this.inputHelper = inputHelper;
        this.pathfindingHelper = pathfindingHelper;
    }

    public void teardown() {
        session.setMining(false);
        session.transitionTo(MiningState.IDLE);
        inputHelper.releaseAllKeys();
        pathfindingHelper.cleanup();
    }
}
