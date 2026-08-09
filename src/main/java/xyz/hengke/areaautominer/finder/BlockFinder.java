package xyz.hengke.areaautominer.finder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.state.BreakingState;
import xyz.hengke.areaautominer.context.state.FacingState;
import xyz.hengke.areaautominer.context.state.MovementState;
import xyz.hengke.areaautominer.context.state.SessionState;
import xyz.hengke.areaautominer.helper.AreaIterator;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.SpatialHelper;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;

/**
 * 下一方块选择：跳过空气、检查可达性，决定直接挖掘、行走还是转向。
 * 依赖状态对象:遍历游标(经 AreaIterator)、转向/挖掘/会话状态。
 */
public class BlockFinder {
    private final MinecraftClient client;
    private final MiningConfig config;
    private final AreaIterator areaIterator;
    private final CameraHelper cameraHelper;
    private final NotificationService notificationService;
    private final MiningCompletionService completionService;
    private final MovementState movement;
    private final FacingState facing;
    private final BreakingState breaking;
    private final SessionState session;

    public BlockFinder(MinecraftClient client, MiningConfig config, AreaIterator areaIterator,
                       CameraHelper cameraHelper, NotificationService notificationService,
                       MiningCompletionService completionService,
                       MovementState movement, FacingState facing, BreakingState breaking, SessionState session) {
        this.client = client;
        this.config = config;
        this.areaIterator = areaIterator;
        this.cameraHelper = cameraHelper;
        this.notificationService = notificationService;
        this.completionService = completionService;
        this.movement = movement;
        this.facing = facing;
        this.breaking = breaking;
        this.session = session;
    }

    public void findNext() {
        BlockPos targetPos = areaIterator.getCurrentPos();

        int airSkipCount = 0;
        while (client.world.getBlockState(targetPos).isAir()) {
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            targetPos = areaIterator.getCurrentPos();
            airSkipCount++;
            if (airSkipCount >= config.maxAirSkipPerTick) {
                notificationService.logDebug("本 tick 跳过 " + airSkipCount + " 个空气方块，未找到目标，下 tick 继续");
                return;
            }
        }

        if (!SpatialHelper.isBlockWithinReach(client, targetPos, config)) {
            movement.startWalkingToBlock(client.player.getX(), client.player.getZ());
            session.setState(MiningState.WALKING_TO_BLOCK);
            notificationService.logDebug("超出挖掘范围或无视线，开始行走");
            return;
        }

        cameraHelper.calculateTargetLook(targetPos);

        float currentYaw = client.player.getYaw();
        float yawDiff = SpatialHelper.normalizeYawDiff(facing.getTargetYaw() - currentYaw);
        float pitchDiff = Math.abs(facing.getTargetPitch() - client.player.getPitch());

        float facingThreshold = (float) config.facingThresholdDegrees;
        if (Math.abs(yawDiff) < facingThreshold && pitchDiff < facingThreshold) {
            breaking.setFirstBreakTick(true);
            breaking.setBreakTicks(0);
            session.setState(MiningState.BREAKING);
            notificationService.logDebug("已对准，直接挖掘");
            return;
        }

        // 显式开始一次转向会话：释放按键并重置会话计数，追踪器从当前真实视角开始逼近
        cameraHelper.beginFacing();
        session.setState(MiningState.FACING_BLOCK);
        notificationService.logDebug("开始转向，需要转动: yaw " + Math.round(Math.abs(yawDiff))
                + "度 / pitch " + Math.round(pitchDiff) + "度");
    }
}
