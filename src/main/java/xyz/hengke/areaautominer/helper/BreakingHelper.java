package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.NotificationService;

public class BreakingHelper {
    private final MiningContext context;
    private final InputHelper inputHelper;
    private final AreaIterator areaIterator;
    private final NotificationService notificationService;

    public BreakingHelper(MiningContext context, InputHelper inputHelper, AreaIterator areaIterator, NotificationService notificationService) {
        this.context = context;
        this.inputHelper = inputHelper;
        this.areaIterator = areaIterator;
        this.notificationService = notificationService;
    }

    public void startBreaking(int minX, int maxX, int minY, int minZ, int maxZ) {
        MinecraftClient client = context.client;
        BlockPos targetPos = areaIterator.getCurrentPos();

        if (client.world.getBlockState(targetPos).isAir()) {
            if (!areaIterator.advancePosition(minX, maxX, minY, minZ, maxZ)) {
                stopMining();
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

        boolean withinHorizontalRange = horizontalDistanceSquared <= MiningConfig.MAX_REACH_SQUARED;
        boolean withinVerticalRange = verticalDistance <= MiningConfig.MAX_VERTICAL_DISTANCE;

        if (!withinHorizontalRange || !withinVerticalRange || !SpatialHelper.hasLineOfSight(client, targetPos)) {
            startWalkingToBlock(client);
            notificationService.logDebug("挖掘时超出范围或无视线，重新行走");
            return;
        }

        float currentYaw = client.player.getYaw();
        float yawDiff = SpatialHelper.normalizeYawDiff(context.targetYaw - currentYaw);
        float pitchDiff = Math.abs(context.targetPitch - client.player.getPitch());

        if (Math.abs(yawDiff) > 15.0F || pitchDiff > 15.0F) {
            context.waitTicks = MiningConfig.SHORT_FACING_WAIT_TICKS;
            context.isAdjacentBlock = true;
            context.state = MiningState.FACING_BLOCK;
            notificationService.logDebug("挖掘时视角偏移过大，重新转向");
            return;
        }

        context.breakTicks++;
        if (context.breakTicks > MiningConfig.MAX_BREAK_TICKS) {
            notificationService.onBlockSkipped(targetPos);
            context.breakTicks = 0;
            if (!areaIterator.advancePosition(minX, maxX, minY, minZ, maxZ)) {
                stopMining();
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
            notificationService.onBlockMined(targetPos);
            if (!areaIterator.advancePosition(minX, maxX, minY, minZ, maxZ)) {
                stopMining();
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
                notificationService.onBlockMined(targetPos);
                context.breakTicks = 0;
                if (!areaIterator.advancePosition(minX, maxX, minY, minZ, maxZ)) {
                    stopMining();
                    return;
                }
                context.state = MiningState.FINDING_BLOCK;
                notificationService.logDebug("方块挖掘完成");
            }
        }
    }

    private void startWalkingToBlock(MinecraftClient client) {
        context.walkTicks = 0;
        context.stuckCounter = 0;
        context.lastPlayerX = client.player.getX();
        context.lastPlayerZ = client.player.getZ();
        context.state = MiningState.WALKING_TO_BLOCK;
    }

    private void stopMining() {
        context.state = MiningState.IDLE;
        inputHelper.releaseAllKeys();
        notificationService.onMineComplete();
    }
}