package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
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
    private final InputHelper inputHelper;

    public BreakingHelper(MiningContext context, AreaIterator areaIterator, NotificationService notificationService, MiningCompletionService completionService, InputHelper inputHelper) {
        this.context = context;
        this.areaIterator = areaIterator;
        this.notificationService = notificationService;
        this.completionService = completionService;
        this.inputHelper = inputHelper;
    }

    // 挖掘时视角重对准阈值（度），偏差超过此值则中断挖掘重新转向
    private static final float FACING_RE_THRESHOLD_DEGREES = 15.0F;

    public void startBreaking() {
        MiningConfig config = MiningConfig.getInstance();
        MinecraftClient client = context.getClient();
        BlockPos targetPos = areaIterator.getCurrentPos();

        BlockPos playerPos = new BlockPos(
            (int) Math.floor(client.player.getX()),
            (int) Math.floor(client.player.getY()),
            (int) Math.floor(client.player.getZ())
        );
        if (client.world.getFluidState(playerPos.down()).isIn(FluidTags.LAVA)) {
            notificationService.sendMessage("§c检测到玩家脚下是岩浆，停止挖掘");
            completionService.onBlockSkipped(targetPos);
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.setState(MiningState.FINDING_BLOCK);
            return;
        }

        if (client.world.getBlockState(targetPos).isAir()) {
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.setState(MiningState.FINDING_BLOCK);
            return;
        }

        double targetX = context.getCurrentX() + 0.5;
        double targetY = context.getCurrentY() + 0.5;
        double targetZ = context.getCurrentZ() + 0.5;
        double playerX = client.player.getX();
        double playerY = client.player.getY() + client.player.getEyeHeight(client.player.getPose());
        double playerZ = client.player.getZ();
        double horizontalDistanceSquared = SpatialHelper.calculateHorizontalDistanceSquared(playerX, playerZ, targetX, targetZ);
        double verticalDistance = Math.abs(playerY - targetY);

        boolean withinHorizontalRange = horizontalDistanceSquared <= config.getMaxReachSquared();
        boolean withinVerticalRange = verticalDistance <= config.getMaxVerticalDistance();

        if (!withinHorizontalRange || !withinVerticalRange || !SpatialHelper.hasLineOfSightToAnyFace(client, targetPos)) {
            context.startWalkingToBlock();
            notificationService.logDebug("挖掘时超出范围或无视线，重新行走");
            return;
        }

        float currentYaw = client.player.getYaw();
        float yawDiff = SpatialHelper.normalizeYawDiff(context.getTargetYaw() - currentYaw);
        float pitchDiff = Math.abs(context.getTargetPitch() - client.player.getPitch());

        if (Math.abs(yawDiff) > FACING_RE_THRESHOLD_DEGREES || pitchDiff > FACING_RE_THRESHOLD_DEGREES) {
            // 不设 waitTicks/initialWaitTicks：让 faceBlock 的 initTurningParameters 自动处理，
            // 避免 faceStartYaw 过时导致跳变（initTurningParameters 会根据偏差自适应 waitTicks）
            context.setAdjacentBlock(true);
            context.setState(MiningState.FACING_BLOCK);
            notificationService.logDebug("挖掘时视角偏移过大，重新转向");
            return;
        }

        context.setBreakTicks(context.getBreakTicks() + 1);
        if (context.getBreakTicks() > config.getMaxBreakTicks()) {
            completionService.onBlockSkipped(targetPos);
            context.setBreakTicks(0);
            context.setFirstBreakTick(true);
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.setState(MiningState.FINDING_BLOCK);
            return;
        }

        GameMode gameMode = client.interactionManager.getCurrentGameMode();

        if (gameMode == GameMode.CREATIVE) {
            client.interactionManager.breakBlock(targetPos);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            completionService.onBlockMined(targetPos);
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.setState(MiningState.FINDING_BLOCK);
        } else {
            ItemStack toolStack = context.getClient().player.getStackInHand(net.minecraft.util.Hand.MAIN_HAND);
            if (toolStack.isDamageable() && toolStack.getMaxDamage() > 0) {
                int currentDurability = toolStack.getMaxDamage() - toolStack.getDamage();
                if (currentDurability < MiningConfig.getInstance().getMinToolDurability()) {
                    notificationService.sendMessage("§c工具耐久不足（剩余 " + currentDurability + " 点），暂停挖掘");
                    inputHelper.releaseAllKeys();
                    context.setState(MiningState.IDLE);
                    return;
                }
            }

            var direction = SpatialHelper.calculateDirection(client, targetPos);

            if (context.isFirstBreakTick()) {
                client.interactionManager.attackBlock(targetPos, direction);
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                context.setFirstBreakTick(false);
            }

            client.interactionManager.updateBlockBreakingProgress(targetPos, direction);

            if (context.getBreakTicks() % 6 == 0) {
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }

            if (client.world.getBlockState(targetPos).isAir()) {
                completionService.onBlockMined(targetPos);
                context.setBreakTicks(0);
                if (!areaIterator.advancePosition()) {
                    completionService.completeMining();
                    return;
                }
                context.setState(MiningState.FINDING_BLOCK);
                notificationService.logDebug("方块挖掘完成");
            }
        }
    }
}