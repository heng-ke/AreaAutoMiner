package xyz.hengke.areaautominer.di;

import net.minecraft.client.MinecraftClient;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.config.MiningConfigHolder;
import xyz.hengke.areaautominer.controller.MiningController;
import xyz.hengke.areaautominer.finder.BlockFinder;
import xyz.hengke.areaautominer.helper.AdvanceCoordinator;
import xyz.hengke.areaautominer.helper.AreaIterator;
import xyz.hengke.areaautominer.helper.BreakingHelper;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.InputHelper;
import xyz.hengke.areaautominer.helper.MovementHelper;
import xyz.hengke.areaautominer.helper.PathfindingHelper;
import xyz.hengke.areaautominer.helper.WalkRequester;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.service.BlockEventReporter;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;
import xyz.hengke.areaautominer.state.BreakingState;
import xyz.hengke.areaautominer.state.FacingState;
import xyz.hengke.areaautominer.state.MovementState;
import xyz.hengke.areaautominer.model.RegionState;
import xyz.hengke.areaautominer.state.SessionState;
import xyz.hengke.areaautominer.state.StateResetter;
import xyz.hengke.areaautominer.model.TraversalState;

public class MinerComponents {
    private final StateResetter stateResetter;
    private final InputHelper inputHelper;
    private final NotificationService notificationService;
    private final PathfindingHelper pathfindingHelper;
    private final AreaIterator areaIterator;
    private final CameraHelper cameraHelper;
    private final WalkRequester walkRequester;
    private final BreakingHelper breakingHelper;
    private final MovementHelper movementHelper;
    private final BlockFinder blockFinder;
    private final BlockEventReporter blockEventReporter;
    private final MiningCompletionService completionService;
    private final AdvanceCoordinator advanceCoordinator;
    private final SessionLifecycle lifecycle;
    private final MiningController controller;

    public MinerComponents(MinecraftClient client) {
        MiningConfig config = MiningConfigHolder.get();

        SessionState session = new SessionState();
        RegionState region = new RegionState();
        TraversalState traversal = new TraversalState();
        FacingState facing = new FacingState();
        MovementState movement = new MovementState();
        BreakingState breaking = new BreakingState();
        this.stateResetter = new StateResetter(session, region, traversal, facing, movement, breaking);

        this.inputHelper = new InputHelper(client);
        this.notificationService = new NotificationService(client, config);
        this.pathfindingHelper = new PathfindingHelper(client, config);
        this.areaIterator = new AreaIterator(region, traversal, config);
        this.lifecycle = new SessionLifecycle(session, inputHelper, pathfindingHelper);

        this.blockEventReporter = new BlockEventReporter(notificationService);

        this.completionService = new MiningCompletionService(
                notificationService, lifecycle, blockEventReporter);

        this.advanceCoordinator = new AdvanceCoordinator(areaIterator, completionService);

        this.cameraHelper = new CameraHelper(client, config, session, movement,
                facing, inputHelper, notificationService);
        this.walkRequester = new WalkRequester(movement);
        this.breakingHelper = new BreakingHelper(client, config,
                breaking, facing,
                notificationService, cameraHelper, walkRequester);
        this.movementHelper = new MovementHelper(client, config, movement,
                inputHelper, cameraHelper, notificationService, pathfindingHelper);
        this.blockFinder = new BlockFinder(client, config, traversal,
                cameraHelper, notificationService, advanceCoordinator, walkRequester,
                facing);

        this.controller = new MiningController(client, session, region, traversal, breaking,
                stateResetter, config, blockFinder, movementHelper, cameraHelper,
                breakingHelper, notificationService, lifecycle, advanceCoordinator);
    }

    public NotificationService notificationService() {
        return notificationService;
    }

    public MiningController controller() {
        return controller;
    }
}
