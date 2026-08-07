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
        int deltaZ = context.getCurrentZ() - context.getMinZ();
        boolean reverseLine = (deltaZ % 2 == 1);

        if (!reverseLine) {
            context.setCurrentX(context.getCurrentX() + 1);
            if (context.getCurrentX() > context.getMaxX()) {
                context.setCurrentX(context.getMaxX());
                context.setCurrentZ(context.getCurrentZ() + 1);
                if (context.getCurrentZ() > context.getMaxZ()) {
                    context.setCurrentZ(context.getMinZ());
                    context.setCurrentY(context.getCurrentY() - 1);
                    if (context.getCurrentY() < context.getMinY()) {
                        return false;
                    }
                }
            }
        } else {
            context.setCurrentX(context.getCurrentX() - 1);
            if (context.getCurrentX() < context.getMinX()) {
                context.setCurrentX(context.getMinX());
                context.setCurrentZ(context.getCurrentZ() + 1);
                if (context.getCurrentZ() > context.getMaxZ()) {
                    context.setCurrentZ(context.getMinZ());
                    context.setCurrentY(context.getCurrentY() - 1);
                    if (context.getCurrentY() < context.getMinY()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean advanceFromBottomUp() {
        context.setCurrentY(context.getCurrentY() + 1);

        if (context.getCurrentY() <= context.getMaxY()) {
            return true;
        }

        context.setCurrentY(context.getMinY());

        int deltaZ = context.getCurrentZ() - context.getMinZ();
        boolean reverseLine = (deltaZ % 2 == 1);

        if (!reverseLine) {
            context.setCurrentX(context.getCurrentX() + 1);
            if (context.getCurrentX() > context.getMaxX()) {
                context.setCurrentX(context.getMaxX());
                context.setCurrentZ(context.getCurrentZ() + 1);
                if (context.getCurrentZ() > context.getMaxZ()) {
                    return false;
                }
            }
        } else {
            context.setCurrentX(context.getCurrentX() - 1);
            if (context.getCurrentX() < context.getMinX()) {
                context.setCurrentX(context.getMinX());
                context.setCurrentZ(context.getCurrentZ() + 1);
                if (context.getCurrentZ() > context.getMaxZ()) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean advancePosition() {
        // 回滚恢复点：挖完回滚方块后跳回主遍历中断点，避免破坏蛇形遍历序列
        BlockPos resume = context.getRollbackResumePos();
        if (resume != null) {
            context.setCurrentX(resume.getX());
            context.setCurrentY(resume.getY());
            context.setCurrentZ(resume.getZ());
            context.setRollbackResumePos(null);
            return true;  // 不推进，下 tick 从恢复点继续（若该位置已是空气，BlockFinder 会自动跳过并正常 advance）
        }
        if (config.getMinerMod() == MinerMod.FROM_TOP_DOWN) {
            return advanceFromTopDown();
        } else {
            return advanceFromBottomUp();
        }
    }

    public BlockPos getCurrentPos() {
        return new BlockPos(context.getCurrentX(), context.getCurrentY(), context.getCurrentZ());
    }
}