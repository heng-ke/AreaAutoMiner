package xyz.hengke.areaautominer.helper;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;

public class MovementHelper {
    // 判定玩家是否卡住的最小移动距离（每 tick 移动小于此值视为静止）
    private static final double STUCK_MOVEMENT_THRESHOLD = 0.05;
    // 认为玩家已足够接近目标、可以开始转向的距离（方块+玩家碰撞框的大约距离）
    private static final double CLOSE_ENOUGH_DISTANCE = 1.5;
    // 行走重试延迟（负值表示从0开始反向计数10 tick再开始，即给予10 tick的宽限时间）
    private static final int WALK_RETRY_DELAY_TICKS = -10;
    // 跳跃后的冷却时间（tick），防止连续跳跃
    private static final int JUMP_COOLDOWN_TICKS = 10;
    // 重试跳跃的冷却时间（tick），比普通冷却稍长
    private static final int JUMP_COOLDOWN_RETRY_TICKS = 15;
    // 每 tick 最大的视角 Y 轴旋转步数（度），限制转向速度以模拟自然视角
    private static final float MAX_YAW_STEP = 15.0f;
    // 距离计算的最小阈值，防止浮点精度问题导致的除零错误
    private static final double MIN_LENGTH_THRESHOLD = 0.001;
    // 前方障碍物检测的距离（格数），检查路径上是否有1格高以上的障碍
    private static final int OBSTACLE_CHECK_DISTANCE = 2;
    // 前方坠落危险检测的距离（格数），检查路径下方是否有深渊
    private static final int FALL_CHECK_DISTANCE = 2;

    private final MiningContext context;
    private final InputHelper inputHelper;
    private final CameraHelper cameraHelper;
    private final AreaIterator areaIterator;
    private final NotificationService notificationService;
    private final MiningCompletionService completionService;

    public MovementHelper(MiningContext context, InputHelper inputHelper, CameraHelper cameraHelper, AreaIterator areaIterator, NotificationService notificationService, MiningCompletionService completionService) {
        this.context = context;
        this.inputHelper = inputHelper;
        this.cameraHelper = cameraHelper;
        this.areaIterator = areaIterator;
        this.notificationService = notificationService;
        this.completionService = completionService;
    }

