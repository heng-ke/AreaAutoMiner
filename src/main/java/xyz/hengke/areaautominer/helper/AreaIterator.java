package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.state.RegionState;
import xyz.hengke.areaautominer.state.TraversalState;
import xyz.hengke.areaautominer.model.MinerMod;

/**
 * 蛇形遍历驱动：只依赖区域边界与遍历游标，不感知其他状态。
 *
 * <p>方案 9 + 方案 B：回滚恢复点/重扫逻辑已随回滚体系移除（本类不持有 RollbackState，
 * 亦无 seek/resetToStart 专属方法），保持纯遍历。</p>
 */
public class AreaIterator {
    private final RegionState region;
    private final TraversalState traversal;
    private final MiningConfig config;

    public AreaIterator(RegionState region, TraversalState traversal, MiningConfig config) {
        this.region = region;
        this.traversal = traversal;
        this.config = config;
    }

    /**
     * TOP_DOWN 蛇形遍历：从最高层 (maxY) 开始逐层向下，每层 Z 行蛇形折返。
     *
     * <p>推进规则：同层内按行蛇形（偶数行 X 递增，奇数行 X 递减），行尾越界换下一行
     * （X 回到该行方向起点）；一行走完后 Z+1，Z 越界则换到下一层（Y-1）。
     * <b>换层后新层第一行（Z=minZ，偶数行）必须从 X=minX 重新开始</b>——
     * 原实现换层时 X 残留上一层行尾值（maxX），导致每层漏掉第一行 X&lt;maxX 的所有方块。</p>
     */
    public boolean advanceFromTopDown() {
        int deltaZ = traversal.getCurrentZ() - region.getMinZ();
        boolean reverseLine = (deltaZ % 2 == 1);

        if (!reverseLine) {
            // 偶数行：从左到右
            traversal.setCurrentX(traversal.getCurrentX() + 1);
            if (traversal.getCurrentX() > region.getMaxX()) {
                // 行尾越界 → 换到下一行（奇数行，从右端 maxX 开始）；或整层走完换层
                traversal.setCurrentX(region.getMaxX());
                traversal.setCurrentZ(traversal.getCurrentZ() + 1);
                if (traversal.getCurrentZ() > region.getMaxZ()) {
                    return moveToNextLayer();
                }
            }
        } else {
            // 奇数行：从右到左
            traversal.setCurrentX(traversal.getCurrentX() - 1);
            if (traversal.getCurrentX() < region.getMinX()) {
                // 行尾越界 → 换到下一行（偶数行，从左端 minX 开始）；或整层走完换层
                traversal.setCurrentX(region.getMinX());
                traversal.setCurrentZ(traversal.getCurrentZ() + 1);
                if (traversal.getCurrentZ() > region.getMaxZ()) {
                    return moveToNextLayer();
                }
            }
        }
        return true;
    }

    /** 整层走完 → 换到下一层（Y-1）：新层第一行始终从 (minX, minZ) 重新开始 */
    private boolean moveToNextLayer() {
        traversal.setCurrentZ(region.getMinZ());
        traversal.setCurrentX(region.getMinX());
        traversal.setCurrentY(traversal.getCurrentY() - 1);
        return traversal.getCurrentY() >= region.getMinY();
    }

    public boolean advanceFromBottomUp() {
        traversal.setCurrentY(traversal.getCurrentY() + 1);

        if (traversal.getCurrentY() <= region.getMaxY()) {
            return true;
        }

        traversal.setCurrentY(region.getMinY());

        int deltaZ = traversal.getCurrentZ() - region.getMinZ();
        boolean reverseLine = (deltaZ % 2 == 1);

        if (!reverseLine) {
            traversal.setCurrentX(traversal.getCurrentX() + 1);
            if (traversal.getCurrentX() > region.getMaxX()) {
                traversal.setCurrentX(region.getMaxX());
                traversal.setCurrentZ(traversal.getCurrentZ() + 1);
                if (traversal.getCurrentZ() > region.getMaxZ()) {
                    return false;
                }
            }
        } else {
            traversal.setCurrentX(traversal.getCurrentX() - 1);
            if (traversal.getCurrentX() < region.getMinX()) {
                traversal.setCurrentX(region.getMinX());
                traversal.setCurrentZ(traversal.getCurrentZ() + 1);
                if (traversal.getCurrentZ() > region.getMaxZ()) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean advancePosition() {
        if (config.minerMod == MinerMod.FROM_TOP_DOWN) {
            return advanceFromTopDown();
        } else {
            return advanceFromBottomUp();
        }
    }

    public BlockPos getCurrentPos() {
        return traversal.getPosition();
    }
}
