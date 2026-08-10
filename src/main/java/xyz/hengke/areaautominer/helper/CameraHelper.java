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
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.NotificationService;

/**
 * 视角控制器 —— 帧率无关指数平滑 (Damped Exponential Lerp) + 物理位移鼠标介入检测。
 *
 * <p>核心公式: new = current + (target - current) * (1 - exp(-k * dt))，dt 单位为秒。
 * 天然具备「远快近慢」的近场减速、单调收敛（无 overshoot）、帧率无关特性，
 * 替代了原实现中 APPROACH_SLOW_ZONE / CONVERGE_CONFIRM_TICKS / MAX_FRAME_DELTA 等
 * 全部过程式状态。</p>
 *
 * <p>与纯数学版的差异（相对可行性分析报告 §5 的落地修正）:
 * <ol>
 *   <li>帧时间: {@code getRenderTickCounter().getTickProgress()} 差分换算为秒
 *       （1.21.11 的 MinecraftClient 无 getLastFrameDuration 暴露），并钳制上限
 *       防极端卡顿单帧瞬移;</li>
 *   <li>峰值角速度: 指数步长叠加显式上限 {@code vMax * dt}（120°/s，与原 6°/tick 对齐），
 *       防止启动瞬间"猛甩"（k*diff 可达 720°/s）;</li>
 *   <li>鼠标介入检测: 帧间鼠标物理位移差分 + 2px 阈值（公开 API，零新增依赖；
 *       fabric-api 0.141.5 实证无 MouseInputEvents 事件，事件驱动方案不可用）;</li>
 *   <li>收敛判定: 指数平滑单调收敛，单次低于阈值即完成，无需连续防抖。</li>
 * </ol></p>
 *
 * <p>逻辑层(tick):{@link #faceBlock()} 只做移动稳定等待、收敛判定、超时逃生与状态切换;
 * 渲染层(帧):{@link #smoothFrame()} 在 {@code WorldRenderEvents.START_MAIN} 每帧回调中推进视角。
 * 覆盖 FACING_BLOCK（yaw+pitch）与 WALKING_TO_BLOCK（仅 yaw，目标为当前寻路节点方向）两个场景。</p>
 */
public class CameraHelper {

    // ---- 数学参数（替代原 11 个过程式常量）----
    /** 面向方块收敛刚度 k(秒⁻¹)：叠加 vMax 后仅控制收尾段（剩余 <16° 由指数接管），
     *  90° 全程 ≈0.85s（匀速段 0.62s + 指数尾段 0.25s） */
    private static final float FACE_K = 8.0F;
    /** 行走转向刚度：略低以保持路径跟踪柔和 */
    private static final float WALK_K = 5.0F;
    /** 最大角速度(°/s)：与原 6°/tick=120°/s 对齐，钳制峰值转速防"猛甩" */
    private static final float MAX_YAW_SPEED = 120.0F;
    /** Pitch 行程通常远小于 yaw，可略快 */
    private static final float MAX_PITCH_SPEED = 160.0F;
    /** 收敛判定阈值(度)：单次低于即完成（指数平滑单调收敛，无需防抖） */
    private static final float CONVERGE_EPS = 1.5F;
    /** 帧时间安全钳制(秒)：低于 ~20FPS 时按 20FPS 等效推进，防极端卡顿单帧瞬移 */
    private static final float MAX_FRAME_DT = 0.05F;
    /** 超时后剩余偏差仍大于此值 → 判定干扰过大,重新开始逼近;否则强制完成 */
    private static final float HARD_FAIL_EPS = 25.0F;
    /** 鼠标介入检测阈值(像素/帧)：1px ≈ 0.1~0.3°(灵敏度相关)，2px 滤行走微抖、
     *  不误伤主动甩视角；mod 自身 setYaw 不产生鼠标物理位移，天然免疫误判 */
    private static final double MOUSE_OVERRIDE_THRESHOLD_PX = 2.0;

    private final MinecraftClient client;
    private final MiningConfig config;
    private final SessionState session;
    private final MovementState movement;
    private final FacingState facing;
    private final InputHelper inputHelper;
    private final NotificationService notificationService;

    /** 本会话已进行 tick 数（硬超时用） */
    private int faceTicks = 0;
    /** 上一帧鼠标物理位置（介入检测基准，NaN=未初始化） */
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    /** 上一帧的 tick 插值因子（计算帧时间增量） */
    private float lastTickProgress = 0.0F;

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

    // ==================== 对外 API ====================

    /**
     * 开始一次转向会话:释放按键、重置会话计数。
     * 由 BlockFinder / BreakingHelper / MovementHelper 在进入 FACING_BLOCK 时调用。
     */
    public void beginFacing() {
        inputHelper.releaseAllKeys();
        faceTicks = 0;
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
    }

