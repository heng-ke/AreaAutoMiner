package xyz.hengke.areaautominer.service;

import net.minecraft.util.math.BlockPos;
import xyz.hengke.areaautominer.context.MiningContext;
import xyz.hengke.areaautominer.helper.InputHelper;
import xyz.hengke.areaautominer.model.MiningState;

public class MiningCompletionService {
    private final MiningContext context;
    private final InputHelper inputHelper;
    private final NotificationService notificationService;

    public MiningCompletionService(MiningContext context, InputHelper inputHelper, NotificationService notificationService) {
        this.context = context;
        this.inputHelper = inputHelper;
        this.notificationService = notificationService;
    }

    public void completeMining() {
        context.isMining = false;
        context.state = MiningState.IDLE;
        inputHelper.releaseAllKeys();
        notificationService.sendMessage("§a挖掘完成！");
        if (context.listener != null) {
            context.listener.onMineComplete();
        }
    }

    public void onBlockSkipped(BlockPos pos) {
        if (context.listener != null) {
            context.listener.onBlockSkipped(pos);
        }
        notificationService.sendMessage("§e跳过方块: " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    public void onBlockMined(BlockPos pos) {
        context.lastMinedPos = new BlockPos(pos);
        if (context.listener != null) {
            context.listener.onBlockMined(pos);
        }
    }
}