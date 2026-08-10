package xyz.hengke.areaautominer.service;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.state.RollbackState;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 * 回滚检测（方案 3 + 方案 B1）：仅承担检测职责。
 *
 * <p>从 MiningController.checkRollback 提取（方案 3，Controller 减负）；
 * 方案 B1 将检测与响应彻底分离：本类 {@link #tick()} 只做计时 + 扫描，
 * 返回命中的回滚位置（Optional），响应（保存恢复点/改游标/状态转移/通知/计数）
 * 由 MiningController 统一执行。检测频率/阈值调整（性能）与响应策略
 * （重挖 vs 跳过 vs 中止）两个变化轴互不影响。</p>
 */
public class RollbackDetector {
    private final MinecraftClient client;
    private final MiningConfig config;
    private final RollbackState rollback;

    public RollbackDetector(MinecraftClient client, MiningConfig config, RollbackState rollback) {
        this.client = client;
        this.config = config;
        this.rollback = rollback;
    }

    /**
     * 每 tick 回滚检测：计时 + 周期扫描（由 MiningController.tick 调用）。
     *
     * @return 命中回滚位置（已挖方块被重新填充）时返回该位置；未命中/未到扫描周期/已达扫描上限返回 empty
     */
    public Optional<BlockPos> tick() {
        if (!config.enableRollbackDetection) return Optional.empty();

        rollback.setRollbackCheckTimer(rollback.getRollbackCheckTimer() + 1);

        if (rollback.getRollbackCheckTimer() < config.rollbackCheckInterval) {
            return Optional.empty();
        }
        rollback.setRollbackCheckTimer(0);
        return scan();
    }

    /**
     * 扫描已挖方块集合：顺带清除已确认空气（不再需要回滚）的记录；
     * 返回第一个被重新填充的方块位置。
     */
    private Optional<BlockPos> scan() {
        // 使用 rollbackScanCount（扫描计数，由 Controller 在响应时递增）限制重扫次数
        if (rollback.getRollbackScanCount() >= config.maxRollbackRetries) {
            return Optional.empty();
        }

        Set<BlockPos> minedPositions = rollback.getMinedPositions();
        Iterator<BlockPos> iterator = minedPositions.iterator();

        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!client.world.getBlockState(pos).isAir()) {
                return Optional.of(pos);
            }
            iterator.remove();
        }
        return Optional.empty();
    }
}
