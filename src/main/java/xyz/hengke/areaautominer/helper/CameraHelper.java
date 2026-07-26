package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.model.MiningState;

public class CameraHelper {
    private final MiningContext context;
    private final InputHelper inputHelper;

    public CameraHelper(MiningContext context, InputHelper inputHelper) {
        this.context = context;
        this.inputHelper = inputHelper;
    }

    public void faceBlock() {
        MinecraftClient client = context.client;
        inputHelper.releaseAllKeys();

        if (context.movingWait) {
            context.waitTicks--;
            if (context.waitTicks <= 0) {
                context.movingWait = false;
                float currentYaw = client.player.getYaw();
                float yawDiff = context.targetYaw - currentYaw;
                while (yawDiff < -180.0F) yawDiff += 360.0F;
                while (yawDiff > 180.0F) yawDiff -= 360.0F;
                float pitchDiff = Math.abs(context.targetPitch - client.player.getPitch());
                context.waitTicks = calculateDynamicWaitTicks(yawDiff, pitchDiff);
                context.facingRetryCount = 0;
                calculateTargetLook(new BlockPos(context.currentX, context.currentY, context.currentZ));
            }
            return;
        }

        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();
        float yawDiff = context.targetYaw - currentYaw;
        while (yawDiff < -180.0F) yawDiff += 360.0F;
        while (yawDiff > 180.0F) yawDiff -= 360.0F;
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
                if (context.facingRetryCount > MiningConfig.MAX_FACING_RETRIES) {
                    logDebug("转向重试次数过多，强制开始挖掘");
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
            logDebug("转向完成，开始挖掘");
        }
    }

    public void calculateTargetLook(BlockPos targetPos) {
        MinecraftClient client = context.client;
        double lookDx = (targetPos.getX() + 0.5) - client.player.getX();
        double lookDy = (targetPos.getY() + 0.5) - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double lookDz = (targetPos.getZ() + 0.5) - client.player.getZ();

        context.targetYaw = (float) Math.atan2(lookDz, lookDx) * (180.0F / (float) Math.PI) - 90.0F;
        context.targetPitch = (float) -Math.atan2(lookDy, Math.sqrt(lookDx * lookDx + lookDz * lookDz)) * (180.0F / (float) Math.PI);
    }

    public int calculateDynamicWaitTicks(float yawDiff, float pitchDiff) {
        float maxDiff = Math.max(Math.abs(yawDiff), pitchDiff);
        
        if (maxDiff < 15.0F) {
            return 2;
        } else if (maxDiff < 45.0F) {
            return 6;
        } else if (maxDiff < 90.0F) {
            return 10;
        } else {
            return MiningConfig.FACING_WAIT_TICKS;
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

    private void logDebug(String message) {
        if (MiningConfig.DEBUG) {
            MinecraftClient client = context.client;
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§7[DEBUG] " + message), false);
            }
        }
    }
}