package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;

public class BreakingHelper {
    private final MiningContext context;
    private final AreaIterator areaIterator;
    private final NotificationService notificationService;
    private final MiningCompletionService completionService;

    public BreakingHelper(MiningContext context, AreaIterator areaIterator, NotificationService notificationService, MiningCompletionService completionService) {
        this.context = context;
        this.areaIterator = areaIterator;
        this.notificationService = notificationService;
        this.completionService = completionService;
    }

    public void startBreaking() {
        MiningConfig config = MiningConfig.getInstance();
        MinecraftClient client = context.client;
        BlockPos targetPos = areaIterator.getCurrentPos();

        if (client.world.getBlockState(targetPos).isAir()) {
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.state = MiningState.FINDING_BLOCK;
            return;
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

        if (!withinHorizontalRange || !withinVerticalRange || !SpatialHelper.hasLineOfSightToAnyFace(client, targetPos)) {
            startWalkingToBlock();
            notificationService.logDebug("挖掘时超出范围或无视线，重新行走");
            return;
        }

        float currentYaw = client.player.getYaw();
        float yawDiff = SpatialHelper.normalizeYawDiff(context.targetYaw - currentYaw);
        float pitchDiff = Math.abs(context.targetPitch - client.player.getPitch());

        if (Math.abs(yawDiff) > 15.0F || pitchDiff > 15.0F) {
            context.waitTicks = config.getShortFacingWaitTicks();
            context.isAdjacentBlock = true;
            context.state = MiningState.FACING_BLOCK;
            notificationService.logDebug("挖掘时视角偏移过大，重新转向");
            return;
        }

        context.breakTicks++;
        if (context.breakTicks > config.getMaxBreakTicks()) {
            completionService.onBlockSkipped(targetPos);
            context.breakTicks = 0;
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.state = MiningState.FINDING_BLOCK;
            return;
        }

        var direction = SpatialHelper.calculateDirection(client, targetPos);
        GameMode gameMode = client.interactionManager.getCurrentGameMode();

        if (gameMode == GameMode.CREATIVE) {
            client.interactionManager.breakBlock(targetPos);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            completionService.onBlockMined(targetPos);
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.state = MiningState.FINDING_BLOCK;
        } else {
            if (context.firstBreakTick) {
                client.interactionManager.attackBlock(targetPos, direction);
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                context.firstBreakTick = false;
            }

            client.interactionManager.updateBlockBreakingProgress(targetPos, direction);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);

            if (client.world.getBlockState(targetPos).isAir()) {
                    completionService.onBlockMined(targetPos);
                    context.breakTicks = 0;
                    if (!areaIterator.advancePosition()) {
                        completionService.completeMining();
                        return;
                    }
                    context.state = MiningState.FINDING_BLOCK;
                    notificationService.logDebug("方块挖掘完成");
                }
        }
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