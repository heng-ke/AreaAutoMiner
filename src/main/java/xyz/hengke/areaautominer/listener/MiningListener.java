package xyz.hengke.areaautominer.listener;

import net.minecraft.util.math.BlockPos;

/**
 * 挖掘过程事件回调。全部为 default 空实现：不关心事件的调用方无需实现任何方法。
 */
public interface MiningListener {
    default void onMineComplete() {
    }

    default void onBlockSkipped(BlockPos pos) {
    }

    default void onBlockMined(BlockPos pos) {
    }

    default void onStartMining(BlockPos pos1, BlockPos pos2) {
    }

    default void onStopMining() {
    }
}
