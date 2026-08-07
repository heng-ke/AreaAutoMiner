package xyz.hengke.areaautominer.controller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.finder.BlockFinder;
import xyz.hengke.areaautominer.helper.AreaIterator;
import xyz.hengke.areaautominer.helper.BreakingHelper;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.InputHelper;
import xyz.hengke.areaautominer.helper.MovementHelper;
import xyz.hengke.areaautominer.helper.PathfindingHelper;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.listener.MiningListener;
import xyz.hengke.areaautominer.model.MinerMod;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;

import java.util.Iterator;
import java.util.Set;

public class MiningController {
    private final MiningContext context;
    private final InputHelper inputHelper;
    private final CameraHelper cameraHelper;
    private final MovementHelper movementHelper;
    private final BreakingHelper breakingHelper;
    private final BlockFinder blockFinder;
    private final NotificationService notificationService;
    private final PathfindingHelper pathfindingHelper;

    public MiningController(MinecraftClient client) {
        this.context = new MiningContext(client);
        this.inputHelper = new InputHelper(context);
        this.notificationService = new NotificationService(context);
        var completionService = new MiningCompletionService(context, inputHelper, notificationService);
        this.cameraHelper = new CameraHelper(context, inputHelper, notificationService);
        var areaIterator = new AreaIterator(context);
        this.pathfindingHelper = new PathfindingHelper(context, notificationService);
        this.breakingHelper = new BreakingHelper(context, areaIterator, notificationService, completionService, inputHelper);
        this.movementHelper = new MovementHelper(context, inputHelper, cameraHelper, areaIterator, notificationService, completionService, pathfindingHelper);
        this.blockFinder = new BlockFinder(context, areaIterator, cameraHelper, notificationService, completionService);
    }

    public void setListener(MiningListener listener) {
        this.context.setListener(listener);
    }

    public void startMining(BlockPos p1, BlockPos p2) {
        if (context.isMining()) return;
        if (p1 == null || p2 == null) {
            notificationService.sendMessage("§c请先选择区域！");
            return;
        }

        context.setRegion(p1, p2);
        context.clearMinedPositions();
        context.setMining(true);

        MiningConfig miningConfig = MiningConfig.getInstance();
        if (miningConfig.getMinerMod() == MinerMod.FROM_TOP_DOWN) {
            context.setCurrentY(context.getMaxY());
        } else {
            context.setCurrentY(context.getMinY());
        }
        context.setCurrentX(context.getMinX());
        context.setCurrentZ(context.getMinZ());
        context.setLastMinedPos(null);
        context.setAdjacentBlock(false);
        context.setMovingWait(false);
        context.setWalkTicks(0);
        context.setStuckCounter(0);
        context.setBreakTicks(0);
        context.setLastPlayerX(0);
        context.setLastPlayerZ(0);
        context.setWalkRetryCount(0);
        context.setJumpCooldown(0);
        context.setRollbackRetryCount(0);
        context.setRollbackCheckTimer(0);
        context.setRollbackScanY(context.getMinY());
        context.setState(MiningState.FINDING_BLOCK);

        notificationService.sendMessage("§a开始挖掘区域");
        if (context.getListener() != null) {
            context.getListener().onStartMining(p1, p2);
        }
        notificationService.logDebug("开始挖掘区域");
    }

    public void stopMining() {
        context.setMining(false);
        context.setState(MiningState.IDLE);
        inputHelper.releaseAllKeys();
        pathfindingHelper.cleanup();

        notificationService.sendMessage("§c停止挖掘");
        if (context.getListener() != null) {
            context.getListener().onStopMining();
        }
        notificationService.logDebug("停止挖掘");
    }

    public boolean isMining() {
        return context.isMining();
    }

    public void tick() {
        MinecraftClient client = context.getClient();
        if (!context.isMining() || client.player == null || client.interactionManager == null || client.world == null) {
            return;
        }

        MiningConfig config = MiningConfig.getInstance();

        if (config.isRollbackDetectionEnabled()) {
            context.setRollbackCheckTimer(context.getRollbackCheckTimer() + 1);

            if (context.getRollbackCheckTimer() >= config.getRollbackCheckInterval()) {
                context.setRollbackCheckTimer(0);
                checkRollback();
            }
        }

        switch (context.getState()) {
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
        MiningConfig config = MiningConfig.getInstance();

        if (context.getRollbackRetryCount() >= config.getMaxRollbackRetries()) {
            return;
        }

        MinecraftClient client = context.getClient();
        Set<BlockPos> minedPositions = context.getMinedPositions();
        Iterator<BlockPos> iterator = minedPositions.iterator();

        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!client.world.getBlockState(pos).isAir()) {
                // 保存主遍历中断点，供挖完回滚方块后由 AreaIterator.advancePosition 自动恢复
                context.setRollbackResumePos(new BlockPos(
                        context.getCurrentX(), context.getCurrentY(), context.getCurrentZ()));
                context.setCurrentX(pos.getX());
                context.setCurrentY(pos.getY());
                context.setCurrentZ(pos.getZ());
                context.setRollbackRetryCount(context.getRollbackRetryCount() + 1);
                context.setState(MiningState.FINDING_BLOCK);
                notificationService.sendMessage("§e检测到回滚，重新挖掘位置: " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                return;
            }
            iterator.remove();
        }
    }
}