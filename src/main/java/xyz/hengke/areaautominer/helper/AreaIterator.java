package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.model.MinerMod;
import xyz.hengke.areaautominer.config.MiningConfig;

public class AreaIterator {
    private final MiningContext context;
    private final MiningConfig config;

    public AreaIterator(MiningContext context) {
        this.context = context;
        this.config = MiningConfig.getInstance();
    }

    public boolean advanceFromTopDown() {
        int deltaZ = context.currentZ - context.minZ;
        boolean reverseLine = (deltaZ % 2 == 1);

        if (!reverseLine) {
            context.currentX++;
            if (context.currentX > context.maxX) {
                context.currentX = context.maxX;
                context.currentZ++;
                if (context.currentZ > context.maxZ) {
                    context.currentZ = context.minZ;
                    context.currentY--;
                    if (context.currentY < context.minY) {
                        return false;
                    }
                }
            }
        } else {
            context.currentX--;
            if (context.currentX < context.minX) {
                context.currentX = context.minX;
                context.currentZ++;
                if (context.currentZ > context.maxZ) {
                    context.currentZ = context.minZ;
                    context.currentY--;
                    if (context.currentY < context.minY) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
    public boolean advanceFromBottomUp() {
        if (context.currentY < context.maxY) {
            context.currentY++;
        } else {
            context.currentY = context.minY;
            context.currentX++;
            
            if (context.currentX > context.maxX) {
                context.currentX = context.minX;
                context.currentZ++;
                
                if (context.currentZ > context.maxZ) {
                    return false;
                }
            }
        }
        return true;
    }
    public boolean advancePosition() {
        if (config.getMinerMod() == MinerMod.FROM_TOP_DOWN) {
            return advanceFromTopDown();
        }else{
            return advanceFromBottomUp();
        }
    }

    public BlockPos getCurrentPos() {
        return new BlockPos(context.currentX, context.currentY, context.currentZ);
    }
}