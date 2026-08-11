package xyz.hengke.areaautominer.helper;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.state.BreakingState;
import xyz.hengke.areaautominer.service.MiningCompletionService;

public class AdvanceCoordinator {
    private final AreaIterator areaIterator;
    private final MiningCompletionService completionService;
    private final BreakingState breaking;

    public AdvanceCoordinator(AreaIterator areaIterator,
                              MiningCompletionService completionService, BreakingState breaking) {
        this.areaIterator = areaIterator;
        this.completionService = completionService;
        this.breaking = breaking;
    }

    public boolean advancePosition() {
        return areaIterator.advancePosition();
    }

    public boolean advanceAfterMined(BlockPos pos) {
        breaking.setBreakTicks(0);
        return advanceOrComplete();
    }

    public boolean advanceAfterSkipped(BlockPos pos) {
        completionService.onBlockSkipped(pos);
        breaking.setBreakTicks(0);
        return advanceOrComplete();
    }

    public boolean advanceSilently() {
        breaking.setBreakTicks(0);
        return advanceOrComplete();
    }

    public boolean advanceOrComplete() {
        if (!advancePosition()) {
            completionService.completeMining();
            return false;
        }
        return true;
    }
}
