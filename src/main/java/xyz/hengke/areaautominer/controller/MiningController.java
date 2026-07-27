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
import xyz.hengke.areaautominer.listener.MiningListener;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;

public class MiningController {
    private final MiningContext context;
    private final InputHelper inputHelper;
    private final CameraHelper cameraHelper;
    private final MovementHelper movementHelper;
    private final BreakingHelper breakingHelper;
    private final BlockFinder blockFinder;
    private final NotificationService notificationService;

    public MiningController(MinecraftClient client) {
        this.context = new MiningContext(client);
        this.inputHelper = new InputHelper(context);
        this.notificationService = new NotificationService(context);
        var completionService = new MiningCompletionService(context, inputHelper, notificationService);
        this.cameraHelper = new CameraHelper(context, inputHelper, notificationService);
        var areaIterator = new AreaIterator(context);
        this.breakingHelper = new BreakingHelper(context, areaIterator, notificationService, completionService);
        this.movementHelper = new MovementHelper(context, inputHelper, cameraHelper, areaIterator, notificationService, completionService);
        this.blockFinder = new BlockFinder(context, areaIterator, cameraHelper, notificationService, completionService);
    }

    public void setListener(MiningListener listener) {
        this.context.listener = listener;
    }

    public void startMining(BlockPos p1, BlockPos p2) {
        if (context.isMining) return;
        MinecraftClient client = context.client;
        if (p1 == null || p2 == null) {
            notificationService.sendMessage("§c请先选择区域！");
            return;
        }

        context.pos1 = p1;
        context.pos2 = p2;
        context.isMining = true;

        int maxY = Math.max(p1.getY(), p2.getY());
        int playerY = (int) Math.floor(client.player.getY());
        context.currentY = Math.min(maxY, playerY + 1);
        context.currentX = Math.min(p1.getX(), p2.getX());
        context.currentZ = Math.min(p1.getZ(), p2.getZ());
        context.lastMinedPos = null;
        context.isAdjacentBlock = false;
        context.movingWait = false;
        context.walkTicks = 0;
        context.stuckCounter = 0;
        context.breakTicks = 0;
        context.lastPlayerX = 0;
        context.lastPlayerZ = 0;
        context.walkRetryCount = 0;
        context.jumpCooldown = 0;
        context.state = MiningState.FINDING_BLOCK;

        notificationService.sendMessage("§a开始挖掘区域");
        if (context.listener != null) {
            context.listener.onStartMining(p1, p2);
        }
        notificationService.logDebug("开始挖掘区域");
    }

    public void stopMining() {
        context.isMining = false;
        context.state = MiningState.IDLE;
        inputHelper.releaseAllKeys();

        notificationService.sendMessage("§c停止挖掘");
        if (context.listener != null) {
            context.listener.onStopMining();
        }
        notificationService.logDebug("停止挖掘");
    }

    public boolean isMining() {
        return context.isMining;
    }

    public void tick() {
        MinecraftClient client = context.client;
        if (!context.isMining || client.player == null || client.interactionManager == null || client.world == null) {
            return;
        }

        int minX = Math.min(context.pos1.getX(), context.pos2.getX());
        int maxX = Math.max(context.pos1.getX(), context.pos2.getX());
        int minY = Math.min(context.pos1.getY(), context.pos2.getY());
        int minZ = Math.min(context.pos1.getZ(), context.pos2.getZ());
        int maxZ = Math.max(context.pos1.getZ(), context.pos2.getZ());

        switch (context.state) {
            case FINDING_BLOCK:
                blockFinder.findNext(client, minX, maxX, minY, minZ, maxZ);
                break;

            case WALKING_TO_BLOCK:
                movementHelper.walkToBlock(minX, maxX, minY, minZ, maxZ);
                break;

            case FACING_BLOCK:
                cameraHelper.faceBlock();
                break;

            case BREAKING:
                breakingHelper.startBreaking(minX, maxX, minY, minZ, maxZ);
                break;

            case IDLE:
            default:
                break;
        }
    }
}