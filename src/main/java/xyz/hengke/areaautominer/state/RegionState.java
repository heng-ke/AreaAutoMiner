package xyz.hengke.areaautominer.state;

import net.minecraft.util.math.BlockPos;

public class RegionState {
    private int minX = 0, minY = 0, minZ = 0;
    private int maxX = 0, maxY = 0, maxZ = 0;

    public void setRegion(BlockPos p1, BlockPos p2) {
        this.minX = Math.min(p1.getX(), p2.getX());
        this.maxX = Math.max(p1.getX(), p2.getX());
        this.minY = Math.min(p1.getY(), p2.getY());
        this.maxY = Math.max(p1.getY(), p2.getY());
        this.minZ = Math.min(p1.getZ(), p2.getZ());
        this.maxZ = Math.max(p1.getZ(), p2.getZ());
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public void reset() {
        minX = minY = minZ = 0;
        maxX = maxY = maxZ = 0;
    }
}
