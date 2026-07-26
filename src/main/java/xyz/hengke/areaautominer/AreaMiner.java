package xyz.hengke.areaautominer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.MovementType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

public class AreaMiner {
    private enum MiningState {
        IDLE,
        FINDING_BLOCK,
        WALKING_TO_BLOCK,
        FACING_BLOCK,
        BREAKING
    }

    private static boolean isMining = false;
    private static BlockPos pos1 = null, pos2 = null;
    private static int currentY = 0;
    private static int currentX = 0;
    private static int currentZ = 0;
    private static MiningState state = MiningState.IDLE;
    private static int waitTicks = 0;

    private static final int FACING_WAIT_TICKS = 15;
    private static final int SHORT_FACING_WAIT_TICKS = 4;
    private static final int MOVE_WAIT_TICKS = 3;
    private static final int MAX_AIR_SKIP_PER_TICK = 5;
    private static final int MAX_WALK_TICKS = 200;
    private static final int MAX_STUCK_TICKS = 20;

    private static final double MAX_REACH_SQUARED = 16.0;
    private static final double STAND_DISTANCE = 2.8;
    private static final double WALK_SPEED = 0.22;
    private static final double ARRIVE_THRESHOLD = 0.3;
    private static final double JUMP_VELOCITY = 0.42;
    private static final double OBSTACLE_CHECK_DISTANCE = 1.5;
    private static final double MIN_DISTANCE_EPSILON = 0.001;

    private static float targetYaw = 0.0f;
    private static float targetPitch = 0.0f;
    private static boolean firstBreakTick = false;
    private static float jitterOffset = 0.0f;
    private static long lastJitterUpdate = 0;
    private static float currentJitterYaw = 0.0f;
    private static float currentJitterPitch = 0.0f;
    private static BlockPos lastMinedPos = null;
    private static boolean isAdjacentBlock = false;
    private static boolean movingWait = false;

    private static int walkTicks = 0;
    private static double lastPlayerX = 0, lastPlayerZ = 0;
    private static int stuckCounter = 0;

    private static final boolean DEBUG = false;

