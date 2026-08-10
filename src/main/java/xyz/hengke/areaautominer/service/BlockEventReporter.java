package xyz.hengke.areaautominer.service;

import net.minecraft.util.math.BlockPos;

/**
 * 方块事件上报：玩家可见消息（跳过事件带同位置去重）。
 *
 * <p>从 MiningCompletionService 的对外部分提取（方案 8），使"完成收尾"与"事件上报"分离：
 * 新增指标追踪（如统计跳过数）只需扩展本类，不再触碰完成逻辑。</p>
 */
public class BlockEventReporter {
    /** 同一方块跳过提示的去重时间窗（ms），避免连续跳过同一方块时刷屏 */
    private static final long SKIP_DEBOUNCE_MS = 2000;

    private final NotificationService notificationService;
    private BlockPos lastSkippedPos;
    private long lastSkippedTime;

    public BlockEventReporter(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 方块被跳过：玩家消息（同位置 2 秒内去重） */
    public void reportSkipped(BlockPos pos) {
        long now = System.currentTimeMillis();
        if (pos.equals(lastSkippedPos) && now - lastSkippedTime < SKIP_DEBOUNCE_MS) {
            return;
        }
        lastSkippedPos = new BlockPos(pos);
        lastSkippedTime = now;
        notificationService.sendMessage(String.format(Messages.BLOCK_SKIPPED, pos.getX(), pos.getY(), pos.getZ()));
    }
}
