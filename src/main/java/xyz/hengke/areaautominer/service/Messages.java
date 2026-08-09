package xyz.hengke.areaautominer.service;

/**
 * 玩家可见消息文案集中管理（§ 颜色码内置）。
 * 业务类不再散落裸字符串，后续如需 i18n 只需替换此处的实现。
 */
public final class Messages {
    private Messages() {
    }

    public static final String NEED_SELECT_REGION = "§c请先选择区域！";
    public static final String START_MINING = "§a开始挖掘区域";
    public static final String STOP_MINING = "§c停止挖掘";
    public static final String MINING_COMPLETE = "§a挖掘完成！";
    public static final String MINING_COMPLETE_WITH_ROLLBACK = "§a挖掘完成（已处理 %d 次回滚）";
    public static final String ROLLBACK_MISS_RESCAN = "§e检测到回滚遗漏，重新挖掘...";
    public static final String ROLLBACK_DETECTED = "§e检测到回滚，重新挖掘位置: ";
    public static final String TOOL_LOW_DURABILITY = "§c工具耐久不足（剩余 %d 点），已停止挖掘，更换工具后按 K 重新开始";
    public static final String BLOCK_SKIPPED = "§e跳过方块: %d,%d,%d";
    public static final String POINT1_RECORDED = "§a点1已记录: ";
    public static final String POINT2_RECORDED = "§a点2已记录: ";
    public static final String PLAYER_DEAD_STOP = "§c玩家死亡，停止挖掘";
    public static final String SCREEN_OPEN_STOP = "§c界面打开，停止挖掘";
}
