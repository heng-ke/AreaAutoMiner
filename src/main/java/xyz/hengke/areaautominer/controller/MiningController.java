package xyz.hengke.areaautominer.controller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.AreaAutoMiner;
import xyz.hengke.areaautominer.finder.BlockFinder;
import xyz.hengke.areaautominer.helper.AdvanceCoordinator;
import xyz.hengke.areaautominer.helper.BreakingHelper;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.MovementHelper;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.model.BreakOutcome;
import xyz.hengke.areaautominer.model.FaceResult;
import xyz.hengke.areaautominer.model.MinerMod;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.model.PathMode;
import xyz.hengke.areaautominer.service.Messages;
import xyz.hengke.areaautominer.service.NotificationService;
import xyz.hengke.areaautominer.state.BreakingState;
import xyz.hengke.areaautominer.model.RegionState;
import xyz.hengke.areaautominer.state.SessionState;
import xyz.hengke.areaautominer.state.StateResetter;
import xyz.hengke.areaautominer.model.TraversalState;

import java.util.List;

public class MiningController {
    private final MinecraftClient client;
    private final SessionState session;
    private final RegionState region;
    private final TraversalState traversal;
    private final StateResetter stateResetter;
    private final MiningConfig config;
    private final BlockFinder blockFinder;
    private final MovementHelper movementHelper;
    private final CameraHelper cameraHelper;
    private final BreakingHelper breakingHelper;
    private final NotificationService notificationService;
    private final SessionLifecycle lifecycle;
    private final AdvanceCoordinator advanceCoordinator;

    public MiningController(MinecraftClient client, SessionState session, RegionState region,
                            TraversalState traversal, BreakingState breaking,
                            StateResetter stateResetter, MiningConfig config, BlockFinder blockFinder,
                            MovementHelper movementHelper, CameraHelper cameraHelper, BreakingHelper breakingHelper,
                            NotificationService notificationService, SessionLifecycle lifecycle,
                            AdvanceCoordinator advanceCoordinator) {
        this.client = client;
        this.session = session;
        this.region = region;
        this.traversal = traversal;
        this.stateResetter = stateResetter;
        this.config = config;
        this.blockFinder = blockFinder;
        this.movementHelper = movementHelper;
        this.cameraHelper = cameraHelper;
        this.breakingHelper = breakingHelper;
        this.notificationService = notificationService;
        this.lifecycle = lifecycle;
        this.advanceCoordinator = advanceCoordinator;

        session.onEnter(MiningState.BREAKING, breaking::beginBreakSession);
        session.onExit(MiningState.BREAKING, breaking::reset);
        session.setIllegalTransitionHandler((from, to) ->
                AreaAutoMiner.LOGGER.warn("[AreaAutoMiner] 非法状态转移被拒绝: {} -> {}", from, to));
    }

    public void startMining(BlockPos p1, BlockPos p2) {
        if (isMining()) return;
        if (p1 == null || p2 == null) {
            notificationService.sendMessage(Messages.NEED_SELECT_REGION);
            return;
        }

        stateResetter.resetAll();

        region.setRegion(p1, p2);
        session.setMining(true);

        if (config.minerMod == MinerMod.FROM_TOP_DOWN) {
            traversal.setCurrentY(region.getMaxY());
        } else {
            traversal.setCurrentY(region.getMinY());
        }
        traversal.setCurrentX(region.getMinX());
        traversal.setCurrentZ(region.getMinZ());
        session.transitionTo(MiningState.FINDING_BLOCK);

        notificationService.sendMessage(Messages.START_MINING);
        notificationService.logDebug("开始挖掘区域");
    }

    public void stopMining() {
        stopMining(Messages.STOP_MINING);
    }
    public void stopMining(String stopReason) {
        if (!isMining()) return;

        lifecycle.teardown();

        notificationService.sendMessage(stopReason);
        notificationService.logDebug(stopReason);
    }

    public boolean isMining() {
        return session.isMining();
    }
    public BlockPos getCurrentTargetPos() {
        if (!isMining()) return null;
        return traversal.getPosition();
    }

    public void onRenderFrame() {
        cameraHelper.smoothFrame();
    }

    public List<BlockPos> getDebugWalkPath() {
        if (!config.showPath) return null;
        return movementHelper.getDebugPathNodes();
    }

    public PathMode getDebugPathMode() {
        if (!config.debug) return null;
        return movementHelper.getDebugPathMode();
    }

    public void tick() {
        if (!isMining() || client.player == null || client.interactionManager == null || client.world == null) {
            return;
        }

        switch (session.getState()) {
            case FINDING_BLOCK:
                switch (blockFinder.findNext()) {
                    case BREAK:
                        session.transitionTo(MiningState.BREAKING);
                        break;
                    case FACE:
                        session.transitionTo(MiningState.FACING_BLOCK);
                        break;
                    case WALK:
                        session.transitionTo(MiningState.WALKING_TO_BLOCK);
                        break;
                    case CONTINUE:
                    case COMPLETE:
                        break;
                }
                break;

            case WALKING_TO_BLOCK:
                BlockPos walkTarget = traversal.getPosition();
                switch (movementHelper.walkToBlock(walkTarget)) {
                    case ARRIVED:
                        session.transitionTo(MiningState.FACING_BLOCK);
                        break;
                    case SKIPPED:
                        if (advanceCoordinator.advanceAfterSkipped(walkTarget)) {
                            session.transitionTo(MiningState.FINDING_BLOCK);
                        }
                        break;
                    case ONGOING:
                        break;
                }
                break;

            case FACING_BLOCK:
                if (cameraHelper.faceBlock() == FaceResult.CONVERGED) {
                    session.transitionTo(MiningState.BREAKING);
                }
                break;

            case BREAKING:
                BlockPos breakTarget = traversal.getPosition();
                BreakOutcome outcome = breakingHelper.startBreaking(breakTarget);
                switch (outcome) {
                    case MINED:
                        if (advanceCoordinator.advanceAfterMined(breakTarget)) {
                            session.transitionTo(MiningState.FINDING_BLOCK);
                        }
                        break;
                    case SKIPPED:
                        if (advanceCoordinator.advanceAfterSkipped(breakTarget)) {
                            session.transitionTo(MiningState.FINDING_BLOCK);
                        }
                        break;
                    case EXTERNALLY_REMOVED:
                        if (advanceCoordinator.advanceSilently()) {
                            session.transitionTo(MiningState.FINDING_BLOCK);
                        }
                        break;
                    case NEED_FACE:
                        session.transitionTo(MiningState.FACING_BLOCK);
                        break;
                    case NEED_WALK:
                        session.transitionTo(MiningState.WALKING_TO_BLOCK);
                        break;
                    case ONGOING:
                        break;
                    default:
                        notificationService.logDebug("未处理的挖掘结果: " + outcome);
                        break;
                }
                break;

            case IDLE:
            default:
                break;
        }
    }
}
