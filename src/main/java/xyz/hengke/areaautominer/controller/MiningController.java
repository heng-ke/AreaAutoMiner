package xyz.hengke.areaautominer.controller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.finder.BlockFinder;
import xyz.hengke.areaautominer.helper.AdvanceCoordinator;
import xyz.hengke.areaautominer.helper.BreakingHelper;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.MovementHelper;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.listener.MiningListener;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.model.BreakOutcome;
import xyz.hengke.areaautominer.model.MinerMod;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.model.WalkResult;
import xyz.hengke.areaautominer.service.Messages;
import xyz.hengke.areaautominer.service.NotificationService;
import xyz.hengke.areaautominer.service.RollbackDetector;

/**
 * 挖掘状态机驱动：每 tick 分发到当前状态。
 * 依赖注入由 MinerComponents 完成；会话归零由 MiningContext.resetAll() 完成，
 * 会话收尾统一走 SessionLifecycle.teardown()。
 *
 * <p>方案 1A：执行层（MovementHelper/BreakingHelper）返回结果信号，
 * 推进/跳过/完成由本类经 {@link AdvanceCoordinator} 统一执行。
 * 方案 3：回滚检测职责移至 {@link RollbackDetector}，本类仅做状态机调度。</p>
 */
public class MiningController {
    private final MiningContext context;
    private final MiningConfig config;
    private final BlockFinder blockFinder;
    private final MovementHelper movementHelper;
    private final CameraHelper cameraHelper;
    private final BreakingHelper breakingHelper;
    private final NotificationService notificationService;
    private final SessionLifecycle lifecycle;
    private final AdvanceCoordinator advanceCoordinator;
    private final RollbackDetector rollbackDetector;

    /** 上一 tick 的状态（方案 C2：BREAKING 状态转移检测用，进入时初始化挖掘会话） */
    private MiningState lastState = MiningState.IDLE;

    public MiningController(MiningContext context, MiningConfig config, BlockFinder blockFinder,
                            MovementHelper movementHelper, CameraHelper cameraHelper, BreakingHelper breakingHelper,
                            NotificationService notificationService, SessionLifecycle lifecycle,
                            AdvanceCoordinator advanceCoordinator, RollbackDetector rollbackDetector) {
        this.context = context;
        this.config = config;
        this.blockFinder = blockFinder;
        this.movementHelper = movementHelper;
        this.cameraHelper = cameraHelper;
        this.breakingHelper = breakingHelper;
        this.notificationService = notificationService;
        this.lifecycle = lifecycle;
        this.advanceCoordinator = advanceCoordinator;
        this.rollbackDetector = rollbackDetector;
    }

    public void setListener(MiningListener listener) {
        this.context.session().setListener(listener);
    }

    public void startMining(BlockPos p1, BlockPos p2) {
        if (context.session().isMining()) return;
        if (p1 == null || p2 == null) {
            notificationService.sendMessage(Messages.NEED_SELECT_REGION);
            return;
        }

        // 会话级归零：清掉上一轮的所有残留状态（遍历游标/转向/行走/挖掘/回滚）
        context.resetAll();
        lastState = MiningState.IDLE;

        context.region().setRegion(p1, p2);
        context.session().setMining(true);

        if (config.minerMod == MinerMod.FROM_TOP_DOWN) {
            context.traversal().setCurrentY(context.region().getMaxY());
        } else {
            context.traversal().setCurrentY(context.region().getMinY());
        }
        context.traversal().setCurrentX(context.region().getMinX());
        context.traversal().setCurrentZ(context.region().getMinZ());
        context.session().setState(MiningState.FINDING_BLOCK);

        notificationService.sendMessage(Messages.START_MINING);
        if (context.session().getListener() != null) {
            context.session().getListener().onStartMining(p1, p2);
        }
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
        if (!context.session().isMining()) return;

        lifecycle.teardown();

        notificationService.sendMessage(stopReason);
        if (context.session().getListener() != null) {
            context.session().getListener().onStopMining();
        }
        notificationService.logDebug("停止挖掘");
    }

    public boolean isMining() {
        return context.session().isMining();
    }

    /** 返回当前遍历目标方块（未在挖掘时返回 null），供渲染层高亮显示 */
    public BlockPos getCurrentTargetPos() {
        if (!context.session().isMining()) return null;
        return context.traversal().getPosition();
    }

    /** 每帧视角推进（LoD-3：供渲染回调注册帧级平滑，替代暴露内部 getCameraHelper） */
    public void onRenderFrame() {
        cameraHelper.smoothFrame();
    }

    public void tick() {
        MinecraftClient client = context.getClient();
        if (!context.session().isMining() || client.player == null || client.interactionManager == null || client.world == null) {
            return;
        }

        // === 回滚检测（方案 3 + B1：检测在 RollbackDetector，响应在本类统一执行）===
        rollbackDetector.tick().ifPresent(this::respondToRollback);

        MiningState state = context.session().getState();
        switch (state) {
            case FINDING_BLOCK:
                blockFinder.findNext();
                break;

            case WALKING_TO_BLOCK:
                BlockPos walkTarget = context.traversal().getPosition();
                if (movementHelper.walkToBlock(walkTarget) == WalkResult.SKIPPED
                        && advanceCoordinator.advanceAfterSkipped(walkTarget)) {
                    context.session().setState(MiningState.FINDING_BLOCK);
                }
                break;

            case FACING_BLOCK:
                cameraHelper.faceBlock();
                break;

            case BREAKING:
                // 方案 C2：进入 BREAKING 状态时统一初始化挖掘会话（首次进入，非重入），
                // 消除 CameraHelper.finishFacing 与 BlockFinder.findNext 两处跨域写入
                if (lastState != MiningState.BREAKING) {
                    context.breaking().beginBreakSession();
                }
                BlockPos breakTarget = context.traversal().getPosition();
                switch (breakingHelper.startBreaking(breakTarget)) {
                    case MINED:
                        if (advanceCoordinator.advanceAfterMined(breakTarget)) {
                            context.session().setState(MiningState.FINDING_BLOCK);
                        }
                        break;
                    case SKIPPED:
                        if (advanceCoordinator.advanceAfterSkipped(breakTarget)) {
                            context.session().setState(MiningState.FINDING_BLOCK);
                        }
                        break;
                    case EXTERNALLY_REMOVED:
                        if (advanceCoordinator.advanceSilently()) {
                            context.session().setState(MiningState.FINDING_BLOCK);
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
        lastState = context.session().getState();
    }

    /**
     * 回滚响应（方案 B1）：检测到已挖方块被重新填充后，跳回重挖。
     * 保存主遍历中断点（供挖完回滚方块后由 AdvanceCoordinator 的恢复点逻辑跳回）→
     * 游标跳回回滚方块 → 递增扫描计数 → 状态机回 FINDING_BLOCK → 玩家通知。
     */
    private void respondToRollback(BlockPos pos) {
        context.rollback().setRollbackResumePos(new BlockPos(
                context.traversal().getCurrentX(), context.traversal().getCurrentY(), context.traversal().getCurrentZ()));
        context.traversal().setCurrentX(pos.getX());
        context.traversal().setCurrentY(pos.getY());
        context.traversal().setCurrentZ(pos.getZ());
        context.rollback().setRollbackScanCount(context.rollback().getRollbackScanCount() + 1);
        context.session().setState(MiningState.FINDING_BLOCK);
        notificationService.sendMessage(String.format(Messages.ROLLBACK_DETECTED, pos.getX(), pos.getY(), pos.getZ()));
    }
}