    public void walkToBlock() {
        MiningConfig config = MiningConfig.getInstance();
        MinecraftClient client = context.getClient();

        if (context.getJumpCooldown() > 0) {
            context.setJumpCooldown(context.getJumpCooldown() - 1);
        }

        BlockPos targetPos = areaIterator.getCurrentPos();

        if (isLavaDanger(targetPos) || isVoidDanger(targetPos)) {
            inputHelper.releaseAllKeys();
            notificationService.sendMessage("§c检测到危险环境（岩浆/虚空），跳过方块: " + targetPos);
            context.setWalkTicks(0);
            context.setStuckCounter(0);
            context.setWalkRetryCount(0);
            completionService.onBlockSkipped(targetPos);
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.setState(MiningState.FINDING_BLOCK);
            return;
        }

        double targetX = targetPos.getX() + 0.5;
        double targetZ = targetPos.getZ() + 0.5;

        context.setWalkTicks(context.getWalkTicks() + 1);
        double currentPlayerX = client.player.getX();
        double currentPlayerZ = client.player.getZ();

        double dx = targetX - currentPlayerX;
        double dz = targetZ - currentPlayerZ;
        double horizontalLength = Math.sqrt(dx * dx + dz * dz);

        double movedDistance = Math.sqrt(
            Math.pow(currentPlayerX - context.getLastPlayerX(), 2) + Math.pow(currentPlayerZ - context.getLastPlayerZ(), 2)
        );
        if (movedDistance < STUCK_MOVEMENT_THRESHOLD) {
            context.setStuckCounter(context.getStuckCounter() + 1);
        } else {
            context.setStuckCounter(0);
            context.setLastPlayerX(currentPlayerX);
            context.setLastPlayerZ(currentPlayerZ);
        }

        if (context.getWalkTicks() > config.getMaxWalkTicks() || context.getStuckCounter() > config.getMaxStuckTicks()) {
            if (horizontalLength < CLOSE_ENOUGH_DISTANCE) {
                inputHelper.releaseAllKeys();
                context.setWaitTicks(config.getMoveWaitTicks());
                context.setMovingWait(true);
                context.setAdjacentBlock(false);
                cameraHelper.calculateTargetLook(targetPos);
                context.setState(MiningState.FACING_BLOCK);
                context.setWalkTicks(0);
                context.setStuckCounter(0);
                context.setWalkRetryCount(0);
                notificationService.logDebug("已足够接近目标，开始转向");
                return;
            }
            context.setWalkRetryCount(context.getWalkRetryCount() + 1);
            if (context.getWalkRetryCount() <= config.getMaxWalkRetries()) {
                notificationService.logDebug("行走超时或卡住，第 " + context.getWalkRetryCount() + " 次重试");
                inputHelper.releaseAllKeys();
                context.setWalkTicks(WALK_RETRY_DELAY_TICKS);
                context.setStuckCounter(0);
                context.setLastPlayerX(client.player.getX());
                context.setLastPlayerZ(client.player.getZ());
                if (context.getWalkRetryCount() > 1 && client.player.isOnGround() && context.getJumpCooldown() == 0) {
                    inputHelper.setKeyPressed(client.options.jumpKey, true);
                    context.setJumpCooldown(JUMP_COOLDOWN_RETRY_TICKS);
                    notificationService.logDebug("重试时尝试跳跃");
                }
                return;
            }
            notificationService.logDebug("行走超时或卡住，重试 " + config.getMaxWalkRetries() + " 次后仍失败，跳过当前方块");
            inputHelper.releaseAllKeys();
            context.setWalkRetryCount(0);
            completionService.onBlockSkipped(targetPos);
            context.setWalkTicks(0);
            context.setStuckCounter(0);
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.setState(MiningState.FINDING_BLOCK);
            return;
        }

        if (horizontalLength < config.getArriveThreshold()) {
            if (targetPos.getY() > client.player.getY()) {
                BlockPos targetTopPos = targetPos.up();
                BlockPos targetAboveTopPos = targetPos.up(2);
                boolean hasSpaceOnTop = client.world.getBlockState(targetTopPos).isAir() &&
                                         client.world.getBlockState(targetAboveTopPos).isAir();

                if (hasSpaceOnTop && client.player.isOnGround() && context.getJumpCooldown() == 0) {
                    inputHelper.setKeyPressed(client.options.jumpKey, true);
                    context.setJumpCooldown(JUMP_COOLDOWN_TICKS);
                    notificationService.logDebug("跳跃到目标方块顶部");
                    return;
                } else if (!hasSpaceOnTop) {
                    notificationService.logDebug("目标方块上方没有足够空间，尝试直接挖掘");
                }
            }
            inputHelper.releaseAllKeys();
            context.setWaitTicks(config.getMoveWaitTicks());
            context.setMovingWait(true);
            context.setAdjacentBlock(false);
            cameraHelper.calculateTargetLook(targetPos);
            context.setState(MiningState.FACING_BLOCK);
            context.setWalkTicks(0);
            context.setStuckCounter(0);
            context.setWalkRetryCount(0);
            notificationService.logDebug("到达目标位置，准备转向");
            return;
        }

        float walkYaw = (float) Math.atan2(dz, dx) * (180.0F / (float) Math.PI) - 90.0F;
        float smoothedYaw = cameraHelper.smoothYawTowards(client.player.getYaw(), walkYaw, MAX_YAW_STEP);
        client.player.setYaw(smoothedYaw);

        boolean needJump = checkObstacleInFront(dx, dz) && client.player.isOnGround() && context.getJumpCooldown() == 0;
        boolean wouldFall = targetPos.getY() < client.player.getY() && checkFallDanger(dx, dz);

        if (wouldFall && !needJump) {
            inputHelper.releaseAllKeys();
            completionService.onBlockSkipped(targetPos);
            context.setWalkTicks(0);
            context.setStuckCounter(0);
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.setState(MiningState.FINDING_BLOCK);
            return;
        }

        inputHelper.setKeyPressed(client.options.forwardKey, true);

        if (needJump) {
            inputHelper.setKeyPressed(client.options.jumpKey, true);
            context.setJumpCooldown(JUMP_COOLDOWN_TICKS);
            notificationService.logDebug("跳跃越过障碍");
        } else {
            inputHelper.setKeyPressed(client.options.jumpKey, false);
        }
    }

