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
import xyz.hengke.areaautominer.helper.ToolDurabilityGuard;
import xyz.hengke.areaautominer.helper.WalkRequester;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.service.BlockEventReporter;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;
import xyz.hengke.areaautominer.state.BreakingState;
import xyz.hengke.areaautominer.state.FacingState;
import xyz.hengke.areaautominer.state.MovementState;
import xyz.hengke.areaautominer.state.RegionState;
import xyz.hengke.areaautominer.state.SessionState;
import xyz.hengke.areaautominer.state.StateResetter;
import xyz.hengke.areaautominer.state.TraversalState;

/**
 * 手动依赖组合根：集中装配所有组件，消除分散在 MiningController 构造器中的手工 new。
 * 新增依赖只需在此处接线一次；各组件构造参数即其真实依赖面。
 *
 * <p>无中间层：7 个状态对象由本类直接创建，按需注入各组件（各组件只依赖它真正需要的 state）。</p>
 */
public class MinerComponents {
    private final StateResetter stateResetter;
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
    private final SessionLifecycle lifecycle;
    private final MiningController controller;

    public MinerComponents(MinecraftClient client) {
        MiningConfig config = MiningConfigHolder.get();

        // 6 个状态对象由组合根统一创建，按需注入（无 MiningContext 中间层；回滚状态已随方案 B 移除）
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

        this.advanceCoordinator = new AdvanceCoordinator(areaIterator,
                completionService, breaking);

        this.cameraHelper = new CameraHelper(client, config, session, movement,
                facing, inputHelper, notificationService);
        this.toolDurabilityGuard = new ToolDurabilityGuard(client, config, notificationService);
        this.walkRequester = new WalkRequester(movement, session);
        this.breakingHelper = new BreakingHelper(client, config,
                breaking, facing, session,
                notificationService, cameraHelper, lifecycle, toolDurabilityGuard, walkRequester);
        this.movementHelper = new MovementHelper(client, config, movement, session,
                inputHelper, cameraHelper, notificationService, pathfindingHelper);
        this.blockFinder = new BlockFinder(client, config, traversal,
                cameraHelper, notificationService, advanceCoordinator, walkRequester,
                facing, session);

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
