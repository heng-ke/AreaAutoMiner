package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.model.RegionState;
import xyz.hengke.areaautominer.model.TraversalState;
import xyz.hengke.areaautominer.model.MinerMod;

public class AreaIterator {
    private final RegionState region;
    private final TraversalState traversal;
    private final MiningConfig config;

    public AreaIterator(RegionState region, TraversalState traversal, MiningConfig config) {
        this.region = region;
        this.traversal = traversal;
        this.config = config;
    }

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
