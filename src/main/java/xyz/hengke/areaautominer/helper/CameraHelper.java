package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.state.FacingState;
import xyz.hengke.areaautominer.state.MovementState;
import xyz.hengke.areaautominer.state.SessionState;
import xyz.hengke.areaautominer.model.FaceResult;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.NotificationService;

public class CameraHelper {

    private static final float FACE_K = 8.0F;
    private static final float WALK_K = 5.0F;
    private static final float MAX_YAW_SPEED = 120.0F;
    private static final float MAX_PITCH_SPEED = 160.0F;
    private static final float CONVERGE_EPS = 1.5F;
    private static final float MAX_FRAME_DT = 0.05F;
    private static final float HARD_FAIL_EPS = 25.0F;
    private static final double MOUSE_OVERRIDE_THRESHOLD_PX = 2.0;

    private final MinecraftClient client;
    private final MiningConfig config;
    private final SessionState session;
    private final MovementState movement;
    private final FacingState facing;
    private final InputHelper inputHelper;
    private final NotificationService notificationService;

    public CameraHelper(MinecraftClient client, MiningConfig config, SessionState session, MovementState movement,
                        FacingState facing,
                        InputHelper inputHelper, NotificationService notificationService) {
        this.client = client;
        this.config = config;
        this.session = session;
        this.movement = movement;
        this.facing = facing;
        this.inputHelper = inputHelper;
        this.notificationService = notificationService;
    }

    public void beginFacing() {
        inputHelper.releaseAllKeys();
        facing.beginFacingSession();
    }
    public FaceResult faceBlock() {
        if (client.player == null) return FaceResult.ONGOING;
        if (movement.isMovingWait()) {
            movement.setWaitTicks(movement.getWaitTicks() - 1);
            if (movement.getWaitTicks() <= 0) {
                movement.setMovingWait(false);
                beginFacing();
                notificationService.logDebug("移动稳定完成，开始转向");
            }
            return FaceResult.ONGOING;
        }

        if (facing.getFaceTicks() == 0) beginFacing();
        facing.setFaceTicks(facing.getFaceTicks() + 1);

        float yawDiff = SpatialMath.yawDiffTo(facing, client);
        float pitchDiff = SpatialMath.pitchDiffTo(facing, client);

        if (yawDiff <= CONVERGE_EPS && pitchDiff <= CONVERGE_EPS) {
            notificationService.logDebug("转向完成，开始挖掘");
            return FaceResult.CONVERGED;
        }

        if (facing.getFaceTicks() >= config.maxFaceTicks) {
            if (yawDiff > HARD_FAIL_EPS || pitchDiff > HARD_FAIL_EPS) {
                notificationService.logDebug("转向超时且偏差过大(" + Math.round(yawDiff)
                        + "°)，重置会话重新逼近");
                beginFacing();
            } else {
                notificationService.logDebug("转向超时但偏差可接受，直接开始挖掘");
                return FaceResult.CONVERGED;
            }
        }
        return FaceResult.ONGOING;
    }

