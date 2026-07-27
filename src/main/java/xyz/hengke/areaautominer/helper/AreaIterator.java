package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.context.MiningContext;

public class AreaIterator {
    private final MiningContext context;

    public AreaIterator(MiningContext context) {
        this.context = context;
    }

    public boolean advancePosition(int minX, int maxX, int minY, int minZ, int maxZ) {
        context.currentX++;
        if (context.currentX > maxX) {
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
        return true;
    }

    public BlockPos getCurrentPos() {
        return new BlockPos(context.currentX, context.currentY, context.currentZ);
    }
}