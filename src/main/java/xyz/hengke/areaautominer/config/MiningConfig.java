package xyz.hengke.areaautominer.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import xyz.hengke.areaautominer.model.MinerMod;

/**
 * 配置模型（AutoConfig 注解驱动）。
 *
 * <p>JSON 序列化 / 反序列化与配置界面均由 cloth-config 内置的 AutoConfig 自动生成：
 * 配置文件位于 {@code config/areaautominer.json}，字段名即 JSON key
 * （与旧版手写 Gson 的 key 完全一致，老配置文件可直接沿用）。
 * 单例实例由 {@link MiningConfigHolder} 持有。</p>
 *
 * <p>字段一律 public 非 final（AutoConfig 反射读写要求），按 {@link ConfigEntry.Category}
 * 分组成配置界面选项卡；字段声明顺序即界面显示顺序。tooltip 文案在 lang 文件中维护：
 * key 为 {@code text.autoconfig.areaautominer.option.<字段名>.@Tooltip}（单行）或
 * {@code .@Tooltip[0..N-1]}（多行，行数由 {@link ConfigEntry.Gui.Tooltip#count} 决定）。</p>
 */
@Config(name = "areaautominer")
public class MiningConfig implements ConfigData {
    // 注意：本类不应声明任何无 @ConfigEntry.Category 注解的静态字段，
    // 否则 AutoConfig 配置界面会为它创建 "default" 空选项卡（见 MiningConfigHolder 说明）。

    // ==================== 时序配置 ====================
    @ConfigEntry.Category("时序配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int moveWaitTicks = 3;
    @ConfigEntry.Category("时序配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int maxAirSkipPerTick = 5;
    @ConfigEntry.Category("时序配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int maxWalkTicks = 200;
    @ConfigEntry.Category("时序配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int maxStuckTicks = 20;
    @ConfigEntry.Category("时序配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int maxBreakTicks = 400;
    // 单次转向硬超时（tick，20 = 1 秒）：持续被外部干扰时避免死循环
    @ConfigEntry.Category("时序配置")
    @ConfigEntry.Gui.Tooltip(count = 2)
    public int maxFaceTicks = 80;

    // ==================== 距离配置 ====================
    // 选区（手持剑右键选点）的最大射线距离（格）
    @ConfigEntry.Category("距离配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public double selectionMaxDistance = 5.0;
    // 默认 20.25 = 4.5^2，与原版生存模式交互距离（4.5 格）一致
    @ConfigEntry.Category("距离配置")
    @ConfigEntry.Gui.Tooltip(count = 2)
    public double maxReachSquared = 20.25;
    @ConfigEntry.Category("距离配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public double arriveThreshold = 1.2;
    @ConfigEntry.Category("距离配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public double maxVerticalDistance = 4.0;
    // vanilla A* 寻路的跟随范围（同时作为 ChunkCache 半径基准，格）
    @ConfigEntry.Category("距离配置")
    @ConfigEntry.Gui.Tooltip(count = 2)
    public int pathFollowRange = 32;

    // ==================== 重试配置 ====================
    @ConfigEntry.Category("重试配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int maxWalkRetries = 2;

    // ==================== 挖掘配置 ====================
    @ConfigEntry.Category("挖掘配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public MinerMod minerMod = MinerMod.FROM_TOP_DOWN;
    @ConfigEntry.Category("挖掘配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int minToolDurability = 10;
    // 视角对准阈值（度）：剩余偏差小于此值视为已对准，直接开始挖掘
    @ConfigEntry.Category("挖掘配置")
    @ConfigEntry.Gui.Tooltip(count = 2)
    public double facingThresholdDegrees = 5.0;
    // 挖掘中视角重对准阈值（度）：偏差超过此值中断挖掘重新转向
    @ConfigEntry.Category("挖掘配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public double reFacingThresholdDegrees = 15.0;

    // ==================== 调试配置 ====================
    @ConfigEntry.Category("调试配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public boolean debug = false;

    @Override
    public void validatePostLoad() throws ConfigData.ValidationException {
        // 校验规则（数值边界钳制、NaN/Infinity 兜底）已提取至 MiningConfigValidator（方案 6），
        // 可独立单测；本方法仅保留 AutoConfig 框架要求的委托入口。
        MiningConfigValidator.validate(this);
    }
}