    public void smoothFrame() {
        if (client.player == null) return;
        if (!session.isMining()) return;
        MiningState state = session.getState();
        if (state != MiningState.FACING_BLOCK && state != MiningState.WALKING_TO_BLOCK) return;
        if (movement.isMovingWait()) return;

        double mx = client.mouse.getX();
        double my = client.mouse.getY();
        if (!Double.isNaN(facing.getLastMouseX())) {
            if (Math.abs(mx - facing.getLastMouseX()) + Math.abs(my - facing.getLastMouseY()) > MOUSE_OVERRIDE_THRESHOLD_PX) {
                facing.setLastMouseX(mx);
                facing.setLastMouseY(my);
                return;
            }
        }
        facing.setLastMouseX(mx);
        facing.setLastMouseY(my);
        float progress = client.getRenderTickCounter().getTickProgress(true);
        float frameDelta = progress - facing.getLastTickProgress();
        if (frameDelta <= 0.0F) frameDelta = progress;
        facing.setLastTickProgress(progress);
        float dt = frameDelta / 20.0F;
        if (dt > MAX_FRAME_DT) dt = MAX_FRAME_DT;
        float k = (state == MiningState.FACING_BLOCK) ? FACE_K : WALK_K;
        float alpha = 1.0F - (float) Math.exp(-k * dt);
        float maxYawStep = MAX_YAW_SPEED * dt;

        if (state == MiningState.FACING_BLOCK) {
            float currentYaw = client.player.getYaw();
            float currentPitch = client.player.getPitch();
            float yawDiff = SpatialMath.normalizeYawDiff(facing.getTargetYaw() - currentYaw);
            float pitchDiff = facing.getTargetPitch() - currentPitch;

            client.player.setYaw(currentYaw + clampStep(yawDiff * alpha, maxYawStep));
            client.player.setPitch(MathHelper.clamp(
                    currentPitch + clampStep(pitchDiff * alpha, MAX_PITCH_SPEED * dt), -90.0F, 90.0F));
        } else {
            Path path = movement.getCurrentPath();
            BlockPos nodePos = null;
            if (path != null && !path.isFinished()) {
                nodePos = path.getCurrentNodePos();
            }
            if (nodePos == null) nodePos = movement.getShortHopTarget();
            if (nodePos == null) return;
            Vec3d playerPos = SpatialMath.getPlayerPos(client);
            float walkYaw = SpatialMath.calculateYawTo(
                    playerPos.x, playerPos.z,
                    SpatialMath.centerX(nodePos), SpatialMath.centerZ(nodePos));
            float yawDiff = SpatialMath.normalizeYawDiff(walkYaw - client.player.getYaw());
            client.player.setYaw(client.player.getYaw() + clampStep(yawDiff * alpha, maxYawStep));
        }
    }

    // ==================== 目标角度计算 ====================
    public void calculateTargetLook(BlockPos targetPos) {
        Direction visibleFace = ReachChecker.getVisibleFace(client, targetPos);
        if (visibleFace != null) {
            calculateTargetLookToFace(targetPos, visibleFace);
        } else {
            calculateTargetLookToPoint(SpatialMath.centerX(targetPos), SpatialMath.centerY(targetPos), SpatialMath.centerZ(targetPos));
        }
    }

    public void calculateTargetLookToFace(BlockPos targetPos, Direction face) {
        double x = SpatialMath.centerX(targetPos);
        double y = SpatialMath.centerY(targetPos);
        double z = SpatialMath.centerZ(targetPos);
        switch (face) {
            case UP:    y = targetPos.getY() + 0.9; break;
            case DOWN:  y = targetPos.getY() + 0.1; break;
            case EAST:  x = targetPos.getX() + 0.9; break;
            case WEST:  x = targetPos.getX() + 0.1; break;
            case SOUTH: z = targetPos.getZ() + 0.9; break;
            case NORTH: z = targetPos.getZ() + 0.1; break;
        }
        calculateTargetLookToPoint(x, y, z);
    }

    private void calculateTargetLookToPoint(double targetX, double targetY, double targetZ) {
        Vec3d playerPos = SpatialMath.getPlayerPos(client);
        double lookDx = targetX - playerPos.x;
        double lookDy = targetY - SpatialMath.getPlayerEyeY(client);
        double lookDz = targetZ - playerPos.z;
        facing.setTargetYaw(SpatialMath.calculateYawTo(
                playerPos.x, playerPos.z, targetX, targetZ));
        facing.setTargetPitch((float) -Math.atan2(lookDy, Math.sqrt(lookDx * lookDx + lookDz * lookDz)) * (180.0F / (float) Math.PI));
    }

    // ==================== 工具 ====================
    private static float clampStep(float value, float max) {
        return MathHelper.clamp(value, -max, max);
    }
}
