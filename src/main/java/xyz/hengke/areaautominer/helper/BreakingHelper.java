package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.model.MiningState;

public class BreakingHelper {
    private final MiningContext context;
    private final InputHelper inputHelper;
    private final CameraHelper cameraHelper;

    public BreakingHelper(MiningContext context, InputHelper inputHelper, CameraHelper cameraHelper) {
        this.context = context;
        this.inputHelper = inputHelper;
        this.cameraHelper = cameraHelper;
    }

    public void startBreaking(int minX, int maxX, int minY, int minZ, int maxZ) {
        MinecraftClient client = context.client;
        BlockPos targetPos = new BlockPos(context.currentX, context.currentY, context.currentZ);

        if (client.world.getBlockState(targetPos).isAir()) {
            if (!advancePosition(minX, maxX, minY, minZ, maxZ)) return;
            context.state = MiningState.FINDING_BLOCK;
            return;
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
        boolean withinVerticalRange = verticalDistance <= 4.0;
        
        if (!withinHorizontalRange || !withinVerticalRange || !hasLineOfSight(client, targetPos)) {
            context.walkTicks = 0;
            context.stuckCounter = 0;
            context.lastPlayerX = client.player.getX();
            context.lastPlayerZ = client.player.getZ();
            context.state = MiningState.WALKING_TO_BLOCK;
            logDebug("挖掘时超出范围或无视线，重新行走");
            return;
        }

        float currentYaw = client.player.getYaw();
        float yawDiff = context.targetYaw - currentYaw;
        while (yawDiff < -180.0F) yawDiff += 360.0F;
        while (yawDiff > 180.0F) yawDiff -= 360.0F;
        float pitchDiff = Math.abs(context.targetPitch - client.player.getPitch());

        if (Math.abs(yawDiff) > 15.0F || pitchDiff > 15.0F) {
            context.waitTicks = MiningConfig.SHORT_FACING_WAIT_TICKS;
            context.isAdjacentBlock = true;
            context.state = MiningState.FACING_BLOCK;
            logDebug("挖掘时视角偏移过大，重新转向");
            return;
        }

        context.breakTicks++;
        if (context.breakTicks > MiningConfig.MAX_BREAK_TICKS) {
            if (context.listener != null) {
                context.listener.onBlockSkipped(targetPos);
            }
            client.player.sendMessage(Text.literal("§e挖掘超时，跳过方块: " + context.currentX + "," + context.currentY + "," + context.currentZ), false);
            context.breakTicks = 0;
            if (!advancePosition(minX, maxX, minY, minZ, maxZ)) return;
            context.state = MiningState.FINDING_BLOCK;
            return;
        }

        Direction direction = calculateDirection(client, targetPos);
        GameMode gameMode = client.interactionManager.getCurrentGameMode();

        if (gameMode == GameMode.CREATIVE) {
            client.interactionManager.breakBlock(targetPos);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            context.lastMinedPos = new BlockPos(targetPos);
            if (context.listener != null) {
                context.listener.onBlockMined(targetPos);
            }
            if (!advancePosition(minX, maxX, minY, minZ, maxZ)) return;
            context.state = MiningState.FINDING_BLOCK;
        } else {
            if (context.firstBreakTick) {
                client.interactionManager.attackBlock(targetPos, direction);
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                context.firstBreakTick = false;
            }

            client.interactionManager.updateBlockBreakingProgress(targetPos, direction);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);

            if (client.world.getBlockState(targetPos).isAir()) {
                context.lastMinedPos = new BlockPos(targetPos);
                context.breakTicks = 0;
                if (context.listener != null) {
                    context.listener.onBlockMined(targetPos);
                }
                if (!advancePosition(minX, maxX, minY, minZ, maxZ)) return;
                context.state = MiningState.FINDING_BLOCK;
                logDebug("方块挖掘完成");
            }
        }
    }

    public Direction calculateDirection(MinecraftClient client, BlockPos targetPos) {
        double dx = (targetPos.getX() + 0.5) - client.player.getX();
        double dy = (targetPos.getY() + 0.5) - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double dz = (targetPos.getZ() + 0.5) - client.player.getZ();

        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);

        if (absY > absX && absY > absZ) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        } else if (absX > absZ) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    public boolean hasLineOfSight(MinecraftClient client, BlockPos targetPos) {
        Vec3d eyePos = new Vec3d(
                client.player.getX(),
                client.player.getY() + client.player.getEyeHeight(client.player.getPose()),
                client.player.getZ()
        );
        Vec3d targetVec = new Vec3d(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5
        );

        net.minecraft.util.hit.BlockHitResult hitResult = client.world.raycast(
                new RaycastContext(
                        eyePos,
                        targetVec,
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        client.player
                )
        );

        if (hitResult.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
            return true;
        }

        return hitResult.getBlockPos().equals(targetPos);
    }

    public boolean isAdjacentToLast(BlockPos pos) {
        if (context.lastMinedPos == null) return false;
        int dx = Math.abs(pos.getX() - context.lastMinedPos.getX());
        int dy = Math.abs(pos.getY() - context.lastMinedPos.getY());
        int dz = Math.abs(pos.getZ() - context.lastMinedPos.getZ());
        return dx + dy + dz == 1;
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
                    context.isMining = false;
                    context.state = MiningState.IDLE;
                    inputHelper.releaseAllKeys();
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