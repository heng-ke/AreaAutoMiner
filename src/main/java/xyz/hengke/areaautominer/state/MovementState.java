package xyz.hengke.areaautominer.state;

import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.helper.WalkCycleBreaker;
public class MovementState {
    private final WalkCycleBreaker walkCycleBreaker = new WalkCycleBreaker();

    private int waitTicks = 0;
    private boolean movingWait = false;

    private int walkTicks = 0;
    private int retryDelayTicks = 0;
    private double lastPlayerX = 0;
    private double lastPlayerZ = 0;
    private int stuckCounter = 0;
    private int jumpCooldown = 0;
    private int walkRetryCount = 0;
    private boolean jumpInProgress = false;
    private boolean turningInPlace = false;

    private Path currentPath;
    private boolean shortHopBlocked = false;
    private BlockPos shortHopTarget;

    public boolean startWalkingToBlock(BlockPos targetPos, double playerX, double playerZ) {
        boolean limitReached = walkCycleBreaker.recordWalkAttempt(targetPos);

        this.walkTicks = 0;
        this.retryDelayTicks = 0;
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

    public void resetWalkSession() {
        this.walkTicks = 0;
        this.retryDelayTicks = 0;
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

    public int getRetryDelayTicks() {
        return retryDelayTicks;
    }

    public void setRetryDelayTicks(int retryDelayTicks) {
        this.retryDelayTicks = retryDelayTicks;
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
        retryDelayTicks = 0;
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
