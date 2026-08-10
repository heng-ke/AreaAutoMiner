package xyz.hengke.areaautominer.di;

import net.minecraft.client.MinecraftClient;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.config.MiningConfigHolder;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.controller.MiningController;
import xyz.hengke.areaautominer.finder.BlockFinder;
import xyz.hengke.areaautominer.helper.AdvanceCoordinator;
import xyz.hengke.areaautominer.helper.AreaIterator;
import xyz.hengke.areaautominer.helper.BreakingHelper;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.InputHelper;
import xyz.hengke.areaautominer.helper.MovementHelper;
import xyz.hengke.areaautominer.helper.PathfindingHelper;
import xyz.hengke.areaautominer.helper.ToolDurabilityGuard;
import xyz.hengke.areaautominer.helper.WalkRequester;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.service.BlockEventReporter;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;
import xyz.hengke.areaautominer.service.RollbackDetector;

/**
 * 手动依赖组合根：集中装配所有组件，消除分散在 MiningController 构造器中的手工 new。
 * 新增依赖只需在此处接线一次；各组件构造参数即其真实依赖面。
 */
public class MinerComponents {
    private final MiningContext context;
    private final InputHelper inputHelper;
    private final NotificationService notificationService;
    private final PathfindingHelper pathfindingHelper;
    private final AreaIterator areaIterator;
    private final CameraHelper cameraHelper;
    private final ToolDurabilityGuard toolDurabilityGuard;
    private final WalkRequester walkRequester;
    private final BreakingHelper breakingHelper;
    private final MovementHelper movementHelper;
    private final BlockFinder blockFinder;
    private final BlockEventReporter blockEventReporter;
    private final MiningCompletionService completionService;
    private final AdvanceCoordinator advanceCoordinator;
    private final RollbackDetector rollbackDetector;
    private final SessionLifecycle lifecycle;
    private final MiningController controller;

    public MinerComponents(MinecraftClient client) {
        MiningConfig config = MiningConfigHolder.get();

        this.context = new MiningContext(client);
        this.inputHelper = new InputHelper(client);
        this.notificationService = new NotificationService(client, config);
        this.pathfindingHelper = new PathfindingHelper(client, config);
        this.areaIterator = new AreaIterator(context.region(), context.traversal(), config);
        this.lifecycle = new SessionLifecycle(context.session(), inputHelper, pathfindingHelper);

        this.blockEventReporter = new BlockEventReporter(context.session(), notificationService);

        this.completionService = new MiningCompletionService(client, config,
                context.rollback(), context.session(), context.breaking(),
                notificationService, lifecycle, areaIterator, blockEventReporter);

        this.advanceCoordinator = new AdvanceCoordinator(areaIterator, context.rollback(),
                completionService, context.breaking());

        this.rollbackDetector = new RollbackDetector(client, config, context.rollback());

        this.cameraHelper = new CameraHelper(client, config, context.session(), context.movement(),
                context.facing(), inputHelper, notificationService);
        this.toolDurabilityGuard = new ToolDurabilityGuard(client, config, notificationService);
        this.walkRequester = new WalkRequester(context.movement(), context.session());
        this.breakingHelper = new BreakingHelper(client, config,
                context.breaking(), context.facing(), context.session(),
                notificationService, cameraHelper, lifecycle, toolDurabilityGuard, walkRequester);
        this.movementHelper = new MovementHelper(client, config, context.movement(), context.session(),
                inputHelper, cameraHelper, notificationService, pathfindingHelper);
        this.blockFinder = new BlockFinder(client, config, context.traversal(),
                cameraHelper, notificationService, advanceCoordinator, walkRequester,
                context.facing(), context.session());

        this.controller = new MiningController(context, config, blockFinder, movementHelper, cameraHelper,
                breakingHelper, notificationService, lifecycle, advanceCoordinator, rollbackDetector);
    }

    public MiningContext context() {
        return context;
    }

    public NotificationService notificationService() {
        return notificationService;
    }

    public MiningController controller() {
        return controller;
    }
}
