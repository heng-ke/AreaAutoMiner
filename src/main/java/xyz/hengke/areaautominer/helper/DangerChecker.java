package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.model.DangerType;

public final class DangerChecker {
    private DangerChecker() {
    }

    public static DangerType evaluate(MinecraftClient client, BlockPos targetPos) {
        if (isLavaDanger(client, targetPos)) return DangerType.LAVA_TARGET;
        if (isLavaAroundPlayer(client)) return DangerType.LAVA_AROUND;
        if (isVoidDanger(client, targetPos)) return DangerType.VOID;
        return DangerType.NONE;
    }

    public static boolean isLavaDanger(MinecraftClient client, BlockPos targetPos) {
        if (client.world.getFluidState(targetPos).isIn(FluidTags.LAVA)) return true;
        return client.world.getFluidState(targetPos.down()).isIn(FluidTags.LAVA);
    }

    public static boolean isVoidDanger(MinecraftClient client, BlockPos targetPos) {
        if (targetPos.getY() < client.world.getBottomY()) return true;
        if (targetPos.getY() < client.world.getBottomY() + 8) {
            for (int dy = -1; dy >= -5; dy--) {
                if (!client.world.getBlockState(targetPos.add(0, dy, 0)).isAir()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    public static boolean isLavaAroundPlayer(MinecraftClient client) {
        if (client.world == null || client.player == null) return false;
        BlockPos playerPos = client.player.getBlockPos();
        if (client.world.getFluidState(playerPos.down()).isIn(FluidTags.LAVA)) return true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (client.world.getFluidState(playerPos.add(dx, 0, dz)).isIn(FluidTags.LAVA)) return true;
            }
        }
        return false;
    }
}
