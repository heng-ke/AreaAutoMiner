package xyz.hengke.areaautominer.context;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.listener.MiningListener;
import xyz.hengke.areaautominer.model.MiningState;

import java.util.HashSet;
import java.util.Set;

public class MiningContext {
    private final MinecraftClient client;
    private MiningListener listener;

    private boolean isMining = false;
    private BlockPos pos1 = null, pos2 = null;
    private int minX = 0, minY = 0, minZ = 0;
    private int maxX = 0, maxY = 0, maxZ = 0;
    private int currentY = 0;
    private int currentX = 0;
    private int currentZ = 0;
    private MiningState state = MiningState.IDLE;
    private int waitTicks = 0;
    private int initialWaitTicks = 0;

    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;
    private boolean firstBreakTick = false;
    private float jitterOffset = 0.0f;
    private long lastJitterUpdate = 0;
    private float currentJitterYaw = 0.0f;
    private float currentJitterPitch = 0.0f;
    private BlockPos lastMinedPos = null;
    private boolean isAdjacentBlock = false;
    private boolean movingWait = false;

    private int walkTicks = 0;
    private double lastPlayerX = 0, lastPlayerZ = 0;
    private int stuckCounter = 0;
    private int breakTicks = 0;
    private int jumpCooldown = 0;
    private int walkRetryCount = 0;
    private int facingRetryCount = 0;
    private int rollbackRetryCount = 0;
    private int rollbackCheckTimer = 0;
    private int rollbackScanY = 0;
    private Set<BlockPos> minedPositions = new HashSet<>();

    public MiningContext(MinecraftClient client) {
        this.client = client;
    }

    public MinecraftClient getClient() {
        return client;
    }

    public MiningListener getListener() {
        return listener;
    }

    public void setListener(MiningListener listener) {
        this.listener = listener;
    }

    public boolean isMining() {
        return isMining;
    }

    public void setMining(boolean mining) {
        isMining = mining;
    }

    public BlockPos getPos1() {
        return pos1;
    }

    public void setPos1(BlockPos pos1) {
        this.pos1 = pos1;
    }

    public BlockPos getPos2() {
        return pos2;
    }

    public void setPos2(BlockPos pos2) {
        this.pos2 = pos2;
    }

    public int getMinX() {
        return minX;
    }

    public void setMinX(int minX) {
        this.minX = minX;
    }

    public int getMinY() {
        return minY;
    }

