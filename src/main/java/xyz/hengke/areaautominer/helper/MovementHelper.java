package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.state.MovementState;
import xyz.hengke.areaautominer.model.DangerType;
import xyz.hengke.areaautominer.model.PathMode;
import xyz.hengke.areaautominer.model.WalkResult;
import xyz.hengke.areaautominer.service.NotificationService;

import java.util.ArrayList;
import java.util.List;

public class MovementHelper {
    private static final double STUCK_MOVEMENT_THRESHOLD = 0.05;
    private static final double STUCK_ANCHOR_RESET_DISTANCE = 0.5;
    private static final double CLOSE_ENOUGH_DISTANCE = 1.5;
    private static final double NODE_ARRIVE_THRESHOLD = 1.5;
    private static final int RETRY_DELAY_TICKS = -10;
    private static final int UNLOADED_RETRY_DELAY_TICKS = -20;
    private static final int JUMP_COOLDOWN_TICKS = 10;
    private static final int JUMP_COOLDOWN_RETRY_TICKS = 15;
    private static final float TURN_BEFORE_WALK_THRESHOLD = 30.0f;
    private static final double SHORT_HOP_MAX_DISTANCE = 6.0;
    private static final int SHORT_HOP_MAX_DY = 1;
    private static final int GREEDY_FALLBACK_STUCK_TICKS = 6;

    private final MinecraftClient client;
    private final MiningConfig config;
    private final MovementState movement;
    private final InputHelper inputHelper;
    private final CameraHelper cameraHelper;
    private final NotificationService notificationService;
    private final PathfindingHelper pathfindingHelper;

    public MovementHelper(MinecraftClient client, MiningConfig config, MovementState movement,
                          InputHelper inputHelper, CameraHelper cameraHelper,
                          NotificationService notificationService, PathfindingHelper pathfindingHelper) {
        this.client = client;
        this.config = config;
        this.movement = movement;
        this.inputHelper = inputHelper;
        this.cameraHelper = cameraHelper;
        this.notificationService = notificationService;
        this.pathfindingHelper = pathfindingHelper;
    }

    public WalkResult walkToBlock(BlockPos targetPos) {
        tickJumpCooldown();
        if (checkDangerAndSkip(targetPos)) return WalkResult.SKIPPED;

        movement.setWalkTicks(movement.getWalkTicks() + 1);

        if (movement.getWalkTicks() <= 0) {
            inputHelper.releaseAllKeys();
            return WalkResult.ONGOING;
        }

        updateStuckDetection();

        WalkResult timeoutOrStuck = checkTimeoutOrStuck(targetPos);
        if (timeoutOrStuck != WalkResult.ONGOING) return timeoutOrStuck;

        if (checkArrive(targetPos)) return WalkResult.ARRIVED;

        return followPath(targetPos);
    }


    private void tickJumpCooldown() {
        if (movement.getJumpCooldown() > 0) {
            movement.setJumpCooldown(movement.getJumpCooldown() - 1);
        }
    }

    private boolean checkDangerAndSkip(BlockPos targetPos) {
        if (DangerChecker.evaluate(client, targetPos) == DangerType.NONE) return false;
        inputHelper.releaseAllKeys();
        notificationService.logDebug("检测到危险环境（岩浆/虚空），跳过方块: " + targetPos);
        movement.resetWalkSession();
        return true;
    }

    private void updateStuckDetection() {
        Vec3d playerPos = SpatialMath.getPlayerPos(client);
        if (movement.isTurningInPlace()) {
            movement.setLastPlayerX(playerPos.x);
            movement.setLastPlayerZ(playerPos.z);
            return;
        }
        double movedDistance = Math.sqrt(SpatialMath.calculateHorizontalDistanceSquared(
                playerPos.x, playerPos.z,
                movement.getLastPlayerX(), movement.getLastPlayerZ()));
        if (movedDistance < STUCK_MOVEMENT_THRESHOLD) {
            movement.setStuckCounter(movement.getStuckCounter() + 1);
        } else if (movedDistance >= STUCK_ANCHOR_RESET_DISTANCE) {
            resetStuckAnchor();
        }
    }

    private WalkResult checkTimeoutOrStuck(BlockPos targetPos) {
        if (movement.getWalkTicks() < config.maxWalkTicks
                && movement.getStuckCounter() < config.maxStuckTicks) {
            return WalkResult.ONGOING;
        }
        if (horizontalDistanceTo(targetPos) < CLOSE_ENOUGH_DISTANCE) {
            arriveAndFace(targetPos);
            return WalkResult.ARRIVED;
        } else {
            return triggerRetryOrSkip(targetPos, "行走超时或卡住", RETRY_DELAY_TICKS);
        }
    }

