package xyz.hengke.areaautominer.model;

import net.minecraft.util.math.BlockPos;

/**
 * 遍历游标：当前扫描位置（纯数据，非会话状态）。
 * 蛇形遍历推进逻辑见 {@code AreaIterator}。
 */
public class TraversalState {
    private int currentX = 0;
    private int currentY = 0;
    private int currentZ = 0;

    public BlockPos getPosition() {
        return new BlockPos(currentX, currentY, currentZ);
    }

    public void setPosition(BlockPos pos) {
        this.currentX = pos.getX();
        this.currentY = pos.getY();
        this.currentZ = pos.getZ();
    }

    public int getCurrentX() {
        return currentX;
    }

    public void setCurrentX(int currentX) {
        this.currentX = currentX;
    }

    public int getCurrentY() {
        return currentY;
    }

    public void setCurrentY(int currentY) {
        this.currentY = currentY;
    }

    public int getCurrentZ() {
        return currentZ;
    }

    public void setCurrentZ(int currentZ) {
        this.currentZ = currentZ;
    }

    public void reset() {
        currentX = currentY = currentZ = 0;
    }
}