    public static void startMining(BlockPos p1, BlockPos p2) {
        if (isMining) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (p1 == null || p2 == null) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c请先选择区域！"), false);
            }
            return;
        }

        pos1 = p1;
        pos2 = p2;
        isMining = true;

        currentY = Math.max(pos1.getY(), pos2.getY());
        currentX = Math.min(pos1.getX(), pos2.getX());
        currentZ = Math.min(pos1.getZ(), pos2.getZ());
        lastMinedPos = null;
        isAdjacentBlock = false;
        movingWait = false;
        walkTicks = 0;
        stuckCounter = 0;
        lastPlayerX = 0;
        lastPlayerZ = 0;
        state = MiningState.FINDING_BLOCK;

        if (client.player != null) {
            client.player.sendMessage(Text.literal("§a开始挖掘区域"), false);
        }
        logDebug("开始挖掘区域");
    }

    public static void stopMining() {
        isMining = false;
        state = MiningState.IDLE;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§c停止挖掘"), false);
        }
        logDebug("停止挖掘");
    }

    public static boolean isMining() {
        return isMining;
    }

    public static void tick(MinecraftClient client) {
        if (!isMining || client.player == null || client.interactionManager == null || client.world == null) return;

        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        switch (state) {
            case FINDING_BLOCK:
                findNextBlock(client, minX, maxX, minY, minZ, maxZ);
                break;

            case WALKING_TO_BLOCK:
                walkToBlock(client, minX, maxX, minY, minZ, maxZ);
                break;

            case FACING_BLOCK:
                faceBlock(client);
                break;

            case BREAKING:
                startBreaking(client, minX, maxX, minY, minZ, maxZ);
                break;

            case IDLE:
            default:
                break;
        }
    }

    private static void findNextBlock(MinecraftClient client, int minX, int maxX, int minY, int minZ, int maxZ) {
        BlockPos targetPos = new BlockPos(currentX, currentY, currentZ);

        int airSkipCount = 0;
        while (client.world.getBlockState(targetPos).isAir()) {
            if (!advancePosition(minX, maxX, minY, minZ, maxZ, client)) return;
            targetPos = new BlockPos(currentX, currentY, currentZ);
            airSkipCount++;
            if (airSkipCount >= MAX_AIR_SKIP_PER_TICK) {
                return;
            }
        }

        double targetX = currentX + 0.5;
        double targetY = currentY + 0.5;
        double targetZ = currentZ + 0.5;

        double playerX = client.player.getX();
        double playerY = client.player.getY() + client.player.getEyeHeight(client.player.getPose());
        double playerZ = client.player.getZ();

        double dx = playerX - targetX;
        double dy = playerY - targetY;
        double dz = playerZ - targetZ;
        double distanceSquared = dx * dx + dy * dy + dz * dz;

        if (distanceSquared > MAX_REACH_SQUARED) {
            walkTicks = 0;
            stuckCounter = 0;
            lastPlayerX = client.player.getX();
            lastPlayerZ = client.player.getZ();
            state = MiningState.WALKING_TO_BLOCK;
            logDebug("超出挖掘范围，开始行走");
            return;
        }

        if (!hasLineOfSight(client, targetPos)) {
            walkTicks = 0;
            stuckCounter = 0;
            lastPlayerX = client.player.getX();
            lastPlayerZ = client.player.getZ();
            state = MiningState.WALKING_TO_BLOCK;
            logDebug("无视线，开始行走");
            return;
        }

        calculateTargetLook(client, targetPos);

        isAdjacentBlock = isAdjacentToLast(targetPos);
        if (isAdjacentBlock) {
            float currentYaw = client.player.getYaw();
            float yawDiff = targetYaw - currentYaw;
            while (yawDiff < -180.0F) yawDiff += 360.0F;
            while (yawDiff > 180.0F) yawDiff -= 360.0F;
            float pitchDiff = Math.abs(targetPitch - client.player.getPitch());

            if (Math.abs(yawDiff) < 5.0F && pitchDiff < 5.0F) {
                firstBreakTick = true;
                state = MiningState.BREAKING;
                logDebug("相邻方块且已对准，直接挖掘");
                return;
            }
            waitTicks = SHORT_FACING_WAIT_TICKS;
        } else {
            waitTicks = FACING_WAIT_TICKS;
        }
        state = MiningState.FACING_BLOCK;
        logDebug("开始转向");
    }

    private static boolean advancePosition(int minX, int maxX, int minY, int minZ, int maxZ, MinecraftClient client) {
        currentX++;
        if (currentX > maxX) {
            currentX = minX;
            currentZ++;
            if (currentZ > maxZ) {
                currentZ = minZ;
                currentY--;
                if (currentY < minY) {
                    stopMining();
                    client.player.sendMessage(Text.literal("§a挖掘完成！"), false);
                    return false;
                }
            }
        }
        return true;
    }

    private static void calculateTargetLook(MinecraftClient client, BlockPos targetPos) {
        double lookDx = (targetPos.getX() + 0.5) - client.player.getX();
        double lookDy = (targetPos.getY() + 0.5) - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double lookDz = (targetPos.getZ() + 0.5) - client.player.getZ();

        targetYaw = (float) Math.atan2(lookDz, lookDx) * (180.0F / (float) Math.PI) - 90.0F;
        targetPitch = (float) -Math.atan2(lookDy, Math.sqrt(lookDx * lookDx + lookDz * lookDz)) * (180.0F / (float) Math.PI);
    }

    private static void walkToBlock(MinecraftClient client, int minX, int maxX, int minY, int minZ, int maxZ) {
        walkTicks++;
        double currentPlayerX = client.player.getX();
        double currentPlayerZ = client.player.getZ();

        double movedDistance = Math.sqrt(
            Math.pow(currentPlayerX - lastPlayerX, 2) + Math.pow(currentPlayerZ - lastPlayerZ, 2)
        );
        if (movedDistance < 0.01) {
            stuckCounter++;
        } else {
            stuckCounter = 0;
            lastPlayerX = currentPlayerX;
            lastPlayerZ = currentPlayerZ;
        }

        if (walkTicks > MAX_WALK_TICKS || stuckCounter > MAX_STUCK_TICKS) {
            logDebug("行走超时或卡住，跳过当前方块");
            client.player.sendMessage(Text.literal("§e无法到达方块，跳过: " + currentX + "," + currentY + "," + currentZ), false);
            client.player.setVelocity(0, client.player.getVelocity().y, 0);
            walkTicks = 0;
            stuckCounter = 0;
            if (!advancePosition(minX, maxX, minY, minZ, maxZ, client)) return;
            state = MiningState.FINDING_BLOCK;
            return;
        }

        double targetX = currentX + 0.5;
        double targetZ = currentZ + 0.5;

        double dx = targetX - currentPlayerX;
        double dz = targetZ - currentPlayerZ;
        double horizontalLength = Math.sqrt(dx * dx + dz * dz);

        if (horizontalLength < MIN_DISTANCE_EPSILON) {
            horizontalLength = MIN_DISTANCE_EPSILON;
        }

        double standX = targetX - (dx / horizontalLength) * STAND_DISTANCE;
        double standZ = targetZ - (dz / horizontalLength) * STAND_DISTANCE;

        double distX = standX - currentPlayerX;
        double distZ = standZ - currentPlayerZ;
        double distLength = Math.sqrt(distX * distX + distZ * distZ);

        float walkYaw = (float) Math.atan2(dz, dx) * (180.0F / (float) Math.PI) - 90.0F;
        client.player.setYaw(walkYaw);

        if (distLength < ARRIVE_THRESHOLD) {
            client.player.setVelocity(0, client.player.getVelocity().y, 0);
            waitTicks = MOVE_WAIT_TICKS;
            movingWait = true;
            isAdjacentBlock = false;
            calculateTargetLook(client, new BlockPos(currentX, currentY, currentZ));
            state = MiningState.FACING_BLOCK;
            walkTicks = 0;
            stuckCounter = 0;
            logDebug("到达目标位置，准备转向");
            return;
        }

        double stepSize = WALK_SPEED;
        if (distLength < stepSize) {
            stepSize = distLength;
        }

        double stepX = (distX / distLength) * stepSize;
        double stepZ = (distZ / distLength) * stepSize;

        boolean needJump = checkObstacleInFront(client, stepX, stepZ) && client.player.isOnGround();

        if (needJump) {
            client.player.setVelocity(stepX, JUMP_VELOCITY, stepZ);
            client.player.move(MovementType.SELF, new Vec3d(stepX, 0, stepZ));
            logDebug("跳跃越过障碍");
        } else {
            client.player.move(MovementType.SELF, new Vec3d(stepX, 0, stepZ));
        }
    }

    private static boolean checkObstacleInFront(MinecraftClient client, double stepX, double stepZ) {
        int playerBlockX = (int) Math.floor(client.player.getX());
        int playerBlockY = (int) Math.floor(client.player.getY());
        int playerBlockZ = (int) Math.floor(client.player.getZ());

        int checkDistances[] = {1, 2};
        for (int dist : checkDistances) {
            BlockPos footPos = new BlockPos(
                (int) Math.floor(client.player.getX() + stepX * dist),
                playerBlockY,
                (int) Math.floor(client.player.getZ() + stepZ * dist)
            );
            BlockPos headPos = new BlockPos(footPos.getX(), footPos.getY() + 1, footPos.getZ());
            BlockPos aboveHeadPos = new BlockPos(footPos.getX(), footPos.getY() + 2, footPos.getZ());

            boolean footBlocked = !client.world.getBlockState(footPos).isAir();
            boolean headBlocked = !client.world.getBlockState(headPos).isAir();
            boolean aboveHeadClear = client.world.getBlockState(aboveHeadPos).isAir();

            if (footBlocked && headBlocked && aboveHeadClear) {
                return true;
            }
            if (footBlocked && headBlocked && !aboveHeadClear) {
                return false;
            }
        }
        return false;
    }

    private static boolean isAdjacentToLast(BlockPos pos) {
        if (lastMinedPos == null) return false;
        int dx = Math.abs(pos.getX() - lastMinedPos.getX());
        int dy = Math.abs(pos.getY() - lastMinedPos.getY());
        int dz = Math.abs(pos.getZ() - lastMinedPos.getZ());
        return dx + dy + dz == 1;
    }

    private static void faceBlock(MinecraftClient client) {
        if (movingWait) {
            waitTicks--;
            if (waitTicks <= 0) {
                movingWait = false;
                waitTicks = FACING_WAIT_TICKS;
                calculateTargetLook(client, new BlockPos(currentX, currentY, currentZ));
            }
            return;
        }

        int totalWaitTicks = isAdjacentBlock ? SHORT_FACING_WAIT_TICKS : FACING_WAIT_TICKS;
        float jitterScale = isAdjacentBlock ? 0.3f : 1.0f;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastJitterUpdate > 80) {
            jitterOffset += 0.3f + Math.random() * 0.2f;
            double angle = Math.random() * Math.PI * 2;
            double magnitude = (2.0 + Math.random() * 3.0) * jitterScale;
            currentJitterYaw = (float) (Math.cos(angle) * magnitude);
            currentJitterPitch = (float) (Math.sin(angle) * magnitude * 0.6);
            lastJitterUpdate = currentTime;
        }

        float waveYaw = (float) Math.sin(jitterOffset * 2.1) * 1.5f * jitterScale;
        float wavePitch = (float) Math.cos(jitterOffset * 1.7) * 0.8f * jitterScale;

        float progress = 1.0f - (float) waitTicks / totalWaitTicks;
        float jitterFade = Math.max(0.2f, 1.0f - progress * 0.7f);

        float totalJitterYaw = (currentJitterYaw + waveYaw) * jitterFade;
        float totalJitterPitch = (currentJitterPitch + wavePitch) * jitterFade;

        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();

        float yawDiff = targetYaw - currentYaw;
        while (yawDiff < -180.0F) yawDiff += 360.0F;
        while (yawDiff > 180.0F) yawDiff -= 360.0F;

        float smoothFactor = isAdjacentBlock ? 0.25F : (0.12F + (float) (Math.random() * 0.08));
        float newYaw = currentYaw + yawDiff * smoothFactor + totalJitterYaw;
        float newPitch = currentPitch + (targetPitch - currentPitch) * smoothFactor + totalJitterPitch;

        client.player.setYaw(newYaw);
        client.player.setPitch(newPitch);

        waitTicks--;
        if (waitTicks <= 0) {
            float finalNoise = isAdjacentBlock ? 0.5f : 2.0f;
            client.player.setYaw(targetYaw + (float)(Math.random() - 0.5) * finalNoise);
            client.player.setPitch(targetPitch + (float)(Math.random() - 0.5) * (finalNoise * 0.75f));
            firstBreakTick = true;
            state = MiningState.BREAKING;
            logDebug("转向完成，开始挖掘");
        }
    }

    private static Direction calculateDirection(MinecraftClient client, BlockPos targetPos) {
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

    private static boolean hasLineOfSight(MinecraftClient client, BlockPos targetPos) {
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
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        client.player
                )
        );

        if (hitResult.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
            return true;
        }

        return hitResult.getBlockPos().equals(targetPos);
    }

    private static void startBreaking(MinecraftClient client, int minX, int maxX, int minY, int minZ, int maxZ) {
        BlockPos targetPos = new BlockPos(currentX, currentY, currentZ);

        if (client.world.getBlockState(targetPos).isAir()) {
            if (!advancePosition(minX, maxX, minY, minZ, maxZ, client)) return;
            state = MiningState.FINDING_BLOCK;
            return;
        }

        double targetX = currentX + 0.5;
        double targetY = currentY + 0.5;
        double targetZ = currentZ + 0.5;
        double playerX = client.player.getX();
        double playerY = client.player.getY() + client.player.getEyeHeight(client.player.getPose());
        double playerZ = client.player.getZ();
        double dx = playerX - targetX;
        double dy = playerY - targetY;
        double dz = playerZ - targetZ;
        double distanceSquared = dx * dx + dy * dy + dz * dz;

        if (distanceSquared > MAX_REACH_SQUARED || !hasLineOfSight(client, targetPos)) {
            walkTicks = 0;
            stuckCounter = 0;
            lastPlayerX = client.player.getX();
            lastPlayerZ = client.player.getZ();
            state = MiningState.WALKING_TO_BLOCK;
            logDebug("挖掘时超出范围或无视线，重新行走");
            return;
        }

        Direction direction = calculateDirection(client, targetPos);
        GameMode gameMode = client.interactionManager.getCurrentGameMode();

        if (gameMode == GameMode.CREATIVE) {
            client.interactionManager.breakBlock(targetPos);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            lastMinedPos = new BlockPos(targetPos);
            if (!advancePosition(minX, maxX, minY, minZ, maxZ, client)) return;
            state = MiningState.FINDING_BLOCK;
        } else {
            if (firstBreakTick) {
                client.interactionManager.attackBlock(targetPos, direction);
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                firstBreakTick = false;
            }

            client.interactionManager.updateBlockBreakingProgress(targetPos, direction);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);

            if (client.world.getBlockState(targetPos).isAir()) {
                lastMinedPos = new BlockPos(targetPos);
                if (!advancePosition(minX, maxX, minY, minZ, maxZ, client)) return;
                state = MiningState.FINDING_BLOCK;
                logDebug("方块挖掘完成");
            }
        }
    }

    private static void logDebug(String message) {
        if (DEBUG) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§7[DEBUG] " + message), false);
            }
        }
    }
}