    private boolean checkArrive(BlockPos targetPos) {
        if (horizontalDistanceTo(targetPos) >= config.arriveThreshold) {
            return false;
        }
        boolean needJumpToReach = targetPos.getY() > client.player.getY()
                && !ReachChecker.isBlockWithinReach(client, targetPos, config);
        if (needJumpToReach) {
            BlockPos targetTopPos = targetPos.up();
            BlockPos targetAboveTopPos = targetPos.up(2);
            boolean hasSpaceOnTop = client.world.getBlockState(targetTopPos).isAir()
                    && client.world.getBlockState(targetAboveTopPos).isAir();

            if (movement.isJumpInProgress()) {
                inputHelper.setKeyPressed(client.options.jumpKey, false);
                if (!client.player.isOnGround()) {
                    return true;
                }
                movement.setJumpInProgress(false);
                notificationService.logDebug("跳跃落地，准备转向");
                arriveAndFace(targetPos);
                return true;
            }
            if (hasSpaceOnTop && client.player.isOnGround() && movement.getJumpCooldown() == 0) {
                inputHelper.setKeyPressed(client.options.jumpKey, true);
                movement.setJumpCooldown(JUMP_COOLDOWN_TICKS);
                movement.setJumpInProgress(true);
                notificationService.logDebug("跳跃到目标方块顶部");
                return true;
            }
            if (!hasSpaceOnTop) {
                notificationService.logDebug("目标方块上方没有足够空间，改为站旁转向后挖掘");
            }
        }
        arriveAndFace(targetPos);
        return true;
    }

    private WalkResult followPath(BlockPos targetPos) {
        Path path = movement.getCurrentPath();
        if (path == null || path.isFinished()) {
            if (!movement.isShortHopBlocked() && isShortHopCandidate(targetPos)) {
                return walkStraight(targetPos);
            }
            path = pathfindingHelper.computePath(targetPos);
            movement.setCurrentPath(path);
            if (path == null) {
                boolean unloaded = !client.world.isPosLoaded(targetPos);
                return triggerRetryOrSkip(targetPos,
                        unloaded ? "目标区块未加载" : "目标不可达",
                        unloaded ? UNLOADED_RETRY_DELAY_TICKS : RETRY_DELAY_TICKS);
            }
        }

        BlockPos nodePos = path.getCurrentNodePos();
        if (nodePos == null) {
            movement.setCurrentPath(null);
            return WalkResult.ONGOING;
        }
        if (horizontalDistanceTo(nodePos) < NODE_ARRIVE_THRESHOLD) {
            path.next();
            if (path.isFinished()) {
                movement.setCurrentPath(null);
                arriveAndFace(targetPos);
                return WalkResult.ARRIVED;
            }
            nodePos = path.getCurrentNodePos();
            if (nodePos == null) {
                movement.setCurrentPath(null);
                return WalkResult.ONGOING;
            }
        }

        Vec3d playerPos = SpatialMath.getPlayerPos(client);
        float walkYaw = SpatialMath.calculateYawTo(
                playerPos.x, playerPos.z,
                SpatialMath.centerX(nodePos), SpatialMath.centerZ(nodePos));
        float yawDiff = SpatialMath.normalizeYawDiff(walkYaw - client.player.getYaw());

        if (turnInPlaceIfNeeded(yawDiff)) return WalkResult.ONGOING;

        boolean needJump = nodePos.getY() > client.player.getBlockPos().getY()
                && client.player.isOnGround()
                && movement.getJumpCooldown() == 0;

        inputHelper.setKeyPressed(client.options.forwardKey, true);
        if (needJump) {
            inputHelper.setKeyPressed(client.options.jumpKey, true);
            movement.setJumpCooldown(JUMP_COOLDOWN_TICKS);
            notificationService.logDebug("沿路径跳跃上坡");
        } else {
            inputHelper.setKeyPressed(client.options.jumpKey, false);
        }
        return WalkResult.ONGOING;
    }
    private boolean isShortHopCandidate(BlockPos targetPos) {
        if (targetPos == null || client.world == null) return false;
        if (!client.world.isPosLoaded(targetPos)) return false;
        Vec3d playerPos = SpatialMath.getPlayerPos(client);
        double dx = SpatialMath.centerX(targetPos) - playerPos.x;
        double dz = SpatialMath.centerZ(targetPos) - playerPos.z;
        if (Math.sqrt(dx * dx + dz * dz) > SHORT_HOP_MAX_DISTANCE) return false;
        if (Math.abs(targetPos.getY() - client.player.getBlockPos().getY()) > SHORT_HOP_MAX_DY) return false;
        return lineOfSightClear(targetPos);
    }

