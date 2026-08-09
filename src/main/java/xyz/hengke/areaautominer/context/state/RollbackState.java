package xyz.hengke.areaautominer.context.state;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * 回滚检测状态：重试/扫描计数、恢复点与已挖方块集合。
 * 已挖集合的容量上限由调用方（MiningCompletionService）按配置传入，超限后静默降级（停止记录）。
 */
public class RollbackState {
    private int rollbackRetryCount = 0;
    private int rollbackCheckTimer = 0;
    private int rollbackScanCount = 0;
    private final Set<BlockPos> minedPositions = new HashSet<>();
    private BlockPos rollbackResumePos = null;

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

    public Set<BlockPos> getMinedPositions() {
        return minedPositions;
    }

    /** 记录已挖方块；超过 maxPositions（由配置传入）后静默降级，回滚检测仅覆盖已记录部分 */
    public void addMinedPosition(BlockPos pos, int maxPositions) {
        if (minedPositions.size() >= maxPositions) {
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

    public BlockPos getRollbackResumePos() {
        return rollbackResumePos;
    }

    public void setRollbackResumePos(BlockPos rollbackResumePos) {
        this.rollbackResumePos = rollbackResumePos;
    }

    public void reset() {
        rollbackRetryCount = 0;
        rollbackCheckTimer = 0;
        rollbackScanCount = 0;
        minedPositions.clear();
        rollbackResumePos = null;
    }
}
