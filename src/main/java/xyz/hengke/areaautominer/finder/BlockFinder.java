package xyz.hengke.areaautominer.finder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.state.FacingState;
import xyz.hengke.areaautominer.model.TraversalState;
import xyz.hengke.areaautominer.helper.AdvanceCoordinator;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.ReachChecker;
import xyz.hengke.areaautominer.helper.SpatialMath;
import xyz.hengke.areaautominer.helper.WalkRequester;
import xyz.hengke.areaautominer.model.FindResult;
import xyz.hengke.areaautominer.service.NotificationService;

public class BlockFinder {
    private final MinecraftClient client;
    private final MiningConfig config;
    private final TraversalState traversal;
    private final CameraHelper cameraHelper;
    private final NotificationService notificationService;
    private final AdvanceCoordinator advanceCoordinator;
    private final WalkRequester walkRequester;
    private final FacingState facing;

    public BlockFinder(MinecraftClient client, MiningConfig config, TraversalState traversal,
                       CameraHelper cameraHelper, NotificationService notificationService,
                       AdvanceCoordinator advanceCoordinator, WalkRequester walkRequester,
                       FacingState facing) {
        this.client = client;
        this.config = config;
        this.traversal = traversal;
        this.cameraHelper = cameraHelper;
        this.notificationService = notificationService;
        this.advanceCoordinator = advanceCoordinator;
        this.walkRequester = walkRequester;
        this.facing = facing;
    }

    public FindResult findNext() {
        BlockPos targetPos = traversal.getPosition();

        int airSkipCount = 0;
        while (client.world.getBlockState(targetPos).isAir()) {
            if (!advanceCoordinator.advanceOrComplete()) return FindResult.COMPLETE;
            targetPos = traversal.getPosition();
            airSkipCount++;
            if (airSkipCount >= config.maxAirSkipPerTick) {
                notificationService.logDebug("本 tick 跳过 " + airSkipCount + " 个空气方块，未找到目标，下 tick 继续");
                return FindResult.CONTINUE;
            }
        }

        if (!ReachChecker.isBlockWithinReach(client, targetPos, config)) {
            Vec3d playerPos = SpatialMath.getPlayerPos(client);
            if (walkRequester.requestWalkOrSkip(targetPos, playerPos.x, playerPos.z)
                    == WalkRequester.Result.SKIPPED) {
                // 不可达超限跳过：推进成功则继续找下一目标；推进失败（遍历结束）则完成
                return advanceCoordinator.advanceAfterSkipped(targetPos)
                        ? FindResult.CONTINUE : FindResult.COMPLETE;
            }
            notificationService.logDebug("超出挖掘范围或无视线，开始行走");
            return FindResult.WALK;
        }

        cameraHelper.calculateTargetLook(targetPos);

        float yawDiff = SpatialMath.yawDiffTo(facing, client);
        float pitchDiff = SpatialMath.pitchDiffTo(facing, client);

        float facingThreshold = (float) config.facingThresholdDegrees;
        if (Math.abs(yawDiff) < facingThreshold && pitchDiff < facingThreshold) {
            notificationService.logDebug("已对准，直接挖掘");
            return FindResult.BREAK;
        }

        cameraHelper.beginFacing();
        notificationService.logDebug("开始转向，需要转动: yaw " + Math.round(yawDiff)
                + "度 / pitch " + Math.round(pitchDiff) + "度");
        return FindResult.FACE;
    }
}