    private boolean lineOfSightClear(BlockPos targetPos) {
        Vec3d target = SpatialMath.center(targetPos);
        Vec3d eye = SpatialMath.getPlayerEyePos(client);
        if (!ReachChecker.isLineClear(client, eye, target, targetPos)) return false;
        if (targetPos.getY() >= client.player.getBlockPos().getY()) {
            Vec3d playerPos = SpatialMath.getPlayerPos(client);
            Vec3d feet = new Vec3d(playerPos.x, playerPos.y + 0.2, playerPos.z);
            Vec3d lowTarget = new Vec3d(SpatialMath.centerX(targetPos), targetPos.getY() + 0.2, SpatialMath.centerZ(targetPos));
            if (!ReachChecker.isLineClear(client, feet, lowTarget, targetPos)) return false;
        }
        return true;
    }
    private WalkResult walkStraight(BlockPos targetPos) {
        movement.setShortHopTarget(targetPos);

        Vec3d playerPos = SpatialMath.getPlayerPos(client);
        float walkYaw = SpatialMath.calculateYawTo(
                playerPos.x, playerPos.z,
                SpatialMath.centerX(targetPos), SpatialMath.centerZ(targetPos));
        float yawDiff = SpatialMath.normalizeYawDiff(walkYaw - client.player.getYaw());

        if (turnInPlaceIfNeeded(yawDiff)) return WalkResult.ONGOING;

        boolean needJump = targetPos.getY() > client.player.getBlockPos().getY()
                && client.player.isOnGround()
                && movement.getJumpCooldown() == 0;
        inputHelper.setKeyPressed(client.options.forwardKey, true);
        if (needJump) {
            inputHelper.setKeyPressed(client.options.jumpKey, true);
            movement.setJumpCooldown(JUMP_COOLDOWN_TICKS);
            notificationService.logDebug("短距离直走上坡");
        } else {
            inputHelper.setKeyPressed(client.options.jumpKey, false);
        }

        if (movement.getStuckCounter() >= GREEDY_FALLBACK_STUCK_TICKS) {
            movement.setShortHopBlocked(true);
            notificationService.logDebug("短距离直走受阻，转入 A* 寻路");
        }
        return WalkResult.ONGOING;
    }
    private boolean turnInPlaceIfNeeded(float yawDiff) {
        if (Math.abs(yawDiff) <= TURN_BEFORE_WALK_THRESHOLD) {
            movement.setTurningInPlace(false);
            return false;
        }
        inputHelper.setKeyPressed(client.options.forwardKey, false);
        inputHelper.setKeyPressed(client.options.jumpKey, false);
        movement.setTurningInPlace(true);
        Vec3d playerPos = SpatialMath.getPlayerPos(client);
        movement.setLastPlayerX(playerPos.x);
        movement.setLastPlayerZ(playerPos.z);
        return true;
    }

    private double horizontalDistanceTo(BlockPos pos) {
        Vec3d playerPos = SpatialMath.getPlayerPos(client);
        return Math.sqrt(SpatialMath.calculateHorizontalDistanceSquared(
                playerPos.x, playerPos.z,
                SpatialMath.centerX(pos), SpatialMath.centerZ(pos)));
    }

    private void resetStuckAnchor() {
        movement.setStuckCounter(0);
        Vec3d playerPos = SpatialMath.getPlayerPos(client);
        movement.setLastPlayerX(playerPos.x);
        movement.setLastPlayerZ(playerPos.z);
    }


    private WalkResult triggerRetryOrSkip(BlockPos targetPos, String reason, int retryDelayTicks) {
        movement.setWalkRetryCount(movement.getWalkRetryCount() + 1);

        if (movement.getWalkRetryCount() <= config.maxWalkRetries) {
            notificationService.logDebug(reason + "，第 " + movement.getWalkRetryCount() + " 次重试（重算路径）");
            inputHelper.releaseAllKeys();
            movement.setWalkTicks(retryDelayTicks);
            resetStuckAnchor();
            movement.setCurrentPath(null);
            movement.setTurningInPlace(false);
            if (movement.getWalkRetryCount() > 1 && client.player.isOnGround() && movement.getJumpCooldown() == 0) {
                inputHelper.setKeyPressed(client.options.jumpKey, true);
                movement.setJumpCooldown(JUMP_COOLDOWN_RETRY_TICKS);
                notificationService.logDebug("重试时尝试跳跃");
            }
            return WalkResult.ONGOING;
        }
        notificationService.logDebug(reason + "，重试 " + config.maxWalkRetries + " 次仍失败，跳过方块");
        inputHelper.releaseAllKeys();
        movement.resetWalkSession();
        return WalkResult.SKIPPED;
    }

    private void arriveAndFace(BlockPos targetPos) {
        inputHelper.releaseAllKeys();
        movement.setWaitTicks(config.moveWaitTicks);
        movement.setMovingWait(true);
        movement.resetWalkSession();
        cameraHelper.calculateTargetLook(targetPos);
        notificationService.logDebug("到达目标位置，准备转向");
    }

    public List<BlockPos> getDebugPathNodes() {
        List<BlockPos> nodes = new ArrayList<>();
        Path path = movement.getCurrentPath();
        if (path != null) {
            for (int i = 0; i < path.getLength(); i++) {
                nodes.add(path.getNodePos(i));
            }
        } else {
            BlockPos hopTarget = movement.getShortHopTarget();
            if (hopTarget != null) {
                nodes.add(hopTarget);
            }
        }
        return nodes;
    }

    public PathMode getDebugPathMode() {
        if (movement.getCurrentPath() != null) return PathMode.A_STAR;
        if (movement.getShortHopTarget() != null) return PathMode.GREEDY;
        return PathMode.NONE;
    }
}
