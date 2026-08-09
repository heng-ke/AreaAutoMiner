package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.state.BreakingState;
import xyz.hengke.areaautominer.context.state.FacingState;
import xyz.hengke.areaautominer.context.state.MovementState;
import xyz.hengke.areaautominer.context.state.SessionState;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.NotificationService;

/**
 * 视角控制器 —— 增量追踪器 + 帧级平滑。
 *
 * <p>逻辑层(tick):{@link #faceBlock()} 只做收敛判定、超时逃生与状态切换,不再直接写视角;
 * 渲染层(帧):{@link #smoothFrame()} 在 {@code WorldRenderEvents.START_MAIN} 每帧回调中,
 * 从真实当前视角出发,按「角速度 × 帧时间比例」向目标做增量逼近 —— tick 级角度步进被摊平到
 * 帧级(60FPS 下每帧仅约 0.3°),彻底消除"甩头/顿挫"。</p>
 *
 * <p>覆盖两个场景:FACING_BLOCK 面向方块(yaw + pitch)、WALKING_TO_BLOCK 行走转向(仅 yaw,
 * 目标为当前寻路节点方向,保持原 13.5°/tick 等效角速度以免重新引入绕圈)。</p>
 *
 * <p>鼠标输入优先:玩家主动移动鼠标时,本帧不推进,视角完全跟手;松手后从当前位置继续逼近。
 * 玩家动鼠标不再被 mod 抢视角,也消除了"跟手但抽搐"。</p>
 *
 * <p>依赖的状态对象:SessionState(会话开关/状态机)、MovementState(移动稳定等待/路径)、
 * FacingState(目标角度)、BreakingState(转向完成后的挖掘初始化)。</p>
 */
public class CameraHelper {

    // ---- 角速度与收敛(单位:度)----
    /** Yaw 每 tick 最大转角(≈120°/s,接近自然转头速度) */
    private static final float YAW_SPEED = 6.0F;
    /** Pitch 每 tick 最大转角(pitch 行程通常远小于 yaw,可略快) */
    private static final float PITCH_SPEED = 8.0F;
    /** 行走转向每 tick 最大转角(10°/tick≈200°/s;低于原 13.5 以降低单帧跳变,
     *  仍满足防绕圈:曲率半径 v/ω≈0.216/(10°→0.175rad)=1.24 格 < 节点阈值 1.5) */
    private static final float WALK_YAW_SPEED = 10.0F;
    /** 单帧最多推进的 tick 比例:clamp 帧时间增量,消除卡顿/低帧率时单帧跳变(剩余由后续帧补) */
    private static final float MAX_FRAME_DELTA = 0.5F;
    /** 玩家鼠标输入判定阈值(物理光标位移,像素):滑动窗口累积值超过它才视为玩家操作,
     *  行走时手握鼠标的微抖(单帧 0.5~1px)不会误判 */
    private static final double MOUSE_INPUT_EPS = 2.0;
    /** 鼠标位移累积窗的指数衰减因子:单帧微抖被平均掉,持续输入快速累积 */
    private static final double MOUSE_ACCUM_DECAY = 0.5;
    /** 判定收敛的剩余偏差(度):连续 CONVERGE_CONFIRM_TICKS 个 tick 满足即完成 */
    private static final float CONVERGE_EPS = 1.5F;
    /** 收敛防抖:连续满足收敛条件的 tick 数 */
    private static final int CONVERGE_CONFIRM_TICKS = 2;
    /** 超时后剩余偏差仍大于此值 → 判定干扰过大,重新开始逼近;否则强制完成 */
    private static final float HARD_FAIL_EPS = 25.0F;
    /** 近场减速带:剩余角度小于此值开始按比例减速,避免终点急停顿挫 */
    private static final float APPROACH_SLOW_ZONE = 12.0F;
    /** 近场减速的最小比例(防止无限趋近不收敛) */
    private static final float APPROACH_MIN_RATIO = 0.35F;

    private final MinecraftClient client;
    private final MiningConfig config;
    private final SessionState session;
    private final MovementState movement;
    private final FacingState facing;
    private final BreakingState breaking;
    private final InputHelper inputHelper;
    private final NotificationService notificationService;

