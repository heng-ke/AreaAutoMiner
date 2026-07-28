package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.MiningCompletionService;
import xyz.hengke.areaautominer.service.NotificationService;

public class MovementHelper {
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
        MinecraftClient client = context.client;

        if (context.jumpCooldown > 0) {
            context.jumpCooldown--;
        }

        BlockPos targetPos = areaIterator.getCurrentPos();
        double targetX = targetPos.getX() + 0.5;
        double targetZ = targetPos.getZ() + 0.5;

        context.walkTicks++;
        double currentPlayerX = client.player.getX();
        double currentPlayerZ = client.player.getZ();

        double dx = targetX - currentPlayerX;
        double dz = targetZ - currentPlayerZ;
        double horizontalLength = Math.sqrt(dx * dx + dz * dz);

        double movedDistance = Math.sqrt(
            Math.pow(currentPlayerX - context.lastPlayerX, 2) + Math.pow(currentPlayerZ - context.lastPlayerZ, 2)
        );
        if (movedDistance < 0.05) {
            context.stuckCounter++;
        } else {
            context.stuckCounter = 0;
            context.lastPlayerX = currentPlayerX;
            context.lastPlayerZ = currentPlayerZ;
        }

        if (context.walkTicks > config.getMaxWalkTicks() || context.stuckCounter > config.getMaxStuckTicks()) {
            if (horizontalLength < 1.5) {
                inputHelper.releaseAllKeys();
                context.waitTicks = config.getMoveWaitTicks();
                context.movingWait = true;
                context.isAdjacentBlock = false;
                cameraHelper.calculateTargetLook(targetPos);
                context.state = MiningState.FACING_BLOCK;
                context.walkTicks = 0;
                context.stuckCounter = 0;
                context.walkRetryCount = 0;
                notificationService.logDebug("已足够接近目标，开始转向");
                return;
            }
            context.walkRetryCount++;
            if (context.walkRetryCount <= config.getMaxWalkRetries()) {
                notificationService.logDebug("行走超时或卡住，第 " + context.walkRetryCount + " 次重试");
                inputHelper.releaseAllKeys();
                context.walkTicks = -10;
                context.stuckCounter = 0;
                context.lastPlayerX = client.player.getX();
                context.lastPlayerZ = client.player.getZ();
                return;
            }
            notificationService.logDebug("行走超时或卡住，重试 " + config.getMaxWalkRetries() + " 次后仍失败，跳过当前方块");
            inputHelper.releaseAllKeys();
            context.walkRetryCount = 0;
            completionService.onBlockSkipped(targetPos);
            context.walkTicks = 0;
            context.stuckCounter = 0;
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.state = MiningState.FINDING_BLOCK;
            return;
        }

        if (horizontalLength < config.getArriveThreshold()) {
            if (targetPos.getY() > client.player.getY()) {
                BlockPos targetTopPos = targetPos.up();
                BlockPos targetAboveTopPos = targetPos.up(2);
                boolean hasSpaceOnTop = client.world.getBlockState(targetTopPos).isAir() &&
                                         client.world.getBlockState(targetAboveTopPos).isAir();

                if (hasSpaceOnTop && client.player.isOnGround() && context.jumpCooldown == 0) {
                    inputHelper.setKeyPressed(client.options.jumpKey, true);
                    context.jumpCooldown = 10;
                    notificationService.logDebug("跳跃到目标方块顶部");
                    return;
                } else if (!hasSpaceOnTop) {
                    notificationService.logDebug("目标方块上方没有足够空间，尝试直接挖掘");
                }
            }
            inputHelper.releaseAllKeys();
            context.waitTicks = config.getMoveWaitTicks();
            context.movingWait = true;
            context.isAdjacentBlock = false;
            cameraHelper.calculateTargetLook(targetPos);
            context.state = MiningState.FACING_BLOCK;
            context.walkTicks = 0;
            context.stuckCounter = 0;
            context.walkRetryCount = 0;
            notificationService.logDebug("到达目标位置，准备转向");
            return;
        }

        float walkYaw = (float) Math.atan2(dz, dx) * (180.0F / (float) Math.PI) - 90.0F;
        float smoothedYaw = cameraHelper.smoothYawTowards(client.player.getYaw(), walkYaw, 15.0f);
        client.player.setYaw(smoothedYaw);

        boolean needJump = checkObstacleInFront(dx, dz) && client.player.isOnGround() && context.jumpCooldown == 0;
        boolean wouldFall = targetPos.getY() <= client.player.getY() && checkFallDanger(dx, dz);

        if (wouldFall && !needJump) {
            inputHelper.releaseAllKeys();
            completionService.onBlockSkipped(targetPos);
            context.walkTicks = 0;
            context.stuckCounter = 0;
            if (!areaIterator.advancePosition()) {
                completionService.completeMining();
                return;
            }
            context.state = MiningState.FINDING_BLOCK;
            return;
        }

        inputHelper.setKeyPressed(client.options.forwardKey, true);

        if (needJump) {
            inputHelper.setKeyPressed(client.options.jumpKey, true);
            context.jumpCooldown = 10;
            notificationService.logDebug("跳跃越过障碍");
        } else {
            inputHelper.setKeyPressed(client.options.jumpKey, false);
        }
    }

    public boolean checkObstacleInFront(double dx, double dz) {
        MinecraftClient client = context.client;
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

                if (footPos.getX() < context.minX || footPos.getX() > context.maxX || 
                    footPos.getZ() < context.minZ || footPos.getZ() > context.maxZ) {
                    continue;
                }

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

    public boolean checkFallDanger(double dx, double dz) {
        MinecraftClient client = context.client;
        MiningConfig config = MiningConfig.getInstance();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return false;

        double stepX = dx / length;
        double stepZ = dz / length;

        BlockPos playerPos = new BlockPos(
            (int) Math.floor(client.player.getX()),
            (int) Math.floor(client.player.getY()),
            (int) Math.floor(client.player.getZ())
        );

        int playerGroundY = findGroundY(playerPos);

        for (int dist = 1; dist <= 2; dist++) {
            BlockPos frontPos = new BlockPos(
                (int) Math.floor(client.player.getX() + stepX * dist),
                (int) Math.floor(client.player.getY()),
                (int) Math.floor(client.player.getZ() + stepZ * dist)
            );

            if (frontPos.getX() < context.minX || frontPos.getX() > context.maxX || 
                frontPos.getZ() < context.minZ || frontPos.getZ() > context.maxZ) {
                continue;
            }

            int frontGroundY = findGroundY(frontPos);

            int heightDiff = playerGroundY - frontGroundY;
            if (heightDiff >= config.getFallDangerThreshold()) {
                return true;
            }
        }
        return false;
    }

    public int findGroundY(BlockPos pos) {
        MinecraftClient client = context.client;
        for (int y = pos.getY(); y >= pos.getY() - 6; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (!client.world.getBlockState(checkPos).isAir()) {
                return y;
            }
        }
        return pos.getY() - 6;
    }
}