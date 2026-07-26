package xyz.hengke.areaautominer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

import java.lang.reflect.Method;


public class AreaMiner {
    public interface MiningListener {
        void onMineComplete();
        void onBlockSkipped(BlockPos pos);
        void onBlockMined(BlockPos pos);
        void onStartMining(BlockPos pos1, BlockPos pos2);
        void onStopMining();
    }

    private enum MiningState {
        IDLE,
        FINDING_BLOCK,
        WALKING_TO_BLOCK,
        FACING_BLOCK,
        BREAKING
    }

    private boolean isMining = false;
    private BlockPos pos1 = null, pos2 = null;
    private int currentY = 0;
    private int currentX = 0;
    private int currentZ = 0;
    private MiningState state = MiningState.IDLE;
    private int waitTicks = 0;

    private static final int FACING_WAIT_TICKS = 15;
    private static final int SHORT_FACING_WAIT_TICKS = 4;
    private static final int MOVE_WAIT_TICKS = 3;
    private static final int MAX_AIR_SKIP_PER_TICK = 5;
    private static final int MAX_WALK_TICKS = 200;
    private static final int MAX_STUCK_TICKS = 20;
    private static final int MAX_BREAK_TICKS = 400;

    private static final double MAX_REACH_SQUARED = 16.0;
    private static final double ARRIVE_THRESHOLD = 1.2;
    private static final double FALL_DANGER_THRESHOLD = 3.0;

    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;
    private boolean firstBreakTick = false;
    private float jitterOffset = 0.0f;
    private long lastJitterUpdate = 0;
    private float currentJitterYaw = 0.0f;
    private float currentJitterPitch = 0.0f;
    private BlockPos lastMinedPos = null;
    private boolean isAdjacentBlock = false;
    private boolean movingWait = false;

    private int walkTicks = 0;
    private double lastPlayerX = 0, lastPlayerZ = 0;
    private int stuckCounter = 0;
    private int breakTicks = 0;

    private int jumpCooldown = 0;

    private int walkRetryCount = 0;
    private static final int MAX_WALK_RETRIES = 2;

    private static final boolean DEBUG = false;

    private MiningListener listener;

    public AreaMiner() {
    }

    public void setListener(MiningListener listener) {
        this.listener = listener;
    }

    public void startMining(BlockPos p1, BlockPos p2) {
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
        breakTicks = 0;
        lastPlayerX = 0;
        lastPlayerZ = 0;
        walkRetryCount = 0;
        jumpCooldown = 0;
        state = MiningState.FINDING_BLOCK;

        if (client.player != null) {
            client.player.sendMessage(Text.literal("§a开始挖掘区域"), false);
        }
        if (listener != null) {
            listener.onStartMining(pos1, pos2);
        }
        logDebug("开始挖掘区域");
    }

