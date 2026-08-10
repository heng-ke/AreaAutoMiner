package xyz.hengke.areaautominer.service;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.context.state.SessionState;

/**
 * 方块事件上报（方案 8）：listener 分发 + 玩家可见消息。
 *
 * <p>从 MiningCompletionService.onBlockMined/onBlockSkipped 的对外部分提取，
 * 使"完成/回滚编排"与"事件上报"分离：新增指标追踪（如统计跳过数）只需扩展本类，
 * 不再触碰完成/回滚逻辑。回滚数据记录（已挖集合）仍在 MiningCompletionService/状态层。</p>
 */
public class BlockEventReporter {
    /** 同一方块跳过提示的去重时间窗（ms），避免连续跳过同一方块时刷屏 */
    private static final long SKIP_DEBOUNCE_MS = 2000;

    private final SessionState session;
    private final NotificationService notificationService;
    private BlockPos lastSkippedPos;
    private long lastSkippedTime;

    public BlockEventReporter(SessionState session, NotificationService notificationService) {
        this.session = session;
        this.notificationService = notificationService;
    }

    /** 方块被跳过：分发监听器 + 玩家消息（同位置 2 秒内去重） */
    public void reportSkipped(BlockPos pos) {
        if (session.getListener() != null) {
            session.getListener().onBlockSkipped(pos);
        }
        long now = System.currentTimeMillis();
        if (pos.equals(lastSkippedPos) && now - lastSkippedTime < SKIP_DEBOUNCE_MS) {
            return;
        }
        lastSkippedPos = new BlockPos(pos);
        lastSkippedTime = now;
        notificationService.sendMessage(String.format(Messages.BLOCK_SKIPPED, pos.getX(), pos.getY(), pos.getZ()));
    }

    /** 方块被挖掉：分发监听器（玩家可见的"挖掘完成"日志由调用方决定，避免连发） */
    public void reportMined(BlockPos pos) {
        if (session.getListener() != null) {
            session.getListener().onBlockMined(pos);
        }
    }
}
