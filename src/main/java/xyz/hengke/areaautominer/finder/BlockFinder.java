package xyz.hengke.areaautominer.finder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.state.FacingState;
import xyz.hengke.areaautominer.state.SessionState;
import xyz.hengke.areaautominer.state.TraversalState;
import xyz.hengke.areaautominer.helper.AdvanceCoordinator;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.ReachChecker;
import xyz.hengke.areaautominer.helper.SpatialMath;
import xyz.hengke.areaautominer.helper.WalkRequester;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.NotificationService;

/**
 * 下一方块选择：跳过空气、检查可达性，决定直接挖掘、行走还是转向。
 * 依赖状态对象:遍历游标(经 TraversalState)、转向/挖掘/会话状态。
 *
 * <p>方案 1A：推进统一走 {@link AdvanceCoordinator}（不再直接持有 areaIterator/completionService），
 * 遍历位置经 TraversalState 直取。DRY-1：行走请求经 {@link WalkRequester} 统一判定。</p>
 */
public class BlockFinder {
    private final MinecraftClient client;
    private final MiningConfig config;
    private final TraversalState traversal;
    private final CameraHelper cameraHelper;
    private final NotificationService notificationService;
    private final AdvanceCoordinator advanceCoordinator;
    private final WalkRequester walkRequester;
    private final FacingState facing;
    private final SessionState session;

    public BlockFinder(MinecraftClient client, MiningConfig config, TraversalState traversal,
                       CameraHelper cameraHelper, NotificationService notificationService,
                       AdvanceCoordinator advanceCoordinator, WalkRequester walkRequester,
                       FacingState facing, SessionState session) {
        this.client = client;
        this.config = config;
        this.traversal = traversal;
        this.cameraHelper = cameraHelper;
        this.notificationService = notificationService;
        this.advanceCoordinator = advanceCoordinator;
        this.walkRequester = walkRequester;
        this.facing = facing;
        this.session = session;
    }

    public void findNext() {
        BlockPos targetPos = traversal.getPosition();

        int airSkipCount = 0;
        while (client.world.getBlockState(targetPos).isAir()) {
            if (!advanceCoordinator.advanceOrComplete()) return;
            targetPos = traversal.getPosition();
            airSkipCount++;
            if (airSkipCount >= config.maxAirSkipPerTick) {
                notificationService.logDebug("本 tick 跳过 " + airSkipCount + " 个空气方块，未找到目标，下 tick 继续");
                return;
            }
        }

        if (!ReachChecker.isBlockWithinReach(client, targetPos, config)) {
            Vec3d playerPos = SpatialMath.getPlayerPos(client);
            if (walkRequester.requestWalkOrSkip(targetPos, playerPos.x, playerPos.z)
                    == WalkRequester.Result.SKIPPED) {
                advanceCoordinator.advanceAfterSkipped(targetPos);
                return;
            }
            notificationService.logDebug("超出挖掘范围或无视线，开始行走");
            return;
        }

        cameraHelper.calculateTargetLook(targetPos);

        float yawDiff = SpatialMath.yawDiffTo(facing, client);
        float pitchDiff = SpatialMath.pitchDiffTo(facing, client);

        float facingThreshold = (float) config.facingThresholdDegrees;
        if (SpatialMath.isAligned(facing, client, facingThreshold, facingThreshold)) {
            // 方案 C2：挖掘会话初始化（beginBreakSession）由 Controller 在状态转移时统一执行
            session.setState(MiningState.BREAKING);
            notificationService.logDebug("已对准，直接挖掘");
            return;
        }

        // 显式开始一次转向会话：释放按键并重置会话计数，追踪器从当前真实视角开始逼近
        cameraHelper.beginFacing();
        session.setState(MiningState.FACING_BLOCK);
        notificationService.logDebug("开始转向，需要转动: yaw " + Math.round(yawDiff)
                + "度 / pitch " + Math.round(pitchDiff) + "度");
    }
}
