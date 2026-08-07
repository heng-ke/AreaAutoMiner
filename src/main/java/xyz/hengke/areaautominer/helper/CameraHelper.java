package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.model.MiningState;
import xyz.hengke.areaautominer.service.NotificationService;

/**
 * 视角控制器 —— 完全重构版。
 *
 * <p>核心设计:<b>增量追踪器(per-tick tracking),而非轨迹插值(trajectory interpolation)。</b>
 * 旧实现维护「起始角 + 时间进度 + smoothstep + 抖动」,每 tick 把视角强制写入计划轨迹,
 * 任何外部扰动(玩家鼠标、碰撞、击退)都会与计划冲突并被"拽回" —— 即反复修不好的甩头。
 *
 * <p>新实现每 tick 从真实当前视角出发,按角速度上限向目标做一次增量逼近:
 * 没有计划、没有轨迹、没有跨 tick 插值状态。扰动被当作新起点自动吸收,
 * 收敛判据是剩余角度差而非时间,天然鲁棒、天然收敛、无跳变。</p>
 */
public class CameraHelper {

    // ---- 角速度与收敛(单位:度)----
    /** Yaw 每 tick 最大转角(≈120°/s,接近自然转头速度) */
    private static final float YAW_SPEED = 6.0F;
    /** Pitch 每 tick 最大转角(pitch 行程通常远小于 yaw,可略快) */
    private static final float PITCH_SPEED = 8.0F;
    /** 判定收敛的剩余偏差(度):连续 CONVERGE_CONFIRM_TICKS 个 tick 满足即完成 */
    private static final float CONVERGE_EPS = 1.5F;
    /** 收敛防抖:连续满足收敛条件的 tick 数 */
    private static final int CONVERGE_CONFIRM_TICKS = 2;
    /** 单次转向硬超时(tick ≈ 4s):持续被外部干扰(如玩家按住鼠标拖动)时避免死循环 */
    private static final int MAX_FACE_TICKS = 80;
    /** 超时后剩余偏差仍大于此值 → 判定干扰过大,重新开始逼近;否则强制完成 */
    private static final float HARD_FAIL_EPS = 25.0F;
    /** 近场减速带:剩余角度小于此值开始按比例减速,避免终点急停顿挫 */
    private static final float APPROACH_SLOW_ZONE = 12.0F;
    /** 近场减速的最小比例(防止无限趋近不收敛) */
    private static final float APPROACH_MIN_RATIO = 0.35F;

    private final MiningContext context;
    private final InputHelper inputHelper;
    private final NotificationService notificationService;

    // ---- 转向会话状态(由 beginFacing() 重置;不放在 MiningContext,避免污染全局)----
    private int faceTicks = 0;        // 本会话已进行 tick 数(硬超时用)
    private int convergeTicks = 0;    // 连续满足收敛条件的 tick 数(防抖)

    public CameraHelper(MiningContext context, InputHelper inputHelper, NotificationService notificationService) {
        this.context = context;
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
     * 每 tick 视角驱动(FACING_BLOCK 状态)。
     * 内部自动处理移动稳定等待;转向收敛或超时后自动切入 BREAKING。
     */
    public void faceBlock() {
        MinecraftClient client = context.getClient();
        if (client.player == null) return;

        // ---- 移动稳定等待期:只倒数,不转向 ----
        if (context.isMovingWait()) {
            context.setWaitTicks(context.getWaitTicks() - 1);
            if (context.getWaitTicks() <= 0) {
                context.setMovingWait(false);
                beginFacing();
                notificationService.logDebug("移动稳定完成,开始转向");
            }
            return;
        }

        // ---- 防御:若外部直接 setState(FACING_BLOCK) 未显式 beginFacing,首 tick 自动初始化 ----
        if (faceTicks == 0) beginFacing();
        faceTicks++;

        // ---- 从真实当前视角出发,向目标走一步(核心:增量逼近) ----
        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();
        float targetYaw = context.getTargetYaw();
        float targetPitch = context.getTargetPitch();

        float yawDiff = SpatialHelper.normalizeYawDiff(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        client.player.setYaw(currentYaw + stepTowards(yawDiff, YAW_SPEED));
        client.player.setPitch(MathHelper.clamp(currentPitch + stepTowards(pitchDiff, PITCH_SPEED), -90.0F, 90.0F));

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
        if (faceTicks >= MAX_FACE_TICKS) {
            if (Math.abs(yawDiff) > HARD_FAIL_EPS || Math.abs(pitchDiff) > HARD_FAIL_EPS) {
                notificationService.logDebug("转向超时且偏差过大(" + Math.round(Math.abs(yawDiff))
                        + "°),重置会话重新逼近");
                beginFacing();
            } else {
                notificationService.logDebug("转向超时但偏差可接受,直接开始挖掘");
                finishFacing();
            }
        }
    }

    /** 收敛 → 切入挖掘状态(统一出口,避免重复) */
    private void finishFacing() {
        context.setFirstBreakTick(true);
        context.setBreakTicks(0);
        context.setState(MiningState.BREAKING);
        notificationService.logDebug("转向完成,开始挖掘");
    }

    // ==================== 目标角度计算 ====================

    public void calculateTargetLook(BlockPos targetPos) {
        Direction visibleFace = SpatialHelper.getVisibleFace(context.getClient(), targetPos);
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
        MinecraftClient client = context.getClient();
        double lookDx = targetX - client.player.getX();
        double lookDy = targetY - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double lookDz = targetZ - client.player.getZ();
        context.setTargetYaw((float) Math.atan2(lookDz, lookDx) * (180.0F / (float) Math.PI) - 90.0F);
        context.setTargetPitch((float) -Math.atan2(lookDy, Math.sqrt(lookDx * lookDx + lookDz * lookDz)) * (180.0F / (float) Math.PI));
    }

    // ==================== 工具 ====================

    /**
     * 计算本 tick 的增量转角:限速 + 近场减速。
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
