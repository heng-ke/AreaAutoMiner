package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.state.BreakingState;
import xyz.hengke.areaautominer.state.FacingState;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.model.BreakOutcome;
import xyz.hengke.areaautominer.service.NotificationService;

public class BreakingHelper {
    private static final int SWING_INTERVAL = 6;

    private final MinecraftClient client;
    private final MiningConfig config;
    private final BreakingState breaking;
    private final FacingState facing;
    private final NotificationService notificationService;
    private final CameraHelper cameraHelper;
    private final SessionLifecycle lifecycle;
    private final ToolDurabilityGuard toolDurabilityGuard;
    private final WalkRequester walkRequester;

    public BreakingHelper(MinecraftClient client, MiningConfig config,
                          BreakingState breaking, FacingState facing,
                          NotificationService notificationService,
                          CameraHelper cameraHelper, SessionLifecycle lifecycle,
                          ToolDurabilityGuard toolDurabilityGuard, WalkRequester walkRequester) {
        this.client = client;
        this.config = config;
        this.breaking = breaking;
        this.facing = facing;
        this.notificationService = notificationService;
        this.cameraHelper = cameraHelper;
        this.lifecycle = lifecycle;
        this.toolDurabilityGuard = toolDurabilityGuard;
        this.walkRequester = walkRequester;
    }

    public BreakOutcome startBreaking(BlockPos targetPos) {
        if (DangerChecker.isLavaAroundPlayer(client)) {
            notificationService.logDebug("检测到玩家周围有岩浆，跳过方块: " + targetPos);
            return BreakOutcome.SKIPPED;
        }

        if (client.world.getBlockState(targetPos).isAir()) {
            return BreakOutcome.EXTERNALLY_REMOVED;
        }

        if (client.world.getBlockState(targetPos).getHardness(client.world, targetPos) < 0) {
            notificationService.logDebug("目标方块不可破坏，跳过: " + targetPos);
            return BreakOutcome.SKIPPED;
        }

        if (!ReachChecker.isBlockWithinReach(client, targetPos, config)) {
            Vec3d playerPos = SpatialMath.getPlayerPos(client);
            if (walkRequester.requestWalkOrSkip(targetPos, playerPos.x, playerPos.z)
                    == WalkRequester.Result.SKIPPED) {
                notificationService.logDebug("目标方块多次无法到达，跳过: " + targetPos);
                return BreakOutcome.SKIPPED;
            }
            notificationService.logDebug("挖掘时超出范围或无视线，重新行走");
            return BreakOutcome.NEED_WALK;
        }

        if (isFacingDrifted()) {
            cameraHelper.beginFacing();
            notificationService.logDebug("挖掘时视角偏移过大，重新转向");
            return BreakOutcome.NEED_FACE;
        }

        breaking.setBreakTicks(breaking.getBreakTicks() + 1);
        if (breaking.getBreakTicks() > config.maxBreakTicks) {
            notificationService.logDebug("挖掘超时，跳过: " + targetPos);
            return BreakOutcome.SKIPPED;
        }

        GameMode gameMode = client.interactionManager.getCurrentGameMode();
        if (gameMode == GameMode.CREATIVE) {
            return breakBlockCreative(targetPos);
        } else {
            return breakBlockSurvival(targetPos);
        }
    }

    // ---------- 挖掘执行 ----------
    private BreakOutcome breakBlockCreative(BlockPos targetPos) {
        client.interactionManager.breakBlock(targetPos);
        client.player.swingHand(Hand.MAIN_HAND);

        if (client.world.getBlockState(targetPos).isAir()) {
            return BreakOutcome.MINED;
        } else {
            notificationService.logDebug("方块无法破坏（创造模式），跳过: " + targetPos);
            return BreakOutcome.SKIPPED;
        }
    }

    private BreakOutcome breakBlockSurvival(BlockPos targetPos) {
        if (toolDurabilityGuard.shouldPause()) {
            lifecycle.teardown();
            return BreakOutcome.ONGOING;
        }

        Direction direction = SpatialMath.calculateDirection(client, targetPos);

        if (breaking.isFirstBreakTick()) {
            client.interactionManager.attackBlock(targetPos, direction);
            client.player.swingHand(Hand.MAIN_HAND);
            breaking.setFirstBreakTick(false);
        }
        client.interactionManager.updateBlockBreakingProgress(targetPos, direction);

        if (breaking.getBreakTicks() % SWING_INTERVAL == 0) {
            client.player.swingHand(Hand.MAIN_HAND);
        }

        if (client.world.getBlockState(targetPos).isAir()) {
            return BreakOutcome.MINED;
        }
        return BreakOutcome.ONGOING;
    }

    // ---------- 工具 ----------
    private boolean isFacingDrifted() {
        float threshold = (float) config.reFacingThresholdDegrees;
        return SpatialMath.yawDiffTo(facing, client) > threshold
                || SpatialMath.pitchDiffTo(facing, client) > threshold;
    }
}
