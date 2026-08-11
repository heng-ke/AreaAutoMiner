package xyz.hengke.areaautominer.state;

import xyz.hengke.areaautominer.model.RegionState;
import xyz.hengke.areaautominer.model.TraversalState;

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

    public void resetAll() {
        session.reset();
        region.reset();
        traversal.reset();
        facing.reset();
        movement.reset();
        breaking.reset();
    }
}
