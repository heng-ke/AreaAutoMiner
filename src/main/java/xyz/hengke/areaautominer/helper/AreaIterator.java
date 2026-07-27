package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.context.MiningContext;

public class AreaIterator {
    private final MiningContext context;

    public AreaIterator(MiningContext context) {
        this.context = context;
    }

    public boolean advancePosition(int minX, int maxX, int minY, int minZ, int maxZ) {
    int deltaZ = context.currentZ - minZ;
    boolean reverseLine = (deltaZ % 2 == 1); // 奇数Z偏移：反向行走

    if (!reverseLine) {
        // 正向行：X向右递增
        context.currentX++;
        if (context.currentX > maxX) {
            // 当前行走完，切换下一条Z
            context.currentX = maxX;
            context.currentZ++;
            if (context.currentZ > maxZ) {
                // 当前Y层全部遍历完成，下移一层
                context.currentZ = minZ;
                context.currentY--;
                if (context.currentY < minY) {
                    return false;
                }
            }
        }
    } else {
        // 反向行：X向左递减
        context.currentX--;
        if (context.currentX < minX) {
            context.currentX = minX;
            context.currentZ++;
            if (context.currentZ > maxZ) {
                context.currentZ = minZ;
                context.currentY--;
                if (context.currentY < minY) {
                    return false;
                }
            }
        }
    }
    return true;
}

    public BlockPos getCurrentPos() {
        return new BlockPos(context.currentX, context.currentY, context.currentZ);
    }
}