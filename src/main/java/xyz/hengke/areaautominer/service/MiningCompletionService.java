package xyz.hengke.areaautominer.service;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.config.MiningConfig;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.helper.InputHelper;
import xyz.hengke.areaautominer.model.MiningState;

import java.util.Set;

public class MiningCompletionService {
    private final MiningContext context;
    private final InputHelper inputHelper;
    private final NotificationService notificationService;
    private final MiningConfig config;

    public MiningCompletionService(MiningContext context, InputHelper inputHelper, NotificationService notificationService) {
        this.context = context;
        this.inputHelper = inputHelper;
        this.notificationService = notificationService;
        this.config = MiningConfig.getInstance();
    }

    public void completeMining() {
        if (!config.isRollbackDetectionEnabled()) {
            forceCompleteMining();
            return;
        }

        if (context.getRollbackRetryCount() >= config.getMaxRollbackRetries()) {
            forceCompleteMining();
            return;
        }

        if (!verifyAllBlocksMined()) {
            context.setRollbackRetryCount(context.getRollbackRetryCount() + 1);
            notificationService.sendMessage("§e检测到回滚遗漏，重新挖掘...");
            context.setState(MiningState.FINDING_BLOCK);
            return;
        }

        forceCompleteMining();
    }

    private void forceCompleteMining() {
        context.setMining(false);
        context.setState(MiningState.IDLE);
        inputHelper.releaseAllKeys();

        if (context.getRollbackRetryCount() > 0) {
            notificationService.sendMessage("§a挖掘完成（已处理 " + context.getRollbackRetryCount() + " 次回滚）");
        } else {
            notificationService.sendMessage("§a挖掘完成！");
        }

        context.setRollbackRetryCount(0);

        if (context.getListener() != null) {
            context.getListener().onMineComplete();
        }
    }

    public boolean verifyAllBlocksMined() {
        MinecraftClient client = context.getClient();
        Set<BlockPos> minedPositions = context.getMinedPositions();

        for (BlockPos pos : minedPositions) {
            if (!client.world.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    public void onBlockSkipped(BlockPos pos) {
        if (context.getListener() != null) {
            context.getListener().onBlockSkipped(pos);
        }
        notificationService.sendMessage("§e跳过方块: " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    public void onBlockMined(BlockPos pos) {
        context.setLastMinedPos(new BlockPos(pos));
        context.addMinedPosition(pos);
        if (context.getListener() != null) {
            context.getListener().onBlockMined(pos);
        }
    }
}