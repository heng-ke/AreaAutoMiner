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
    // 抖动角度更新间隔（毫秒），模拟人脑处理视觉反馈的延迟
    private static final long JITTER_UPDATE_INTERVAL_MS = 80;
    // 每次抖动偏移的增量值
    private static final float JITTER_OFFSET_INCREMENT = 0.3f;
    // 抖动的基础幅度（度），乘以动态缩放系数得到实际抖动幅度
    private static final float JITTER_BASE_MAGNITUDE = 2.0f;
    // Y 轴抖动波动的正弦频率，模拟自然手部的微颤
    private static final float WAVE_YAW_FREQUENCY = 2.1f;
    // Pitch 轴抖动波动的余弦频率
    private static final float WAVE_PITCH_FREQUENCY = 1.7f;
    // 视角平滑因子基础值，控制每 tick 向目标方向转动的比例
    private static final float SMOOTH_FACTOR_BASE = 0.15F;
    // 视角平滑因子最大额外奖励值
    private static final float SMOOTH_FACTOR_MAX_BONUS = 0.1F;
    // 大角度转向的奖励阈值（度），超过此值时平滑因子提高以加快转向
    private static final float LARGE_YAW_DIFF_BONUS_THRESHOLD = 60.0f;

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

        if (context.isMovingWait()) {
            context.setWaitTicks(context.getWaitTicks() - 1);
            if (context.getWaitTicks() <= 0) {
                context.setMovingWait(false);
                float currentYaw = client.player.getYaw();
                float yawDiff = SpatialHelper.normalizeYawDiff(context.getTargetYaw() - currentYaw);
                float pitchDiff = Math.abs(context.getTargetPitch() - client.player.getPitch());
                context.setWaitTicks(calculateDynamicWaitTicks(yawDiff, pitchDiff));
                context.setInitialWaitTicks(context.getWaitTicks());
                context.setFacingRetryCount(0);
            }
            return;
        }

        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();
        float yawDiff = SpatialHelper.normalizeYawDiff(context.getTargetYaw() - currentYaw);
        float pitchDiff = Math.abs(context.getTargetPitch() - currentPitch);

        if (context.getWaitTicks() <= 0) {
            context.setWaitTicks(calculateDynamicWaitTicks(yawDiff, pitchDiff));
            context.setInitialWaitTicks(context.getWaitTicks());
        }

        float jitterScale = calculateDynamicJitterScale(yawDiff, pitchDiff);

        long currentTime = System.currentTimeMillis();
        if (currentTime - context.getLastJitterUpdate() > JITTER_UPDATE_INTERVAL_MS) {
            context.setJitterOffset(context.getJitterOffset() + JITTER_OFFSET_INCREMENT + (float) (Math.random() * 0.2));
            double angle = Math.random() * Math.PI * 2;
            double magnitude = (JITTER_BASE_MAGNITUDE + Math.random() * 3.0) * jitterScale;
            context.setCurrentJitterYaw((float) (Math.cos(angle) * magnitude));
            context.setCurrentJitterPitch((float) (Math.sin(angle) * magnitude * 0.6));
            context.setLastJitterUpdate(currentTime);
        }

        float waveYaw = (float) Math.sin(context.getJitterOffset() * WAVE_YAW_FREQUENCY) * 1.5f * jitterScale;
        float wavePitch = (float) Math.cos(context.getJitterOffset() * WAVE_PITCH_FREQUENCY) * 0.8f * jitterScale;

        int totalWaitTicks = Math.max(context.getInitialWaitTicks(), 2);
        float progress = 1.0f - (float) context.getWaitTicks() / totalWaitTicks;
        float jitterFade = Math.max(0.1f, 1.0f - progress * 0.8f);

        float totalJitterYaw = (context.getCurrentJitterYaw() + waveYaw) * jitterFade;
        float totalJitterPitch = (context.getCurrentJitterPitch() + wavePitch) * jitterFade;

        float smoothFactor = (SMOOTH_FACTOR_BASE + (float) (Math.random() * SMOOTH_FACTOR_MAX_BONUS)) * (Math.abs(yawDiff) > LARGE_YAW_DIFF_BONUS_THRESHOLD ? 1.5f : 1.0f);
        float newYaw = currentYaw + yawDiff * smoothFactor + totalJitterYaw;
        float newPitch = currentPitch + (context.getTargetPitch() - currentPitch) * smoothFactor + totalJitterPitch;

        client.player.setYaw(newYaw);
        client.player.setPitch(newPitch);

        context.setWaitTicks(context.getWaitTicks() - 1);
        if (context.getWaitTicks() <= 0) {
            float finalNoise = jitterScale * 2.0f;

            if (Math.abs(yawDiff) > FACING_COMPLETE_THRESHOLD || pitchDiff > FACING_COMPLETE_THRESHOLD) {
                context.setFacingRetryCount(context.getFacingRetryCount() + 1);
                if (context.getFacingRetryCount() > config.getMaxFacingRetries()) {
                    notificationService.logDebug("转向重试次数过多，强制开始挖掘");
                    client.player.setYaw(context.getTargetYaw() + (float)(Math.random() - 0.5) * finalNoise);
                    client.player.setPitch(context.getTargetPitch() + (float)(Math.random() - 0.5) * (finalNoise * 0.75f));
                    context.setFirstBreakTick(true);
                    context.setBreakTicks(0);
                    context.setFacingRetryCount(0);
                    context.setState(MiningState.BREAKING);
                    return;
                }
                context.setWaitTicks(2);
                context.setInitialWaitTicks(2);
                return;
            }

            client.player.setYaw(context.getTargetYaw() + (float)(Math.random() - 0.5) * finalNoise);
            client.player.setPitch(context.getTargetPitch() + (float)(Math.random() - 0.5) * (finalNoise * 0.75f));
            context.setFirstBreakTick(true);
            context.setBreakTicks(0);
            context.setFacingRetryCount(0);
            context.setState(MiningState.BREAKING);
            notificationService.logDebug("转向完成，开始挖掘");
        }
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
            return 2;
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