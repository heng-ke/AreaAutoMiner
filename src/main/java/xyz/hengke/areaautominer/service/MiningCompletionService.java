package xyz.hengke.areaautominer.service;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;

/**
 * 挖掘完成与事件收尾：完成收尾、跳过事件（事件上报委托 {@link BlockEventReporter}，方案 8）。
 *
 * <p>方案 B（移除回滚体系）：completeMining 直接收尾（原回滚校验/重扫已删除）；
 * onBlockMined 原回滚数据记录（lastMinedPos/minedPositions）随回滚体系一并移除，
 * 已挖推进由 AdvanceCoordinator 直接处理，本类不再需要 BreakingState 依赖。</p>
 */
public class MiningCompletionService {
    private final NotificationService notificationService;
    private final SessionLifecycle lifecycle;
    private final BlockEventReporter blockEventReporter;

    public MiningCompletionService(NotificationService notificationService,
                                   SessionLifecycle lifecycle, BlockEventReporter blockEventReporter) {
        this.notificationService = notificationService;
        this.lifecycle = lifecycle;
        this.blockEventReporter = blockEventReporter;
    }

    /** 遍历结束：统一收尾（teardown + 完成消息） */
    public void completeMining() {
        lifecycle.teardown();  // 与 stopMining() 共用同一收尾出口

        notificationService.sendMessage(Messages.MINING_COMPLETE);
    }

    /** 方块被跳过：事件上报委托 BlockEventReporter（同位置去重，防刷屏） */
    public void onBlockSkipped(BlockPos pos) {
        blockEventReporter.reportSkipped(pos);
    }
}