    // ---- 转向会话状态(由 beginFacing() 重置;不放在 MiningContext,避免污染全局)----
    private int faceTicks = 0;        // 本会话已进行 tick 数(硬超时用)
    private int convergeTicks = 0;    // 连续满足收敛条件的 tick 数(防抖)

    // ---- 帧级推进状态 ----
    private float lastFrameProgress = 0.0F;   // 上一帧的 tick 插值因子(计算帧时间增量)
    private double lastMouseX = 0.0;          // 上一帧鼠标物理位置(输入检测)
    private double lastMouseY = 0.0;
    private double mouseMoveAccum = 0.0;      // 鼠标位移滑动窗口累积值(指数衰减,抗微抖)
    private boolean mouseBaselineReady = false;

    public CameraHelper(MinecraftClient client, MiningConfig config, SessionState session, MovementState movement,
                        FacingState facing, BreakingState breaking,
                        InputHelper inputHelper, NotificationService notificationService) {
        this.client = client;
        this.config = config;
        this.session = session;
        this.movement = movement;
        this.facing = facing;
        this.breaking = breaking;
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
        convergeTicks = 0;
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

        // 基于最新真实视角(由帧级推进更新)检查剩余偏差
        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();
        float yawDiff = SpatialHelper.normalizeYawDiff(facing.getTargetYaw() - currentYaw);
        float pitchDiff = facing.getTargetPitch() - currentPitch;

        // ---- 收敛判定:剩余角度 + 连续 2 tick 防抖 ----
        boolean converged = Math.abs(yawDiff) <= CONVERGE_EPS && Math.abs(pitchDiff) <= CONVERGE_EPS;
        if (converged) {
            if (++convergeTicks >= CONVERGE_CONFIRM_TICKS) {
                finishFacing();
                return;
            }
        } else {
            convergeTicks = 0;
        }

        // ---- 硬超时逃生:持续被外部干扰时的出口(以真实视角为新起点,无跳变) ----
        if (faceTicks >= config.maxFaceTicks) {
            if (Math.abs(yawDiff) > HARD_FAIL_EPS || Math.abs(pitchDiff) > HARD_FAIL_EPS) {
                notificationService.logDebug("转向超时且偏差过大(" + Math.round(Math.abs(yawDiff))
                        + "°)，重置会话重新逼近");
                beginFacing();
            } else {
                notificationService.logDebug("转向超时但偏差可接受，直接开始挖掘");
                finishFacing();
            }
        }
    }