    public boolean checkObstacleInFront(double dx, double dz) {
        MinecraftClient client = context.getClient();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < MIN_LENGTH_THRESHOLD) return false;

        double stepX = dx / length;
        double stepZ = dz / length;

        double[] checkOffsets = {-0.2, 0, 0.2};
        int[] checkDistances = {1, OBSTACLE_CHECK_DISTANCE};

        for (int dist : checkDistances) {
            for (double offset : checkOffsets) {
                BlockPos footPos = new BlockPos(
                    (int) Math.floor(client.player.getX() + stepX * dist + stepZ * offset),
                    (int) Math.floor(client.player.getY()),
                    (int) Math.floor(client.player.getZ() + stepZ * dist - stepX * offset)
                );

                if (footPos.getX() < context.getMinX() || footPos.getX() > context.getMaxX() || 
                    footPos.getZ() < context.getMinZ() || footPos.getZ() > context.getMaxZ()) {
                    continue;
                }

                BlockPos headPos = footPos.up();
                BlockPos aboveHeadPos = footPos.up(2);
                BlockPos highPos = footPos.up(3);

                boolean footBlocked = !client.world.getBlockState(footPos).isAir();
                boolean headBlocked = !client.world.getBlockState(headPos).isAir();
                boolean aboveHeadClear = client.world.getBlockState(aboveHeadPos).isAir();
                boolean highClear = client.world.getBlockState(highPos).isAir();

                if ((footBlocked || headBlocked) && aboveHeadClear && highClear) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkFallDanger(double dx, double dz) {
        MinecraftClient client = context.getClient();
        MiningConfig config = MiningConfig.getInstance();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < MIN_LENGTH_THRESHOLD) return false;

        double stepX = dx / length;
        double stepZ = dz / length;

        BlockPos playerPos = new BlockPos(
            (int) Math.floor(client.player.getX()),
            (int) Math.floor(client.player.getY()),
            (int) Math.floor(client.player.getZ())
        );

        int playerGroundY = findGroundY(playerPos);
        if (playerGroundY == Integer.MIN_VALUE) {
            return true;
        }

        for (int dist = 1; dist <= FALL_CHECK_DISTANCE; dist++) {
            BlockPos frontPos = new BlockPos(
                (int) Math.floor(client.player.getX() + stepX * dist),
                (int) Math.floor(client.player.getY()),
                (int) Math.floor(client.player.getZ() + stepZ * dist)
            );

            if (frontPos.getX() < context.getMinX() || frontPos.getX() > context.getMaxX() || 
                frontPos.getZ() < context.getMinZ() || frontPos.getZ() > context.getMaxZ()) {
                continue;
            }

            int frontGroundY = findGroundY(frontPos);
            if (frontGroundY == Integer.MIN_VALUE) {
                return true;
            }

            int heightDiff = playerGroundY - frontGroundY;
            if (heightDiff >= config.getFallDangerThreshold()) {
                return true;
            }
        }
        return false;
    }

    private static final int GROUND_SEARCH_RANGE = 32;

    public int findGroundY(BlockPos pos) {
        MinecraftClient client = context.getClient();
        int minSearchY = Math.max(pos.getY() - GROUND_SEARCH_RANGE, client.world.getBottomY());
        for (int y = pos.getY(); y >= minSearchY; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (!client.world.getBlockState(checkPos).isAir()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean isLavaDanger(BlockPos targetPos) {
        MinecraftClient client = context.getClient();
        if (client.world.getBlockState(targetPos).isOf(Blocks.LAVA)) return true;
        if (client.world.getBlockState(targetPos.down()).isOf(Blocks.LAVA)) return true;
        BlockPos playerPos = new BlockPos(
            (int) Math.floor(client.player.getX()),
            (int) Math.floor(client.player.getY()),
            (int) Math.floor(client.player.getZ())
        );
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos checkPos = playerPos.add(dx, 0, dz);
                if (client.world.getBlockState(checkPos).isOf(Blocks.LAVA)) return true;
            }
        }
        return false;
    }

    private boolean isVoidDanger(BlockPos targetPos) {
        MinecraftClient client = context.getClient();
        return targetPos.getY() < client.world.getBottomY();
    }
}