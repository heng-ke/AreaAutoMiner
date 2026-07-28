package xyz.hengke.areaautominer.finder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.helper.AreaIterator;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.SpatialHelper;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;

public class BlockFinder {
    private final MiningContext context;
    private final AreaIterator areaIterator;
    private final CameraHelper cameraHelper;
    private final NotificationService notificationService;
    private final MiningCompletionService completionService;

    public BlockFinder(MiningContext context, AreaIterator areaIterator, CameraHelper cameraHelper, NotificationService notificationService, MiningCompletionService completionService) {
        this.context = context;
        this.areaIterator = areaIterator;
        this.cameraHelper = cameraHelper;
        this.notificationService = notificationService;
        this.completionService = completionService;
    }

    public void findNext() {
        MiningConfig config = MiningConfig.getInstance();
        MinecraftClient client = context.client;
        BlockPos targetPos = areaIterator.getCurrentPos();

        int airSkipCount = 0;
        while (client.world.getBlockState(targetPos).isAir()) {
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            targetPos = areaIterator.getCurrentPos();
            airSkipCount++;
            if (airSkipCount >= config.getMaxAirSkipPerTick()) {
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

        boolean withinHorizontalRange = horizontalDistanceSquared <= config.getMaxReachSquared();
        boolean withinVerticalRange = verticalDistance <= config.getMaxVerticalDistance();

        if (!withinHorizontalRange || !withinVerticalRange) {
            startWalkingToBlock();
            notificationService.logDebug("超出挖掘范围，开始行走");
            return;
        }

        if (!SpatialHelper.hasLineOfSightToAnyFace(client, targetPos)) {
            startWalkingToBlock();
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

    private void startWalkingToBlock() {
        MinecraftClient client = context.client;
        context.walkTicks = 0;
        context.stuckCounter = 0;
        context.walkRetryCount = 0;
        context.lastPlayerX = client.player.getX();
        context.lastPlayerZ = client.player.getZ();
        context.state = MiningState.WALKING_TO_BLOCK;
    }
}