    public void stopMining() {
        isMining = false;
        state = MiningState.IDLE;
        releaseAllKeys();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§c停止挖掘"), false);
        }
        if (listener != null) {
            listener.onStopMining();
        }
        logDebug("停止挖掘");
    }

    public boolean isMining() {
        return isMining;
    }

    public void tick(MinecraftClient client) {
        if (!isMining || client.player == null || client.interactionManager == null || client.world == null) {
            return;
        }

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

    private void findNextBlock(MinecraftClient client, int minX, int maxX, int minY, int minZ, int maxZ) {
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
            walkRetryCount = 0;
            lastPlayerX = client.player.getX();
            lastPlayerZ = client.player.getZ();
            state = MiningState.WALKING_TO_BLOCK;
            logDebug("超出挖掘范围，开始行走");
            return;
        }

        if (!hasLineOfSight(client, targetPos)) {
            walkTicks = 0;
            stuckCounter = 0;
            walkRetryCount = 0;
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

    private boolean advancePosition(int minX, int maxX, int minY, int minZ, int maxZ, MinecraftClient client) {
        currentX++;
        if (currentX > maxX) {
            currentX = minX;
            currentZ++;
            if (currentZ > maxZ) {
                currentZ = minZ;
                currentY--;
                if (currentY < minY) {
                    stopMining();
                    if (listener != null) {
                        listener.onMineComplete();
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private void calculateTargetLook(MinecraftClient client, BlockPos targetPos) {
        double lookDx = (targetPos.getX() + 0.5) - client.player.getX();
        double lookDy = (targetPos.getY() + 0.5) - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double lookDz = (targetPos.getZ() + 0.5) - client.player.getZ();

        targetYaw = (float) Math.atan2(lookDz, lookDx) * (180.0F / (float) Math.PI) - 90.0F;
        targetPitch = (float) -Math.atan2(lookDy, Math.sqrt(lookDx * lookDx + lookDz * lookDz)) * (180.0F / (float) Math.PI);
    }



    private void walkToBlock(MinecraftClient client, int minX, int maxX, int minY, int minZ, int maxZ) {
        if (jumpCooldown > 0) {
            jumpCooldown--;
        }

        BlockPos targetPos = new BlockPos(currentX, currentY, currentZ);
        double targetX = targetPos.getX() + 0.5;
        double targetZ = targetPos.getZ() + 0.5;

        walkTicks++;
        double currentPlayerX = client.player.getX();
        double currentPlayerZ = client.player.getZ();

        double dx = targetX - currentPlayerX;
        double dz = targetZ - currentPlayerZ;
        double horizontalLength = Math.sqrt(dx * dx + dz * dz);

        double movedDistance = Math.sqrt(
            Math.pow(currentPlayerX - lastPlayerX, 2) + Math.pow(currentPlayerZ - lastPlayerZ, 2)
        );
        if (movedDistance < 0.05) {
            stuckCounter++;
        } else {
            stuckCounter = 0;
            lastPlayerX = currentPlayerX;
            lastPlayerZ = currentPlayerZ;
        }

        if (walkTicks > MAX_WALK_TICKS || stuckCounter > MAX_STUCK_TICKS) {
            if (horizontalLength < 1.5) {
                releaseAllKeys();
                waitTicks = MOVE_WAIT_TICKS;
                movingWait = true;
                isAdjacentBlock = false;
                calculateTargetLook(client, targetPos);
                state = MiningState.FACING_BLOCK;
                walkTicks = 0;
                stuckCounter = 0;
                walkRetryCount = 0;
                logDebug("已足够接近目标，开始转向");
                return;
            }
            walkRetryCount++;
            if (walkRetryCount <= MAX_WALK_RETRIES) {
                logDebug("行走超时或卡住，第 " + walkRetryCount + " 次重试");
                releaseAllKeys();
                walkTicks = -10;
                stuckCounter = 0;
                lastPlayerX = client.player.getX();
                lastPlayerZ = client.player.getZ();
                return;
            }
            logDebug("行走超时或卡住，重试 " + MAX_WALK_RETRIES + " 次后仍失败，跳过当前方块");
            releaseAllKeys();
            walkRetryCount = 0;
            if (listener != null) {
                listener.onBlockSkipped(new BlockPos(currentX, currentY, currentZ));
            }
            client.player.sendMessage(Text.literal("§e无法到达方块，跳过: " + currentX + "," + currentY + "," + currentZ), false);
            walkTicks = 0;
            stuckCounter = 0;
            if (!advancePosition(minX, maxX, minY, minZ, maxZ, client)) return;
            state = MiningState.FINDING_BLOCK;
            return;
        }

        if (horizontalLength < ARRIVE_THRESHOLD || (horizontalLength < 1.5 && targetPos.getY() > client.player.getY())) {
            releaseAllKeys();
            waitTicks = MOVE_WAIT_TICKS;
            movingWait = true;
            isAdjacentBlock = false;
            calculateTargetLook(client, targetPos);
            state = MiningState.FACING_BLOCK;
            walkTicks = 0;
            stuckCounter = 0;
            logDebug("到达目标位置，准备转向");
            return;
        }

        float walkYaw = (float) Math.atan2(dz, dx) * (180.0F / (float) Math.PI) - 90.0F;
        float smoothedYaw = smoothYawTowards(client.player.getYaw(), walkYaw, 15.0f);
        client.player.setYaw(smoothedYaw);

        boolean needJump = checkObstacleInFront(client, dx, dz) && client.player.isOnGround() && jumpCooldown == 0;
        boolean wouldFall = targetPos.getY() > client.player.getY() && checkFallDanger(client, dx, dz);

        if (wouldFall && !needJump) {
            releaseAllKeys();
            if (listener != null) {
                listener.onBlockSkipped(targetPos);
            }
            client.player.sendMessage(Text.literal("§e前方危险，跳过方块: " + currentX + "," + currentY + "," + currentZ), false);
            walkTicks = 0;
            stuckCounter = 0;
            if (!advancePosition(minX, maxX, minY, minZ, maxZ, client)) return;
            state = MiningState.FINDING_BLOCK;
            return;
        }

        setKeyPressed(client.options.forwardKey, true);

        if (needJump) {
            setKeyPressed(client.options.jumpKey, true);
            jumpCooldown = 10;
            logDebug("跳跃越过障碍");
        } else {
            setKeyPressed(client.options.jumpKey, false);
        }
    }

    private boolean checkObstacleInFront(MinecraftClient client, double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return false;

        double stepX = dx / length;
        double stepZ = dz / length;

        double[] checkOffsets = {-0.2, 0, 0.2};
        int[] checkDistances = {1, 2};

        for (int dist : checkDistances) {
            for (double offset : checkOffsets) {
                BlockPos footPos = new BlockPos(
                    (int) Math.floor(client.player.getX() + stepX * dist + stepZ * offset),
                    (int) Math.floor(client.player.getY()),
                    (int) Math.floor(client.player.getZ() + stepZ * dist - stepX * offset)
                );
                BlockPos headPos = footPos.up();
                BlockPos aboveHeadPos = footPos.up(2);
                BlockPos highPos = footPos.up(3);

                boolean footBlocked = !client.world.getBlockState(footPos).isAir();
                boolean headBlocked = !client.world.getBlockState(headPos).isAir();
                boolean aboveHeadClear = client.world.getBlockState(aboveHeadPos).isAir();
                boolean highClear = client.world.getBlockState(highPos).isAir();

                if (headBlocked && aboveHeadClear && highClear) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkFallDanger(MinecraftClient client, double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return false;

        double stepX = dx / length;
        double stepZ = dz / length;

        BlockPos playerPos = new BlockPos(
            (int) Math.floor(client.player.getX()),
            (int) Math.floor(client.player.getY()),
            (int) Math.floor(client.player.getZ())
        );

        int playerGroundY = findGroundY(client, playerPos);

        for (int dist = 1; dist <= 2; dist++) {
            BlockPos frontPos = new BlockPos(
                (int) Math.floor(client.player.getX() + stepX * dist),
                (int) Math.floor(client.player.getY()),
                (int) Math.floor(client.player.getZ() + stepZ * dist)
            );

            int frontGroundY = findGroundY(client, frontPos);

            int heightDiff = playerGroundY - frontGroundY;
            if (heightDiff >= FALL_DANGER_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private int findGroundY(MinecraftClient client, BlockPos pos) {
        for (int y = pos.getY(); y >= pos.getY() - 6; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (!client.world.getBlockState(checkPos).isAir()) {
                return y;
            }
        }
        return pos.getY() - 6;
    }

    private boolean isAdjacentToLast(BlockPos pos) {
        if (lastMinedPos == null) return false;
        int dx = Math.abs(pos.getX() - lastMinedPos.getX());
        int dy = Math.abs(pos.getY() - lastMinedPos.getY());
        int dz = Math.abs(pos.getZ() - lastMinedPos.getZ());
        return dx + dy + dz == 1;
    }

    private void faceBlock(MinecraftClient client) {
        releaseAllKeys();

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
            
            if (Math.abs(yawDiff) > 5.0F || Math.abs(targetPitch - currentPitch) > 5.0F) {
                waitTicks = SHORT_FACING_WAIT_TICKS;
                return;
            }
            
            client.player.setYaw(targetYaw + (float)(Math.random() - 0.5) * finalNoise);
            client.player.setPitch(targetPitch + (float)(Math.random() - 0.5) * (finalNoise * 0.75f));
            firstBreakTick = true;
            breakTicks = 0;
            state = MiningState.BREAKING;
            logDebug("转向完成，开始挖掘");
        }
    }

    private Direction calculateDirection(MinecraftClient client, BlockPos targetPos) {
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

    private boolean hasLineOfSight(MinecraftClient client, BlockPos targetPos) {
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

    private void startBreaking(MinecraftClient client, int minX, int maxX, int minY, int minZ, int maxZ) {
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

        float currentYaw = client.player.getYaw();
        float yawDiff = targetYaw - currentYaw;
        while (yawDiff < -180.0F) yawDiff += 360.0F;
        while (yawDiff > 180.0F) yawDiff -= 360.0F;
        float pitchDiff = Math.abs(targetPitch - client.player.getPitch());

        if (Math.abs(yawDiff) > 15.0F || pitchDiff > 15.0F) {
            waitTicks = SHORT_FACING_WAIT_TICKS;
            isAdjacentBlock = true;
            state = MiningState.FACING_BLOCK;
            logDebug("挖掘时视角偏移过大，重新转向");
            return;
        }

        breakTicks++;
        if (breakTicks > MAX_BREAK_TICKS) {
            if (listener != null) {
                listener.onBlockSkipped(targetPos);
            }
            client.player.sendMessage(Text.literal("§e挖掘超时，跳过方块: " + currentX + "," + currentY + "," + currentZ), false);
            breakTicks = 0;
            if (!advancePosition(minX, maxX, minY, minZ, maxZ, client)) return;
            state = MiningState.FINDING_BLOCK;
            return;
        }

        Direction direction = calculateDirection(client, targetPos);
        GameMode gameMode = client.interactionManager.getCurrentGameMode();

        if (gameMode == GameMode.CREATIVE) {
            client.interactionManager.breakBlock(targetPos);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            lastMinedPos = new BlockPos(targetPos);
            if (listener != null) {
                listener.onBlockMined(targetPos);
            }
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
                breakTicks = 0;
                if (listener != null) {
                    listener.onBlockMined(targetPos);
                }
                if (!advancePosition(minX, maxX, minY, minZ, maxZ, client)) return;
                state = MiningState.FINDING_BLOCK;
                logDebug("方块挖掘完成");
            }
        }
    }

    private float smoothYawTowards(float currentYaw, float targetYaw, float maxDelta) {
        float yawDiff = targetYaw - currentYaw;
        while (yawDiff < -180.0F) yawDiff += 360.0F;
        while (yawDiff > 180.0F) yawDiff -= 360.0F;
        yawDiff = Math.max(-maxDelta, Math.min(maxDelta, yawDiff));
        return currentYaw + yawDiff;
    }

    private void setKeyPressed(KeyBinding key, boolean pressed) {
        try {
            Method method = KeyBinding.class.getDeclaredMethod("setPressed", boolean.class);
            method.setAccessible(true);
            method.invoke(key, pressed);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void releaseAllKeys() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            setKeyPressed(client.options.forwardKey, false);
            setKeyPressed(client.options.jumpKey, false);
        }
    }

    private void logDebug(String message) {
        if (DEBUG) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§7[DEBUG] " + message), false);
            }
        }
    }
}
