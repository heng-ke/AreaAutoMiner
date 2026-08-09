package xyz.hengke.areaautominer.di;

import net.minecraft.client.MinecraftClient;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.config.MiningConfigHolder;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.controller.MiningController;
import xyz.hengke.areaautominer.finder.BlockFinder;
import xyz.hengke.areaautominer.helper.AreaIterator;
import xyz.hengke.areaautominer.helper.BreakingHelper;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.InputHelper;
import xyz.hengke.areaautominer.helper.MovementHelper;
import xyz.hengke.areaautominer.helper.PathfindingHelper;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;

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
    private final BreakingHelper breakingHelper;
    private final MovementHelper movementHelper;
    private final BlockFinder blockFinder;
    private final MiningCompletionService completionService;
    private final SessionLifecycle lifecycle;
    private final MiningController controller;

    public MinerComponents(MinecraftClient client) {
        MiningConfig config = MiningConfigHolder.get();

        this.context = new MiningContext(client);
        this.inputHelper = new InputHelper(client);
        this.notificationService = new NotificationService(client, config);
        this.pathfindingHelper = new PathfindingHelper(client, config);
        this.areaIterator = new AreaIterator(context.region(), context.traversal(), context.rollback(), config);
        this.lifecycle = new SessionLifecycle(context.session(), inputHelper, pathfindingHelper);

        this.completionService = new MiningCompletionService(client, config,
                context.rollback(), context.session(), context.breaking(),
                notificationService, lifecycle, areaIterator);

        this.cameraHelper = new CameraHelper(client, config, context.session(), context.movement(),
                context.facing(), context.breaking(), inputHelper, notificationService);
        this.breakingHelper = new BreakingHelper(client, config, areaIterator,
                context.breaking(), context.facing(), context.movement(), context.session(),
                notificationService, completionService, cameraHelper, lifecycle);
        this.movementHelper = new MovementHelper(client, config, context.movement(), context.session(),
                areaIterator, inputHelper, cameraHelper, notificationService, completionService, pathfindingHelper);
        this.blockFinder = new BlockFinder(client, config, areaIterator,
                cameraHelper, notificationService, completionService,
                context.movement(), context.facing(), context.breaking(), context.session());

        this.controller = new MiningController(context, config, blockFinder, movementHelper, cameraHelper,
                breakingHelper, notificationService, lifecycle);
    }

    public MiningContext context() {
        return context;
    }

    public MiningController controller() {
        return controller;
    }
}
