package xyz.hengke.areaautominer.controller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.finder.BlockFinder;
import xyz.hengke.areaautominer.helper.AdvanceCoordinator;
import xyz.hengke.areaautominer.helper.BreakingHelper;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.MovementHelper;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.model.MinerMod;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.model.WalkResult;
import xyz.hengke.areaautominer.service.Messages;
import xyz.hengke.areaautominer.service.NotificationService;
import xyz.hengke.areaautominer.state.BreakingState;
import xyz.hengke.areaautominer.state.RegionState;
import xyz.hengke.areaautominer.state.SessionState;
import xyz.hengke.areaautominer.state.StateResetter;
import xyz.hengke.areaautominer.state.TraversalState;

/**
 * 挖掘状态机驱动：每 tick 分发到当前状态。
 * 依赖注入由 MinerComponents 完成；会话归零由 StateResetter.resetAll() 完成，
 * 会话收尾统一走 SessionLifecycle.teardown()。
 *
 * <p>方案 1A：执行层（MovementHelper/BreakingHelper）返回结果信号，
 * 推进/跳过/完成由本类经 {@link AdvanceCoordinator} 统一执行。</p>
 */
public class MiningController {
    private final MinecraftClient client;
    private final SessionState session;
    private final RegionState region;
    private final TraversalState traversal;
    private final BreakingState breaking;
    private final StateResetter stateResetter;
    private final MiningConfig config;
    private final BlockFinder blockFinder;
    private final MovementHelper movementHelper;
    private final CameraHelper cameraHelper;
    private final BreakingHelper breakingHelper;
    private final NotificationService notificationService;
    private final SessionLifecycle lifecycle;
    private final AdvanceCoordinator advanceCoordinator;

    /** 上一 tick 的状态（方案 C2：BREAKING 状态转移检测用，进入时初始化挖掘会话） */
    private MiningState lastState = MiningState.IDLE;

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
        this.breaking = breaking;
        this.stateResetter = stateResetter;
        this.config = config;
        this.blockFinder = blockFinder;
        this.movementHelper = movementHelper;
        this.cameraHelper = cameraHelper;
        this.breakingHelper = breakingHelper;
        this.notificationService = notificationService;
        this.lifecycle = lifecycle;
        this.advanceCoordinator = advanceCoordinator;
    }

    public void startMining(BlockPos p1, BlockPos p2) {
        if (isMining()) return;
        if (p1 == null || p2 == null) {
            notificationService.sendMessage(Messages.NEED_SELECT_REGION);
            return;
        }

        // 会话级归零：清掉上一轮的所有残留状态（遍历游标/转向/行走/挖掘）
        stateResetter.resetAll();
        lastState = MiningState.IDLE;

        region.setRegion(p1, p2);
        session.setMining(true);

        if (config.minerMod == MinerMod.FROM_TOP_DOWN) {
            traversal.setCurrentY(region.getMaxY());
        } else {
            traversal.setCurrentY(region.getMinY());
        }
        traversal.setCurrentX(region.getMinX());
        traversal.setCurrentZ(region.getMinZ());
        session.setState(MiningState.FINDING_BLOCK);

        notificationService.sendMessage(Messages.START_MINING);
        notificationService.logDebug("开始挖掘区域");
    }

    /** 主动停止（快捷键/断线）：提示统一为「停止挖掘」 */
    public void stopMining() {
        stopMining(Messages.STOP_MINING);
    }

    /**
     * 以指定原因文案停止挖掘：由调用方传入原因（如玩家死亡/界面打开），
     * 消息只发一条，避免「停止挖掘 + 原因」两条叠加；未在挖掘时幂等返回。
     */
    public void stopMining(String stopReason) {
        if (!isMining()) return;

        lifecycle.teardown();

        notificationService.sendMessage(stopReason);
        notificationService.logDebug(stopReason);
    }

    public boolean isMining() {
        return session.isMining();
    }

    /** 返回当前遍历目标方块（未在挖掘时返回 null），供渲染层高亮显示 */
    public BlockPos getCurrentTargetPos() {
        if (!isMining()) return null;
        return traversal.getPosition();
    }

    /** 每帧视角推进（LoD-3：供渲染回调注册帧级平滑，替代暴露内部 getCameraHelper） */
    public void onRenderFrame() {
        cameraHelper.smoothFrame();
    }

    public void tick() {
        if (!isMining() || client.player == null || client.interactionManager == null || client.world == null) {
            return;
        }

        MiningState state = session.getState();
        switch (state) {
            case FINDING_BLOCK:
                blockFinder.findNext();
                break;

            case WALKING_TO_BLOCK:
                BlockPos walkTarget = traversal.getPosition();
                if (movementHelper.walkToBlock(walkTarget) == WalkResult.SKIPPED
                        && advanceCoordinator.advanceAfterSkipped(walkTarget)) {
                    session.setState(MiningState.FINDING_BLOCK);
                }
                break;

            case FACING_BLOCK:
                cameraHelper.faceBlock();
                break;

            case BREAKING:
                // 方案 C2：进入 BREAKING 状态时统一初始化挖掘会话（首次进入，非重入），
                // 消除 CameraHelper.finishFacing 与 BlockFinder.findNext 两处跨域写入
                if (lastState != MiningState.BREAKING) {
                    breaking.beginBreakSession();
                }
                BlockPos breakTarget = traversal.getPosition();
                switch (breakingHelper.startBreaking(breakTarget)) {
                    case MINED:
                        if (advanceCoordinator.advanceAfterMined(breakTarget)) {
                            session.setState(MiningState.FINDING_BLOCK);
                        }
                        break;
                    case SKIPPED:
                        if (advanceCoordinator.advanceAfterSkipped(breakTarget)) {
                            session.setState(MiningState.FINDING_BLOCK);
                        }
                        break;
                    case EXTERNALLY_REMOVED:
                        if (advanceCoordinator.advanceSilently()) {
                            session.setState(MiningState.FINDING_BLOCK);
                        }
                        break;
                    default:
                        break;
                }
                break;

            case IDLE:
            default:
                break;
        }
        lastState = session.getState();
    }
}
