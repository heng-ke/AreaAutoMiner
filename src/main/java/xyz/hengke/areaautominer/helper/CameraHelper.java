package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.NotificationService;

public class CameraHelper {
    // 判定视角转向完成的阈值（度），低于此值视为对准完成
    private static final float FACING_COMPLETE_THRESHOLD = 5.0F;
    // 抖动相位每 tick 固定增量（连续平滑推进，替代原 80ms 随机跳变）
    private static final float JITTER_PHASE_INCREMENT = 0.4f;
    // Y 轴抖动波幅度（度），模拟自然手部微颤
    private static final float JITTER_WAVE_YAW_MAGNITUDE = 1.5f;
    // Pitch 轴抖动波幅度（度）
    private static final float JITTER_WAVE_PITCH_MAGNITUDE = 0.8f;
    // Y 轴抖动波动的正弦频率
    private static final float WAVE_YAW_FREQUENCY = 2.1f;
    // Pitch 轴抖动波动的余弦频率
    private static final float WAVE_PITCH_FREQUENCY = 1.7f;

    private final MiningContext context;
    private final InputHelper inputHelper;
    private final NotificationService notificationService;

    public CameraHelper(MiningContext context, InputHelper inputHelper, NotificationService notificationService) {
        this.context = context;
        this.inputHelper = inputHelper;
        this.notificationService = notificationService;
    }

    public void faceBlock() {
        MiningConfig config = MiningConfig.getInstance();
        MinecraftClient client = context.getClient();
        inputHelper.releaseAllKeys();

        // Phase 1: movingWait — 等待移动稳定后初始化转向参数
        if (context.isMovingWait()) {
            context.setWaitTicks(context.getWaitTicks() - 1);
            if (context.getWaitTicks() <= 0) {
                context.setMovingWait(false);
                initTurningParameters(client);
                context.setFacingRetryCount(0);
            }
            return;
        }

        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();
        float yawDiff = SpatialHelper.normalizeYawDiff(context.getTargetYaw() - currentYaw);
        float pitchDiff = Math.abs(context.getTargetPitch() - currentPitch);

        // 首次进入或重试后初始化转向参数（不重置重试计数）
        if (context.getWaitTicks() <= 0) {
            initTurningParameters(client);
        }

        // 进度（0→1）并应用 smoothstep 3t²-2t³：导数在 t=0/t=1 处为 0（慢起慢收，无首帧跳变）
        // 使用 (waitTicks-1) 使首 tick 即有位移、末 tick 恰好到达目标，无需瞬间跳变
        int totalTicks = Math.max(context.getInitialWaitTicks(), 2);
        float progress = Math.min(1.0f, 1.0f - (float) (context.getWaitTicks() - 1) / totalTicks);
        float easedProgress = 3.0f * progress * progress - 2.0f * progress * progress * progress;

        // 从起始角度到目标的确定性插值（替代随机 smoothFactor，速度均匀无顿挫）
        float totalYawDelta = SpatialHelper.normalizeYawDiff(context.getTargetYaw() - context.getFaceStartYaw());
        float totalPitchDelta = context.getTargetPitch() - context.getFaceStartPitch();
        float baseYaw = context.getFaceStartYaw() + totalYawDelta * easedProgress;
        float basePitch = context.getFaceStartPitch() + totalPitchDelta * easedProgress;

        // 连续正弦波抖动：每 tick 固定增量推进相位，幅度随进度三次方衰减（末帧归零，消除转向→挖掘切换时的抖动跳变）
        float jitterScale = calculateDynamicJitterScale(yawDiff, pitchDiff);
        float jitterFade = (float) Math.pow(1.0f - easedProgress, 3.0f);
        context.setJitterOffset(context.getJitterOffset() + JITTER_PHASE_INCREMENT);
        float waveYaw = (float) Math.sin(context.getJitterOffset() * WAVE_YAW_FREQUENCY) * JITTER_WAVE_YAW_MAGNITUDE * jitterScale * jitterFade;
        float wavePitch = (float) Math.cos(context.getJitterOffset() * WAVE_PITCH_FREQUENCY) * JITTER_WAVE_PITCH_MAGNITUDE * jitterScale * jitterFade;

        client.player.setYaw(baseYaw + waveYaw);
        client.player.setPitch(basePitch + wavePitch);

        context.setWaitTicks(context.getWaitTicks() - 1);
        if (context.getWaitTicks() <= 0) {
            // 基于设置后的实际视角检查对准偏差（而非 tick 开始时的旧值）
            float finalYawDiff = Math.abs(SpatialHelper.normalizeYawDiff(context.getTargetYaw() - client.player.getYaw()));
            float finalPitchDiff = Math.abs(context.getTargetPitch() - client.player.getPitch());

            if (finalYawDiff > FACING_COMPLETE_THRESHOLD || finalPitchDiff > FACING_COMPLETE_THRESHOLD) {
                // ease-out 后仍偏差过大（外部干扰等），短重试一次（不瞬间跳变）
                context.setFacingRetryCount(context.getFacingRetryCount() + 1);
                if (context.getFacingRetryCount() > config.getMaxFacingRetries()) {
                    notificationService.logDebug("转向重试上限，强制开始挖掘");
                    context.setFirstBreakTick(true);
                    context.setBreakTicks(0);
                    context.setFacingRetryCount(0);
                    context.setState(MiningState.BREAKING);
                    return;
                }
                // 以当前视角为新起点继续插值（initTurningParameters 内部设置 faceStartYaw=currentYaw，无跳变）
                // waitTicks 由 calculateDynamicWaitTicks 根据剩余偏差自适应，大偏差给更多时间平滑修正
                initTurningParameters(client);
                return;
            }

            // 对准完成：ease-out 已平滑到达目标，无瞬间跳变
            context.setFirstBreakTick(true);
            context.setBreakTicks(0);
            context.setFacingRetryCount(0);
            context.setState(MiningState.BREAKING);
            notificationService.logDebug("转向完成，开始挖掘");
        }
    }

