package xyz.hengke.areaautominer.context.state;

import net.minecraft.entity.ai.pathing.Path;

/**
 * 行走状态：移动稳定等待、行走计时、卡住检测、跳跃冷却、重试计数与当前寻路路径。
 * startWalkingToBlock() / resetWalkSession() 封装了行走会话的归零逻辑。
 */
public class MovementState {
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

    /** 开始走向目标方块：清零行走会话并锚定当前位置（用于卡住检测） */
    public void startWalkingToBlock(double playerX, double playerZ) {
        this.walkTicks = 0;
        this.stuckCounter = 0;
        this.walkRetryCount = 0;
        this.lastPlayerX = playerX;
        this.lastPlayerZ = playerZ;
        this.currentPath = null;
        this.jumpInProgress = false;
        this.turningInPlace = false;
    }

    /** 重置一次行走会话的公共状态（到达/跳过/重试超限后统一清理） */
    public void resetWalkSession() {
        this.walkTicks = 0;
        this.stuckCounter = 0;
        this.walkRetryCount = 0;
        this.currentPath = null;
        this.jumpInProgress = false;
        this.turningInPlace = false;
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
        currentPath = null;
    }
}
