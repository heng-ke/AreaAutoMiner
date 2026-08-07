package xyz.hengke.areaautominer.context;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ai.pathing.Path;
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

    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;
    private boolean firstBreakTick = false;
    private BlockPos lastMinedPos = null;
    private boolean isAdjacentBlock = false;
    private boolean movingWait = false;

    private int walkTicks = 0;
    private double lastPlayerX = 0, lastPlayerZ = 0;
    private int stuckCounter = 0;
    private int breakTicks = 0;
    private int jumpCooldown = 0;
    private int walkRetryCount = 0;
    private int rollbackRetryCount = 0;
    private int rollbackCheckTimer = 0;
    private int rollbackScanCount = 0;  // 回滚扫描计数（checkRollback 专用，与 rollbackRetryCount 分离）
    private Set<BlockPos> minedPositions = new HashSet<>();
    // vanilla A* 寻路当前路径（沿节点行走驱动）
    private Path currentPath;
    // 回滚恢复点：检测到回滚时保存主遍历中断点，挖完回滚方块后由此恢复
    private BlockPos rollbackResumePos = null;
    // minedPositions 容量上限，超限后静默降级（停止记录，回滚检测仅覆盖已记录部分）
    private static final int MAX_MINED_POSITIONS = 50000;

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

    public int getRollbackScanCount() {
        return rollbackScanCount;
    }

    public void setRollbackScanCount(int rollbackScanCount) {
        this.rollbackScanCount = rollbackScanCount;
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

    public void startWalkingToBlock() {
        this.walkTicks = 0;
        this.stuckCounter = 0;
        this.walkRetryCount = 0;
        this.lastPlayerX = client.player.getX();
        this.lastPlayerZ = client.player.getZ();
        this.state = MiningState.WALKING_TO_BLOCK;
        this.currentPath = null;
    }

    public Set<BlockPos> getMinedPositions() {
        return minedPositions;
    }

    public void addMinedPosition(BlockPos pos) {
        if (minedPositions.size() >= MAX_MINED_POSITIONS) {
            // 静默降级：超限后停止记录，回滚检测仅覆盖已记录部分
            return;
        }
        minedPositions.add(pos);
    }

    public void removeMinedPosition(BlockPos pos) {
        minedPositions.remove(pos);
    }

    public void clearMinedPositions() {
        minedPositions.clear();
    }

    public Path getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(Path currentPath) {
        this.currentPath = currentPath;
    }

    public BlockPos getRollbackResumePos() {
        return rollbackResumePos;
    }

    public void setRollbackResumePos(BlockPos rollbackResumePos) {
        this.rollbackResumePos = rollbackResumePos;
    }
}