    /**
     * 每 tick 视角状态机(FACING_BLOCK 状态)。
     * 只做移动稳定等待、收敛判定、超时逃生与状态切换;
     * 视角的实际推进由渲染帧回调 {@link #smoothFrame()} 完成。
     */
    public void faceBlock() {
        if (client.player == null) return;

        // ---- 移动稳定等待期:只倒数,不转向 ----
        if (movement.isMovingWait()) {
            movement.setWaitTicks(movement.getWaitTicks() - 1);
            if (movement.getWaitTicks() <= 0) {
                movement.setMovingWait(false);
                beginFacing();
                notificationService.logDebug("移动稳定完成，开始转向");
            }
            return;
        }

        // ---- 防御:若外部直接 setState(FACING_BLOCK) 未显式 beginFacing,首 tick 自动初始化 ----
        if (faceTicks == 0) beginFacing();
        faceTicks++;

        float yawDiff = SpatialMath.yawDiffTo(facing, client);
        float pitchDiff = SpatialMath.pitchDiffTo(facing, client);

        // ---- 收敛判定:指数平滑单调收敛,单次低于阈值即完成(无需连续防抖) ----
        if (yawDiff <= CONVERGE_EPS && pitchDiff <= CONVERGE_EPS) {
            finishFacing();
            return;
        }

        // ---- 硬超时逃生:持续被外部干扰时的出口(以真实视角为新起点,无跳变) ----
        if (faceTicks >= config.maxFaceTicks) {
            if (yawDiff > HARD_FAIL_EPS || pitchDiff > HARD_FAIL_EPS) {
                notificationService.logDebug("转向超时且偏差过大(" + Math.round(yawDiff)
                        + "°)，重置会话重新逼近");
                beginFacing();
            } else {
                notificationService.logDebug("转向超时但偏差可接受，直接开始挖掘");
                finishFacing();
            }
        }
    }

    /**
     * 每帧视角推进(注册到 WorldRenderEvents.START_MAIN)。
     *
     * <p>指数步长 + 显式角速度上限: step = clamp((target-current)*alpha, ±vMax*dt)。
     * 玩家移动鼠标时本帧跳过(完全跟手),松手后从当前位置继续逼近。</p>
     */
    public void smoothFrame() {
        if (client.player == null) return;
        if (!session.isMining()) return;
        MiningState state = session.getState();
        if (state != MiningState.FACING_BLOCK && state != MiningState.WALKING_TO_BLOCK) return;
        if (movement.isMovingWait()) return;

        // ---- 鼠标介入检测（帧间物理位移，像素）：玩家动鼠标则本帧不推进，完全跟手;
        //      自动转向不产生鼠标物理位移,不会误判;松手后从当前位置继续逼近 ----
        double mx = client.mouse.getX();
        double my = client.mouse.getY();
        if (!Double.isNaN(lastMouseX)) {
            if (Math.abs(mx - lastMouseX) + Math.abs(my - lastMouseY) > MOUSE_OVERRIDE_THRESHOLD_PX) {
                lastMouseX = mx;
                lastMouseY = my;
                return;
            }
        }
        lastMouseX = mx;
        lastMouseY = my;

        // ---- 帧时间(秒):getTickProgress 差分 = 帧间隔 tick 数 → /20;
        //      钳制上限,卡顿恢复时按 20FPS 等效推进,剩余由后续帧补 ----
        float progress = client.getRenderTickCounter().getTickProgress(true);
        float frameDelta = progress - lastTickProgress;
        if (frameDelta <= 0.0F) frameDelta = progress;   // 新 tick 开始,progress 回绕(近似)
        lastTickProgress = progress;
        float dt = frameDelta / 20.0F;
        if (dt > MAX_FRAME_DT) dt = MAX_FRAME_DT;

        // ---- 指数平滑因子 + 角速度上限(峰值 = min(k*diff, vMax)) ----
        float k = (state == MiningState.FACING_BLOCK) ? FACE_K : WALK_K;
        float alpha = 1.0F - (float) Math.exp(-k * dt);
        float maxYawStep = MAX_YAW_SPEED * dt;

        if (state == MiningState.FACING_BLOCK) {
            // 面向方块:yaw + pitch
            float currentYaw = client.player.getYaw();
            float currentPitch = client.player.getPitch();
            float yawDiff = SpatialMath.normalizeYawDiff(facing.getTargetYaw() - currentYaw);
            float pitchDiff = facing.getTargetPitch() - currentPitch;

            client.player.setYaw(currentYaw + clampStep(yawDiff * alpha, maxYawStep));
            client.player.setPitch(MathHelper.clamp(
                    currentPitch + clampStep(pitchDiff * alpha, MAX_PITCH_SPEED * dt), -90.0F, 90.0F));
        } else {
            // 行走转向：仅 yaw，目标 = 当前寻路节点方向；
            // 贪心直走（无 Path）时回退到 MovementState.shortHopTarget，保证同样帧级平滑
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

    /**
     * 收敛 → 切入挖掘状态（方案 C2：挖掘会话初始化 beginBreakSession 由 Controller
     * 在状态转移时统一执行，本类仅做状态机转移，不再跨域写 BreakingState）。
     */
    private void finishFacing() {
        session.setState(MiningState.BREAKING);
        notificationService.logDebug("转向完成，开始挖掘");
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

    /** 对称钳制:保留符号,限制绝对值(指数步长与角速度上限的交集) */
    private static float clampStep(float value, float max) {
        return MathHelper.clamp(value, -max, max);
    }
}
