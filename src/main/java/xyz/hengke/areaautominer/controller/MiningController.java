package xyz.hengke.areaautominer.controller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.finder.BlockFinder;
import xyz.hengke.areaautominer.helper.BreakingHelper;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.MovementHelper;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.listener.MiningListener;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.model.MinerMod;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.Messages;
import xyz.hengke.areaautominer.service.NotificationService;

import java.util.Iterator;
import java.util.Set;

/**
 * 挖掘状态机驱动：每 tick 分发到当前状态。
 * 依赖注入由 MinerComponents 完成；会话归零由 MiningContext.resetAll() 完成，
 * 会话收尾统一走 SessionLifecycle.teardown()。
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

    public MiningController(MiningContext context, MiningConfig config, BlockFinder blockFinder,
                            MovementHelper movementHelper, CameraHelper cameraHelper, BreakingHelper breakingHelper,
                            NotificationService notificationService, SessionLifecycle lifecycle) {
        this.context = context;
        this.config = config;
        this.blockFinder = blockFinder;
        this.movementHelper = movementHelper;
        this.cameraHelper = cameraHelper;
        this.breakingHelper = breakingHelper;
        this.notificationService = notificationService;
        this.lifecycle = lifecycle;
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

    public void stopMining() {
        lifecycle.teardown();

        notificationService.sendMessage(Messages.STOP_MINING);
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

    /** 返回视角控制器,供渲染回调注册帧级平滑(WorldRenderEvents.BEFORE_TERRAIN) */
    public CameraHelper getCameraHelper() {
        return cameraHelper;
    }

    public void tick() {
        MinecraftClient client = context.getClient();
        if (!context.session().isMining() || client.player == null || client.interactionManager == null || client.world == null) {
            return;
        }

        if (config.enableRollbackDetection) {
            context.rollback().setRollbackCheckTimer(context.rollback().getRollbackCheckTimer() + 1);

            if (context.rollback().getRollbackCheckTimer() >= config.rollbackCheckInterval) {
                context.rollback().setRollbackCheckTimer(0);
                checkRollback();
            }
        }

        switch (context.session().getState()) {
            case FINDING_BLOCK:
                blockFinder.findNext();
                break;

            case WALKING_TO_BLOCK:
                movementHelper.walkToBlock();
                break;

            case FACING_BLOCK:
                cameraHelper.faceBlock();
                break;

            case BREAKING:
                breakingHelper.startBreaking();
                break;

            case IDLE:
            default:
                break;
        }
    }

    private void checkRollback() {
        // 使用 rollbackScanCount（扫描计数）替代 rollbackRetryCount（完成重试计数），二者独立
        if (context.rollback().getRollbackScanCount() >= config.maxRollbackRetries) {
            return;
        }

        MinecraftClient client = context.getClient();
        Set<BlockPos> minedPositions = context.rollback().getMinedPositions();
        Iterator<BlockPos> iterator = minedPositions.iterator();

        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!client.world.getBlockState(pos).isAir()) {
                // 保存主遍历中断点，供挖完回滚方块后由 AreaIterator.advancePosition 自动恢复
                context.rollback().setRollbackResumePos(new BlockPos(
                        context.traversal().getCurrentX(), context.traversal().getCurrentY(), context.traversal().getCurrentZ()));
                context.traversal().setCurrentX(pos.getX());
                context.traversal().setCurrentY(pos.getY());
                context.traversal().setCurrentZ(pos.getZ());
                context.rollback().setRollbackScanCount(context.rollback().getRollbackScanCount() + 1);
                context.session().setState(MiningState.FINDING_BLOCK);
                notificationService.sendMessage(Messages.ROLLBACK_DETECTED + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                return;
            }
            iterator.remove();
        }
    }
}
