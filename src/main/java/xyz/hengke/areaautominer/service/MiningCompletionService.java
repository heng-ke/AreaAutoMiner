package xyz.hengke.areaautominer.service;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.state.BreakingState;
import xyz.hengke.areaautominer.context.state.RollbackState;
import xyz.hengke.areaautominer.context.state.SessionState;
import xyz.hengke.areaautominer.helper.AreaIterator;
import xyz.hengke.areaautominer.lifecycle.SessionLifecycle;
import xyz.hengke.areaautominer.model.MiningState;

/**
 * 挖掘完成与回滚编排：回滚校验、完成收尾、已挖/跳过事件（事件上报已委托
 * {@link BlockEventReporter}，方案 8）。
 * 依赖的状态对象:RollbackState(回滚计数/已挖集合)、SessionState(会话/监听器)、BreakingState(最近挖掉方块)。
 */
public class MiningCompletionService {
    private final MinecraftClient client;
    private final MiningConfig config;
    private final RollbackState rollback;
    private final SessionState session;
    private final BreakingState breaking;
    private final NotificationService notificationService;
    private final SessionLifecycle lifecycle;
    private final AreaIterator areaIterator;
    private final BlockEventReporter blockEventReporter;

    public MiningCompletionService(MinecraftClient client, MiningConfig config,
                                   RollbackState rollback, SessionState session, BreakingState breaking,
                                   NotificationService notificationService, SessionLifecycle lifecycle,
                                   AreaIterator areaIterator, BlockEventReporter blockEventReporter) {
        this.client = client;
        this.config = config;
        this.rollback = rollback;
        this.session = session;
        this.breaking = breaking;
        this.notificationService = notificationService;
        this.lifecycle = lifecycle;
        this.areaIterator = areaIterator;
        this.blockEventReporter = blockEventReporter;
    }

    public void completeMining() {
        if (!config.enableRollbackDetection) {
            forceCompleteMining();
            return;
        }

        if (rollback.getRollbackRetryCount() >= config.maxRollbackRetries) {
            forceCompleteMining();
            return;
        }

        if (!verifyAllBlocksMined()) {
            // 方案A（H1）：剔除已确认挖掉（空气）的记录，游标重置到区域起点重新遍历。
            // 旧实现游标停在区域外（遍历结束时必然越界），重扫只会扫区域外空气层空转，
            // 区域内的回滚方块永远不会被重挖；重置游标后重扫能真正扫到并挖掉残留方块。
            rollback.getMinedPositions().removeIf(pos -> client.world.getBlockState(pos).isAir());
            areaIterator.resetToStart();
            rollback.setRollbackRetryCount(rollback.getRollbackRetryCount() + 1);
            notificationService.sendMessage(Messages.ROLLBACK_MISS_RESCAN);
            session.setState(MiningState.FINDING_BLOCK);
            return;
        }

        forceCompleteMining();
    }

    private void forceCompleteMining() {
        lifecycle.teardown();  // 与 stopMining() 共用同一收尾出口

        if (rollback.getRollbackRetryCount() > 0) {
            notificationService.sendMessage(String.format(Messages.MINING_COMPLETE_WITH_ROLLBACK, rollback.getRollbackRetryCount()));
        } else {
            notificationService.sendMessage(Messages.MINING_COMPLETE);
        }

        rollback.setRollbackRetryCount(0);

        if (session.getListener() != null) {
            session.getListener().onMineComplete();
        }
    }

    public boolean verifyAllBlocksMined() {
        for (BlockPos pos : rollback.getMinedPositions()) {
            if (!client.world.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** 方块被跳过：事件上报委托 BlockEventReporter（方案 8） */
    public void onBlockSkipped(BlockPos pos) {
        blockEventReporter.reportSkipped(pos);
    }

    /** 方块被挖掉：记录回滚/挖掘数据 + 事件上报委托 BlockEventReporter（方案 8） */
    public void onBlockMined(BlockPos pos) {
        breaking.setLastMinedPos(new BlockPos(pos));
        rollback.addMinedPosition(pos, config.maxMinedPositions);
        blockEventReporter.reportMined(pos);
    }
}
