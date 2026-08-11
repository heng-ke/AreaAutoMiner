package xyz.hengke.areaautominer.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import xyz.hengke.areaautominer.model.MinerMod;

@Config(name = "areaautominer")
public class MiningConfig implements ConfigData {
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
    @ConfigEntry.Category("时序配置")
    @ConfigEntry.Gui.Tooltip(count = 2)
    public int maxFaceTicks = 80;

    // ==================== 距离配置 ====================
    @ConfigEntry.Category("距离配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public double selectionMaxDistance = 5.0;
    @ConfigEntry.Category("距离配置")
    @ConfigEntry.Gui.Tooltip(count = 2)
    public double maxReachSquared = 20.25;
    @ConfigEntry.Category("距离配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public double arriveThreshold = 1.2;
    @ConfigEntry.Category("距离配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public double maxVerticalDistance = 4.0;
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
    @ConfigEntry.Category("挖掘配置")
    @ConfigEntry.Gui.Tooltip(count = 2)
    public double facingThresholdDegrees = 5.0;
    @ConfigEntry.Category("挖掘配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public double reFacingThresholdDegrees = 15.0;

    // ==================== 调试配置 ====================
    @ConfigEntry.Category("调试配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public boolean debug = false;
    @ConfigEntry.Category("调试配置")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public boolean showPath = false;

    @Override
    public void validatePostLoad() throws ConfigData.ValidationException {
        MiningConfigValidator.validate(this);
    }
}