    /**
     * 每帧视角推进(注册到 WorldRenderEvents.BEFORE_TERRAIN)。
     *
     * <p>仅在 FACING_BLOCK 且非移动等待时生效;玩家移动鼠标时本帧跳过(完全跟手),
     * 松手后从当前位置继续逼近。步长 = 角速度 × 帧时间比例,帧率无关、无步进。</p>
     */
    public void smoothFrame() {
        if (client.player == null) return;
        if (!session.isMining()) return;
        MiningState state = session.getState();
        if (state != MiningState.FACING_BLOCK && state != MiningState.WALKING_TO_BLOCK) return;
        if (movement.isMovingWait()) return;

        // ---- 鼠标输入优先(滑动窗口):玩家主动操作视角时完全跟手,不推进。
        // 行走时手握鼠标的微抖被指数衰减窗平均掉,不会造成"走走停停"的抽搐 ----
        double mx = client.mouse.getX();
        double my = client.mouse.getY();
        if (!mouseBaselineReady) {
            lastMouseX = mx;
            lastMouseY = my;
            mouseBaselineReady = true;
        }
        double frameMove = Math.abs(mx - lastMouseX) + Math.abs(my - lastMouseY);
        lastMouseX = mx;
        lastMouseY = my;
        mouseMoveAccum = mouseMoveAccum * MOUSE_ACCUM_DECAY + frameMove;
        if (mouseMoveAccum > MOUSE_INPUT_EPS) return;

        // ---- 帧时间增量:保证任意帧率下角速度恒定,并 clamp 单帧推进量
        // (卡顿/低帧率时单帧不跳大角度,剩余由后续帧补) ----
        float progress = client.getRenderTickCounter().getTickProgress(true);
        float frameDelta = progress - lastFrameProgress;
        if (frameDelta < 0.0F) frameDelta = progress;   // 新 tick 开始,progress 回绕
        lastFrameProgress = progress;
        if (frameDelta > MAX_FRAME_DELTA) frameDelta = MAX_FRAME_DELTA;

        // ---- 从真实当前视角出发,向目标走一步 ----
        if (state == MiningState.FACING_BLOCK) {
            // 面向方块:yaw + pitch
            float currentYaw = client.player.getYaw();
            float currentPitch = client.player.getPitch();
            float yawDiff = SpatialHelper.normalizeYawDiff(facing.getTargetYaw() - currentYaw);
            float pitchDiff = facing.getTargetPitch() - currentPitch;

            client.player.setYaw(currentYaw + stepTowards(yawDiff, YAW_SPEED * frameDelta));
            client.player.setPitch(MathHelper.clamp(currentPitch + stepTowards(pitchDiff, PITCH_SPEED * frameDelta), -90.0F, 90.0F));
        } else {
            // 行走转向:仅 yaw,目标 = 当前寻路节点方向(10°/tick,单帧 ≤5°)
            Path path = movement.getCurrentPath();
            if (path == null || path.isFinished()) return;
            BlockPos nodePos = path.getCurrentNodePos();
            if (nodePos == null) return;
            float walkYaw = SpatialHelper.calculateYawTo(
                    client.player.getX(), client.player.getZ(),
                    nodePos.getX() + 0.5, nodePos.getZ() + 0.5);
            float yawDiff = SpatialHelper.normalizeYawDiff(walkYaw - client.player.getYaw());
            client.player.setYaw(client.player.getYaw() + stepTowards(yawDiff, WALK_YAW_SPEED * frameDelta));
        }
    }

    /** 收敛 → 切入挖掘状态(统一出口,避免重复) */
    private void finishFacing() {
        breaking.setFirstBreakTick(true);
        breaking.setBreakTicks(0);
        session.setState(MiningState.BREAKING);
        notificationService.logDebug("转向完成，开始挖掘");
    }

    // ==================== 目标角度计算 ====================

    public void calculateTargetLook(BlockPos targetPos) {
        Direction visibleFace = SpatialHelper.getVisibleFace(client, targetPos);
        if (visibleFace != null) {
            calculateTargetLookToFace(targetPos, visibleFace);
        } else {
            calculateTargetLookToPoint(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        }
    }

    public void calculateTargetLookToFace(BlockPos targetPos, Direction face) {
        double x = targetPos.getX() + 0.5;
        double y = targetPos.getY() + 0.5;
        double z = targetPos.getZ() + 0.5;
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
        double lookDx = targetX - client.player.getX();
        double lookDy = targetY - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double lookDz = targetZ - client.player.getZ();
        facing.setTargetYaw(SpatialHelper.calculateYawTo(
                client.player.getX(), client.player.getZ(), targetX, targetZ));
        facing.setTargetPitch((float) -Math.atan2(lookDy, Math.sqrt(lookDx * lookDx + lookDz * lookDz)) * (180.0F / (float) Math.PI));
    }

    // ==================== 工具 ====================

    /**
     * 计算本帧的增量转角:限速 + 近场减速。
     * 返回值与 delta 同号,|返回值| <= maxSpeed;剩余角度小于一步时只走剩余角度(无 overshoot)。
     */
    private float stepTowards(float delta, float maxSpeed) {
        float abs = Math.abs(delta);
        if (abs <= 0.0001F) return 0.0F;
        float ratio = abs < APPROACH_SLOW_ZONE
                ? Math.max(APPROACH_MIN_RATIO, abs / APPROACH_SLOW_ZONE)
                : 1.0F;
        return Math.copySign(Math.min(abs, maxSpeed * ratio), delta);
    }
}