    public void setMinY(int minY) {
        this.minY = minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public void setMinZ(int minZ) {
        this.minZ = minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public void setMaxX(int maxX) {
        this.maxX = maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public void setMaxY(int maxY) {
        this.maxY = maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public void setMaxZ(int maxZ) {
        this.maxZ = maxZ;
    }

    public int getCurrentY() {
        return currentY;
    }

    public void setCurrentY(int currentY) {
        this.currentY = currentY;
    }

    public int getCurrentX() {
        return currentX;
    }

    public void setCurrentX(int currentX) {
        this.currentX = currentX;
    }

    public int getCurrentZ() {
        return currentZ;
    }

    public void setCurrentZ(int currentZ) {
        this.currentZ = currentZ;
    }

    public MiningState getState() {
        return state;
    }

    public void setState(MiningState state) {
        this.state = state;
    }

    public int getWaitTicks() {
        return waitTicks;
    }

    public void setWaitTicks(int waitTicks) {
        this.waitTicks = waitTicks;
    }

    public int getInitialWaitTicks() {
        return initialWaitTicks;
    }

    public void setInitialWaitTicks(int initialWaitTicks) {
        this.initialWaitTicks = initialWaitTicks;
    }

    public float getTargetYaw() {
        return targetYaw;
    }

    public void setTargetYaw(float targetYaw) {
        this.targetYaw = targetYaw;
    }

    public float getTargetPitch() {
        return targetPitch;
    }

    public void setTargetPitch(float targetPitch) {
        this.targetPitch = targetPitch;
    }

    public boolean isFirstBreakTick() {
        return firstBreakTick;
    }

    public void setFirstBreakTick(boolean firstBreakTick) {
        this.firstBreakTick = firstBreakTick;
    }

    public float getJitterOffset() {
        return jitterOffset;
    }

    public void setJitterOffset(float jitterOffset) {
        this.jitterOffset = jitterOffset;
    }

    public long getLastJitterUpdate() {
        return lastJitterUpdate;
    }

    public void setLastJitterUpdate(long lastJitterUpdate) {
        this.lastJitterUpdate = lastJitterUpdate;
    }

    public float getCurrentJitterYaw() {
        return currentJitterYaw;
    }

    public void setCurrentJitterYaw(float currentJitterYaw) {
        this.currentJitterYaw = currentJitterYaw;
    }

    public float getCurrentJitterPitch() {
        return currentJitterPitch;
    }

    public void setCurrentJitterPitch(float currentJitterPitch) {
        this.currentJitterPitch = currentJitterPitch;
    }

    public BlockPos getLastMinedPos() {
        return lastMinedPos;
    }

    public void setLastMinedPos(BlockPos lastMinedPos) {
        this.lastMinedPos = lastMinedPos;
    }

    public boolean isAdjacentBlock() {
        return isAdjacentBlock;
    }

    public void setAdjacentBlock(boolean adjacentBlock) {
        isAdjacentBlock = adjacentBlock;
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

    public int getBreakTicks() {
        return breakTicks;
    }

    public void setBreakTicks(int breakTicks) {
        this.breakTicks = breakTicks;
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

    public int getFacingRetryCount() {
        return facingRetryCount;
    }

    public void setFacingRetryCount(int facingRetryCount) {
        this.facingRetryCount = facingRetryCount;
    }

    public int getRollbackRetryCount() {
        return rollbackRetryCount;
    }

    public void setRollbackRetryCount(int rollbackRetryCount) {
        this.rollbackRetryCount = rollbackRetryCount;
    }

    public int getRollbackCheckTimer() {
        return rollbackCheckTimer;
    }

    public void setRollbackCheckTimer(int rollbackCheckTimer) {
        this.rollbackCheckTimer = rollbackCheckTimer;
    }

    public int getRollbackScanY() {
        return rollbackScanY;
    }

    public void setRollbackScanY(int rollbackScanY) {
        this.rollbackScanY = rollbackScanY;
    }

    public void resetWalkState() {
        this.walkTicks = 0;
        this.stuckCounter = 0;
        this.walkRetryCount = 0;
    }

    public void setRegion(BlockPos p1, BlockPos p2) {
        this.pos1 = p1;
        this.pos2 = p2;
        this.minX = Math.min(p1.getX(), p2.getX());
        this.maxX = Math.max(p1.getX(), p2.getX());
        this.minY = Math.min(p1.getY(), p2.getY());
        this.maxY = Math.max(p1.getY(), p2.getY());
        this.minZ = Math.min(p1.getZ(), p2.getZ());
        this.maxZ = Math.max(p1.getZ(), p2.getZ());
    }

    public void resetAllState() {
        this.isMining = false;
        this.pos1 = null;
        this.pos2 = null;
        this.minX = 0;
        this.minY = 0;
        this.minZ = 0;
        this.maxX = 0;
        this.maxY = 0;
        this.maxZ = 0;
        this.currentX = 0;
        this.currentY = 0;
        this.currentZ = 0;
        this.state = MiningState.IDLE;
        this.waitTicks = 0;
        this.initialWaitTicks = 0;
        this.targetYaw = 0.0f;
        this.targetPitch = 0.0f;
        this.firstBreakTick = false;
        this.jitterOffset = 0.0f;
        this.lastJitterUpdate = 0;
        this.currentJitterYaw = 0.0f;
        this.currentJitterPitch = 0.0f;
        this.lastMinedPos = null;
        this.isAdjacentBlock = false;
        this.movingWait = false;
        this.walkTicks = 0;
        this.lastPlayerX = 0;
        this.lastPlayerZ = 0;
        this.stuckCounter = 0;
        this.breakTicks = 0;
        this.jumpCooldown = 0;
        this.walkRetryCount = 0;
        this.facingRetryCount = 0;
        this.rollbackRetryCount = 0;
        this.rollbackCheckTimer = 0;
        this.rollbackScanY = 0;
    }

    public void startWalkingToBlock() {
        this.walkTicks = 0;
        this.stuckCounter = 0;
        this.walkRetryCount = 0;
        this.lastPlayerX = client.player.getX();
        this.lastPlayerZ = client.player.getZ();
        this.state = MiningState.WALKING_TO_BLOCK;
    }

    public void advanceRollbackScanY() {
        this.rollbackScanY++;
        if (this.rollbackScanY > this.maxY) {
            this.rollbackScanY = this.minY;
        }
    }

    public Set<BlockPos> getMinedPositions() {
        return minedPositions;
    }

    public void addMinedPosition(BlockPos pos) {
        minedPositions.add(pos);
    }

    public void removeMinedPosition(BlockPos pos) {
        minedPositions.remove(pos);
    }

    public void clearMinedPositions() {
        minedPositions.clear();
    }
}