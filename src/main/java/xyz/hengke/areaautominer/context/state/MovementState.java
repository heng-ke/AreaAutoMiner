package xyz.hengke.areaautominer.context.state;

import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.helper.WalkCycleBreaker;

/**
 * 行走状态：移动稳定等待、行走计时、卡住检测、跳跃冷却、重试计数与当前寻路路径。
 * startWalkingToBlock() / resetWalkSession() 封装了行走会话的归零逻辑。
 *
 * <p>方案 4：不可达防死循环的断路器算法（同一目标连续进入 WALKING 的次数）已提取到
 * {@link WalkCycleBreaker}（helper 包），本类回归纯数据容器。startWalkingToBlock 委托断路器判定，
 * 签名不变（调用方 BlockFinder/BreakingHelper 零改动）。</p>
 */
public class MovementState {
    /** 行走循环断路器：同一目标连续进入 WALKING 达上限即判定"不可达"（F1 防死循环） */
    private final WalkCycleBreaker walkCycleBreaker = new WalkCycleBreaker();

    private int waitTicks = 0;
    private boolean movingWait = false;

    private int walkTicks = 0;
    private double lastPlayerX = 0;
    private double lastPlayerZ = 0;
    private int stuckCounter = 0;
    private int jumpCooldown = 0;
    private int walkRetryCount = 0;
    // 头顶跳跃进行中：已起跳、等待落地。用于避免"落地后立刻重复起跳"的原地连跳
    private boolean jumpInProgress = false;
    // 原地转向进行中（上一 tick 在 followPath 转向分支）：转向期间不计卡住、不清零计数（M2 方案B）
    private boolean turningInPlace = false;

    private Path currentPath;
    // 短距离直走（贪心）受阻标记：近距目标直走时按住前进但位移过小 → 置 true，
    // MovementHelper 下一 tick 转入 A* 兜底。仅在新行走会话（startWalkingToBlock）/会话级归零时重置，
    // 重试期间保持 true，避免"贪心被挡 → A* 失败 → 贪心"来回振荡。
    private boolean shortHopBlocked = false;
    // 短距离直走（贪心）的转向目标：贪心模式不持有 Path，CameraHelper.smoothFrame 以此为准做帧级平滑转向。
    private BlockPos shortHopTarget;

    /**
     * 开始走向目标方块：清零行走会话并锚定当前位置（用于卡住检测）。
     *
     * @param targetPos 本次行走的目标方块（不可达计数依据；null 时仅清零计数不更新目标）
     * @return true 表示该目标已连续多次进入 WALKING（超过 {@link WalkCycleBreaker#MAX_WALK_CYCLE_COUNT}），
     *         调用方应跳过此方块（F1 防死循环）
     */
    public boolean startWalkingToBlock(BlockPos targetPos, double playerX, double playerZ) {
        boolean limitReached = walkCycleBreaker.recordWalkAttempt(targetPos);

        this.walkTicks = 0;
        this.stuckCounter = 0;
        this.walkRetryCount = 0;
        this.lastPlayerX = playerX;
        this.lastPlayerZ = playerZ;
        this.currentPath = null;
        this.jumpInProgress = false;
        this.turningInPlace = false;
        this.shortHopBlocked = false;
        this.shortHopTarget = null;

        return limitReached;
    }

    /** 重置一次行走会话的公共状态（到达/跳过/重试超限后统一清理） */
    public void resetWalkSession() {
        this.walkTicks = 0;
        this.stuckCounter = 0;
        this.walkRetryCount = 0;
        this.currentPath = null;
        this.jumpInProgress = false;
        this.turningInPlace = false;
        this.shortHopTarget = null;
    }

    public int getWaitTicks() {
        return waitTicks;
    }

    public void setWaitTicks(int waitTicks) {
        this.waitTicks = waitTicks;
    }

    public boolean isMovingWait() {
        return movingWait;
    }

    public void setMovingWait(boolean movingWait) {
        this.movingWait = movingWait;
    }

    public int getWalkTicks() {
        return walkTicks;
    }

    public void setWalkTicks(int walkTicks) {
        this.walkTicks = walkTicks;
    }

    public double getLastPlayerX() {
        return lastPlayerX;
    }

    public void setLastPlayerX(double lastPlayerX) {
        this.lastPlayerX = lastPlayerX;
    }

    public double getLastPlayerZ() {
        return lastPlayerZ;
    }

    public void setLastPlayerZ(double lastPlayerZ) {
        this.lastPlayerZ = lastPlayerZ;
    }

    public int getStuckCounter() {
        return stuckCounter;
    }

    public void setStuckCounter(int stuckCounter) {
        this.stuckCounter = stuckCounter;
    }

    public int getJumpCooldown() {
        return jumpCooldown;
    }

    public void setJumpCooldown(int jumpCooldown) {
        this.jumpCooldown = jumpCooldown;
    }

    public int getWalkRetryCount() {
        return walkRetryCount;
    }

    public void setWalkRetryCount(int walkRetryCount) {
        this.walkRetryCount = walkRetryCount;
    }

    public boolean isJumpInProgress() {
        return jumpInProgress;
    }

    public void setJumpInProgress(boolean jumpInProgress) {
        this.jumpInProgress = jumpInProgress;
    }

    public boolean isTurningInPlace() {
        return turningInPlace;
    }

    public void setTurningInPlace(boolean turningInPlace) {
        this.turningInPlace = turningInPlace;
    }

    public Path getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(Path currentPath) {
        this.currentPath = currentPath;
    }

    public boolean isShortHopBlocked() {
        return shortHopBlocked;
    }

    public void setShortHopBlocked(boolean shortHopBlocked) {
        this.shortHopBlocked = shortHopBlocked;
    }

    public BlockPos getShortHopTarget() {
        return shortHopTarget;
    }

    public void setShortHopTarget(BlockPos shortHopTarget) {
        this.shortHopTarget = shortHopTarget;
    }

    public void reset() {
        waitTicks = 0;
        movingWait = false;
        walkTicks = 0;
        lastPlayerX = 0;
        lastPlayerZ = 0;
        stuckCounter = 0;
        jumpCooldown = 0;
        walkRetryCount = 0;
        jumpInProgress = false;
        turningInPlace = false;
        walkCycleBreaker.reset();
        currentPath = null;
        shortHopBlocked = false;
        shortHopTarget = null;
    }
}