    /** 记录转向起始角度并计算等待 ticks（由 faceBlock 在转向开始时调用） */
    private void initTurningParameters(MinecraftClient client) {
        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();
        float yawDiff = SpatialHelper.normalizeYawDiff(context.getTargetYaw() - currentYaw);
        float pitchDiff = Math.abs(context.getTargetPitch() - currentPitch);
        context.setWaitTicks(calculateDynamicWaitTicks(yawDiff, pitchDiff));
        context.setInitialWaitTicks(context.getWaitTicks());
        context.setFaceStartYaw(currentYaw);
        context.setFaceStartPitch(currentPitch);
        // 不重置 jitterOffset：抖动相位跨方块/重试连续，避免相位断裂产生微跳变
        //（重试时 jitterFade≈0 故幅度已归零，相位是否重置无可见差异，但连续更自然）
    }

    public void calculateTargetLook(BlockPos targetPos) {
        Direction visibleFace = SpatialHelper.getVisibleFace(context.getClient(), targetPos);
        if (visibleFace != null) {
            calculateTargetLookToFace(targetPos, visibleFace);
        } else {
            calculateTargetLookToPoint(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        }
    }

    public void calculateTargetLookToFace(BlockPos targetPos, Direction face) {
        double x = targetPos.getX() + 0.5;
        double y = targetPos.getY() + 0.5;
        double z = targetPos.getZ() + 0.5;

        switch (face) {
            case UP:
                y = targetPos.getY() + 0.9;
                break;
            case DOWN:
                y = targetPos.getY() + 0.1;
                break;
            case EAST:
                x = targetPos.getX() + 0.9;
                break;
            case WEST:
                x = targetPos.getX() + 0.1;
                break;
            case SOUTH:
                z = targetPos.getZ() + 0.9;
                break;
            case NORTH:
                z = targetPos.getZ() + 0.1;
                break;
        }

        calculateTargetLookToPoint(x, y, z);
    }

    private void calculateTargetLookToPoint(double targetX, double targetY, double targetZ) {
        MinecraftClient client = context.getClient();
        double lookDx = targetX - client.player.getX();
        double lookDy = targetY - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double lookDz = targetZ - client.player.getZ();

        context.setTargetYaw((float) Math.atan2(lookDz, lookDx) * (180.0F / (float) Math.PI) - 90.0F);
        context.setTargetPitch((float) -Math.atan2(lookDy, Math.sqrt(lookDx * lookDx + lookDz * lookDz)) * (180.0F / (float) Math.PI));
    }

    public int calculateDynamicWaitTicks(float yawDiff, float pitchDiff) {
        MiningConfig config = MiningConfig.getInstance();
        float maxDiff = Math.max(Math.abs(yawDiff), pitchDiff);

        if (maxDiff < 15.0F) {
            return 4;
        } else if (maxDiff < 45.0F) {
            return 6;
        } else if (maxDiff < 90.0F) {
            return 10;
        } else {
            return config.getFacingWaitTicks();
        }
    }

    public float calculateDynamicJitterScale(float yawDiff, float pitchDiff) {
        float maxDiff = Math.max(Math.abs(yawDiff), pitchDiff);

        if (maxDiff < 15.0F) {
            return 0.15f;
        } else if (maxDiff < 45.0F) {
            return 0.4f;
        } else if (maxDiff < 90.0F) {
            return 0.7f;
        } else {
            return 1.0f;
        }
    }

    public float smoothYawTowards(float currentYaw, float targetYaw, float maxDelta) {
        float yawDiff = SpatialHelper.normalizeYawDiff(targetYaw - currentYaw);
        yawDiff = Math.max(-maxDelta, Math.min(maxDelta, yawDiff));
        return currentYaw + yawDiff;
    }
}