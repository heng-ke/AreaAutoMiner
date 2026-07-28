package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.NotificationService;

public class CameraHelper {
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
        MinecraftClient client = context.client;
        inputHelper.releaseAllKeys();

        if (context.movingWait) {
            context.waitTicks--;
            if (context.waitTicks <= 0) {
                context.movingWait = false;
                float currentYaw = client.player.getYaw();
                float yawDiff = SpatialHelper.normalizeYawDiff(context.targetYaw - currentYaw);
                float pitchDiff = Math.abs(context.targetPitch - client.player.getPitch());
                context.waitTicks = calculateDynamicWaitTicks(yawDiff, pitchDiff);
                context.facingRetryCount = 0;
            }
            return;
        }

        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();
        float yawDiff = SpatialHelper.normalizeYawDiff(context.targetYaw - currentYaw);
        float pitchDiff = Math.abs(context.targetPitch - currentPitch);

        float jitterScale = calculateDynamicJitterScale(yawDiff, pitchDiff);

        long currentTime = System.currentTimeMillis();
        if (currentTime - context.lastJitterUpdate > 80) {
            context.jitterOffset += 0.3f + Math.random() * 0.2f;
            double angle = Math.random() * Math.PI * 2;
            double magnitude = (2.0 + Math.random() * 3.0) * jitterScale;
            context.currentJitterYaw = (float) (Math.cos(angle) * magnitude);
            context.currentJitterPitch = (float) (Math.sin(angle) * magnitude * 0.6);
            context.lastJitterUpdate = currentTime;
        }

        float waveYaw = (float) Math.sin(context.jitterOffset * 2.1) * 1.5f * jitterScale;
        float wavePitch = (float) Math.cos(context.jitterOffset * 1.7) * 0.8f * jitterScale;

        int totalWaitTicks = Math.max(context.waitTicks, 2);
        float progress = 1.0f - (float) context.waitTicks / totalWaitTicks;
        float jitterFade = Math.max(0.1f, 1.0f - progress * 0.8f);

        float totalJitterYaw = (context.currentJitterYaw + waveYaw) * jitterFade;
        float totalJitterPitch = (context.currentJitterPitch + wavePitch) * jitterFade;

        float smoothFactor = (0.15F + (float) (Math.random() * 0.1F)) * (Math.abs(yawDiff) > 60 ? 1.5f : 1.0f);
        float newYaw = currentYaw + yawDiff * smoothFactor + totalJitterYaw;
        float newPitch = currentPitch + (context.targetPitch - currentPitch) * smoothFactor + totalJitterPitch;

        client.player.setYaw(newYaw);
        client.player.setPitch(newPitch);

        context.waitTicks--;
        if (context.waitTicks <= 0) {
            float finalNoise = jitterScale * 2.0f;

            if (Math.abs(yawDiff) > 5.0F || pitchDiff > 5.0F) {
                context.facingRetryCount++;
                if (context.facingRetryCount > config.getMaxFacingRetries()) {
                    notificationService.logDebug("转向重试次数过多，强制开始挖掘");
                    client.player.setYaw(context.targetYaw + (float)(Math.random() - 0.5) * finalNoise);
                    client.player.setPitch(context.targetPitch + (float)(Math.random() - 0.5) * (finalNoise * 0.75f));
                    context.firstBreakTick = true;
                    context.breakTicks = 0;
                    context.state = MiningState.BREAKING;
                    return;
                }
                context.waitTicks = 2;
                return;
            }

            client.player.setYaw(context.targetYaw + (float)(Math.random() - 0.5) * finalNoise);
            client.player.setPitch(context.targetPitch + (float)(Math.random() - 0.5) * (finalNoise * 0.75f));
            context.firstBreakTick = true;
            context.breakTicks = 0;
            context.state = MiningState.BREAKING;
            notificationService.logDebug("转向完成，开始挖掘");
        }
    }

    public void calculateTargetLook(BlockPos targetPos) {
        Direction visibleFace = SpatialHelper.getVisibleFace(context.client, targetPos);
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
        MinecraftClient client = context.client;
        double lookDx = targetX - client.player.getX();
        double lookDy = targetY - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double lookDz = targetZ - client.player.getZ();

        context.targetYaw = (float) Math.atan2(lookDz, lookDx) * (180.0F / (float) Math.PI) - 90.0F;
        context.targetPitch = (float) -Math.atan2(lookDy, Math.sqrt(lookDx * lookDx + lookDz * lookDz)) * (180.0F / (float) Math.PI);
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