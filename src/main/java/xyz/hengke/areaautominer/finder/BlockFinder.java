package xyz.hengke.areaautominer.finder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.helper.AreaIterator;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.InputHelper;
import xyz.hengke.areaautominer.helper.SpatialHelper;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.NotificationService;

public class BlockFinder {
    private final MiningContext context;
    private final AreaIterator areaIterator;
    private final CameraHelper cameraHelper;
    private final InputHelper inputHelper;
    private final NotificationService notificationService;

    public BlockFinder(MiningContext context, AreaIterator areaIterator, CameraHelper cameraHelper, InputHelper inputHelper, NotificationService notificationService) {
        this.context = context;
        this.areaIterator = areaIterator;
        this.cameraHelper = cameraHelper;
        this.inputHelper = inputHelper;
        this.notificationService = notificationService;
    }

    public void findNext(MinecraftClient client, int minX, int maxX, int minY, int minZ, int maxZ) {
        BlockPos targetPos = areaIterator.getCurrentPos();

        int airSkipCount = 0;
        while (client.world.getBlockState(targetPos).isAir()) {
            if (!areaIterator.advancePosition(minX, maxX, minY, minZ, maxZ)) {
                stopMining();
                return;
            }
            targetPos = areaIterator.getCurrentPos();
            airSkipCount++;
            if (airSkipCount >= MiningConfig.MAX_AIR_SKIP_PER_TICK) {
                return;
            }
        }

        double targetX = context.currentX + 0.5;
        double targetY = context.currentY + 0.5;
        double targetZ = context.currentZ + 0.5;

        double playerX = client.player.getX();
        double playerY = client.player.getY() + client.player.getEyeHeight(client.player.getPose());
        double playerZ = client.player.getZ();

        double horizontalDistanceSquared = SpatialHelper.calculateHorizontalDistanceSquared(playerX, playerZ, targetX, targetZ);
        double verticalDistance = Math.abs(playerY - targetY);

        boolean withinHorizontalRange = horizontalDistanceSquared <= MiningConfig.MAX_REACH_SQUARED;
        boolean withinVerticalRange = verticalDistance <= MiningConfig.MAX_VERTICAL_DISTANCE;

        if (!withinHorizontalRange || !withinVerticalRange) {
            startWalkingToBlock(client);
            notificationService.logDebug("超出挖掘范围，开始行走");
            return;
        }

        if (!SpatialHelper.hasLineOfSight(client, targetPos)) {
            startWalkingToBlock(client);
            notificationService.logDebug("无视线，开始行走");
            return;
        }

        cameraHelper.calculateTargetLook(targetPos);

        context.isAdjacentBlock = SpatialHelper.isAdjacentToLast(context, targetPos);

        float currentYaw = client.player.getYaw();
        float yawDiff = SpatialHelper.normalizeYawDiff(context.targetYaw - currentYaw);
        float pitchDiff = Math.abs(context.targetPitch - client.player.getPitch());

        if (Math.abs(yawDiff) < 5.0F && pitchDiff < 5.0F) {
            context.firstBreakTick = true;
            context.state = MiningState.BREAKING;
            notificationService.logDebug("已对准，直接挖掘");
            return;
        }

        context.waitTicks = cameraHelper.calculateDynamicWaitTicks(yawDiff, pitchDiff);
        context.facingRetryCount = 0;
        context.state = MiningState.FACING_BLOCK;
        notificationService.logDebug("开始转向，需要转动: " + Math.round(Math.abs(yawDiff)) + "度，等待: " + context.waitTicks + "tick");
    }

    private void startWalkingToBlock(MinecraftClient client) {
        context.walkTicks = 0;
        context.stuckCounter = 0;
        context.walkRetryCount = 0;
        context.lastPlayerX = client.player.getX();
        context.lastPlayerZ = client.player.getZ();
        context.state = MiningState.WALKING_TO_BLOCK;
    }

    private void stopMining() {
        context.isMining = false;
        context.state = MiningState.IDLE;
        inputHelper.releaseAllKeys();
        notificationService.sendMessage(net.minecraft.text.Text.literal("§a挖掘完成！"));
        if (context.listener != null) {
            context.listener.onStopMining();
        }
    }
}