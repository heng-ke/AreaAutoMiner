package xyz.hengke.areaautominer.listener;

import net.minecraft.util.math.BlockPos;

public interface MiningListener {
    void onMineComplete();
    void onBlockSkipped(BlockPos pos);
    void onBlockMined(BlockPos pos);
    void onStartMining(BlockPos pos1, BlockPos pos2);
    void onStopMining();
}