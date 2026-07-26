package xyz.hengke.areaautominer.controller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.helper.BreakingHelper;
import xyz.hengke.areaautominer.helper.CameraHelper;
import xyz.hengke.areaautominer.helper.InputHelper;
import xyz.hengke.areaautominer.helper.MovementHelper;
import xyz.hengke.areaautominer.listener.MiningListener;
import xyz.hengke.areaautominer.model.MiningState;

public class MiningController {
    private final MiningContext context;
    private final InputHelper inputHelper;
    private final CameraHelper cameraHelper;
    private final MovementHelper movementHelper;
    private final BreakingHelper breakingHelper;

    public MiningController(MinecraftClient client) {
        this.context = new MiningContext(client);
        this.inputHelper = new InputHelper(context);
        this.cameraHelper = new CameraHelper(context, inputHelper);
        this.movementHelper = new MovementHelper(context, inputHelper, cameraHelper);
        this.breakingHelper = new BreakingHelper(context, inputHelper, cameraHelper);
    }

    public void setListener(MiningListener listener) {
        this.context.listener = listener;
    }

    public void startMining(BlockPos p1, BlockPos p2) {
        if (context.isMining) return;
        MinecraftClient client = context.client;
        if (p1 == null || p2 == null) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c请先选择区域！"), false);
            }
            return;
        }

        context.pos1 = p1;
        context.pos2 = p2;
        context.isMining = true;

        int maxY = Math.max(p1.getY(), p2.getY());
        int playerY = (int) Math.floor(client.player.getY());
        context.currentY = Math.min(maxY, playerY + 1);
        context.currentX = Math.min(p1.getX(), p2.getX());
        context.currentZ = Math.min(p1.getZ(), p2.getZ());
        context.lastMinedPos = null;
        context.isAdjacentBlock = false;
        context.movingWait = false;
        context.walkTicks = 0;
        context.stuckCounter = 0;
        context.breakTicks = 0;
        context.lastPlayerX = 0;
        context.lastPlayerZ = 0;
        context.walkRetryCount = 0;
        context.jumpCooldown = 0;
        context.state = MiningState.FINDING_BLOCK;

        if (client.player != null) {
            client.player.sendMessage(Text.literal("§a开始挖掘区域"), false);
        }
        if (context.listener != null) {
            context.listener.onStartMining(p1, p2);
        }
        logDebug("开始挖掘区域");
    }

    public void stopMining() {
        context.isMining = false;
        context.state = MiningState.IDLE;
        inputHelper.releaseAllKeys();

        MinecraftClient client = context.client;
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§c停止挖掘"), false);
        }
        if (context.listener != null) {
            context.listener.onStopMining();
        }
        logDebug("停止挖掘");
    }

    public boolean isMining() {
        return context.isMining;
    }

    public void tick() {
        MinecraftClient client = context.client;
        if (!context.isMining || client.player == null || client.interactionManager == null || client.world == null) {
            return;
        }

        int minX = Math.min(context.pos1.getX(), context.pos2.getX());
        int maxX = Math.max(context.pos1.getX(), context.pos2.getX());
        int minY = Math.min(context.pos1.getY(), context.pos2.getY());
        int minZ = Math.min(context.pos1.getZ(), context.pos2.getZ());
        int maxZ = Math.max(context.pos1.getZ(), context.pos2.getZ());

        switch (context.state) {
            case FINDING_BLOCK:
                findNextBlock(client, minX, maxX, minY, minZ, maxZ);
                break;

            case WALKING_TO_BLOCK:
                movementHelper.walkToBlock(minX, maxX, minY, minZ, maxZ);
                break;

            case FACING_BLOCK:
                cameraHelper.faceBlock();
                break;

            case BREAKING:
                breakingHelper.startBreaking(minX, maxX, minY, minZ, maxZ);
                break;

            case IDLE:
            default:
                break;
        }
    }

    private void findNextBlock(MinecraftClient client, int minX, int maxX, int minY, int minZ, int maxZ) {
        BlockPos targetPos = new BlockPos(context.currentX, context.currentY, context.currentZ);

        int airSkipCount = 0;
        while (client.world.getBlockState(targetPos).isAir()) {
            if (!advancePosition(minX, maxX, minY, minZ, maxZ)) return;
            targetPos = new BlockPos(context.currentX, context.currentY, context.currentZ);
            airSkipCount++;
            if (airSkipCount >= MiningConfig.MAX_AIR_SKIP_PER_TICK) {
                return;
            }
        }

        double targetX = context.currentX + 0.5;
        double targetY = context.currentY + 0.5;
        double targetZ = context.currentZ + 0.5;

        double playerX = client.player.getX();
        double playerY = client.player.getY() + client.player.getEyeHeight(client.player.getPose());
        double playerZ = client.player.getZ();

        double dx = playerX - targetX;
        double dy = playerY - targetY;
        double dz = playerZ - targetZ;
        double horizontalDistanceSquared = dx * dx + dz * dz;
        double verticalDistance = Math.abs(dy);

        boolean withinHorizontalRange = horizontalDistanceSquared <= MiningConfig.MAX_REACH_SQUARED;
        boolean withinVerticalRange = verticalDistance <= 3.0;

        if (!withinHorizontalRange || !withinVerticalRange) {
            context.walkTicks = 0;
            context.stuckCounter = 0;
            context.walkRetryCount = 0;
            context.lastPlayerX = client.player.getX();
            context.lastPlayerZ = client.player.getZ();
            context.state = MiningState.WALKING_TO_BLOCK;
            logDebug("超出挖掘范围，开始行走");
            return;
        }

        if (!breakingHelper.hasLineOfSight(client, targetPos)) {
            context.walkTicks = 0;
            context.stuckCounter = 0;
            context.walkRetryCount = 0;
            context.lastPlayerX = client.player.getX();
            context.lastPlayerZ = client.player.getZ();
            context.state = MiningState.WALKING_TO_BLOCK;
            logDebug("无视线，开始行走");
            return;
        }

        cameraHelper.calculateTargetLook(targetPos);

        context.isAdjacentBlock = breakingHelper.isAdjacentToLast(targetPos);
        
        float currentYaw = client.player.getYaw();
        float yawDiff = context.targetYaw - currentYaw;
        while (yawDiff < -180.0F) yawDiff += 360.0F;
        while (yawDiff > 180.0F) yawDiff -= 360.0F;
        float pitchDiff = Math.abs(context.targetPitch - client.player.getPitch());

        if (Math.abs(yawDiff) < 5.0F && pitchDiff < 5.0F) {
            context.firstBreakTick = true;
            context.state = MiningState.BREAKING;
            logDebug("已对准，直接挖掘");
            return;
        }
        
        context.waitTicks = cameraHelper.calculateDynamicWaitTicks(yawDiff, pitchDiff);
        context.facingRetryCount = 0;
        context.state = MiningState.FACING_BLOCK;
        logDebug("开始转向，需要转动: " + Math.round(Math.abs(yawDiff)) + "度，等待: " + context.waitTicks + "tick");
    }

    private boolean advancePosition(int minX, int maxX, int minY, int minZ, int maxZ) {
        context.currentX++;
        if (context.currentX > maxX) {
            context.currentX = minX;
            context.currentZ++;
            if (context.currentZ > maxZ) {
                context.currentZ = minZ;
                context.currentY--;
                if (context.currentY < minY) {
                    stopMining();
                    if (context.listener != null) {
                        context.listener.onMineComplete();
                    }
                    return false;
                }
            }
        }
        return true;
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