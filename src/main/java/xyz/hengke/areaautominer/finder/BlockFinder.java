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
        MinecraftClient client = context.getClient();
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
                notificationService.logDebug("本 tick 跳过 " + airSkipCount + " 个空气方块，未找到目标，下 tick 继续");
                return;
            }
        }

        if (!SpatialHelper.isBlockWithinReach(client, targetPos, config)) {
            context.startWalkingToBlock();
            notificationService.logDebug("超出挖掘范围或无视线，开始行走");
            return;
        }

        cameraHelper.calculateTargetLook(targetPos);

        context.setAdjacentBlock(SpatialHelper.isAdjacentToLast(context, targetPos));

        float currentYaw = client.player.getYaw();
        float yawDiff = SpatialHelper.normalizeYawDiff(context.getTargetYaw() - currentYaw);
        float pitchDiff = Math.abs(context.getTargetPitch() - client.player.getPitch());

        if (Math.abs(yawDiff) < SpatialHelper.FACING_THRESHOLD_DEGREES && pitchDiff < SpatialHelper.FACING_THRESHOLD_DEGREES) {
            context.setFirstBreakTick(true);
            context.setBreakTicks(0);
            context.setState(MiningState.BREAKING);
            notificationService.logDebug("已对准，直接挖掘");
            return;
        }

        // 显式开始一次转向会话：释放按键并重置会话计数，追踪器从当前真实视角开始逼近
        cameraHelper.beginFacing();
        context.setState(MiningState.FACING_BLOCK);
        notificationService.logDebug("开始转向，需要转动: yaw " + Math.round(Math.abs(yawDiff))
                + "度 / pitch " + Math.round(pitchDiff) + "度");
    }
}