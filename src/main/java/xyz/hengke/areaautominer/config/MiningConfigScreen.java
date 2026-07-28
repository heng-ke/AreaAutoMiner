package xyz.hengke.areaautominer.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import xyz.hengke.areaautominer.model.MinerMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class MiningConfigScreen {
    public static Screen create(Screen parent) {
        MiningConfig config = MiningConfig.getInstance();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("AreaAutoMiner 配置"))
                .setSavingRunnable(config::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory timingCategory = builder.getOrCreateCategory(Text.literal("时序配置"));
        timingCategory.addEntry(entryBuilder.startIntField(Text.literal("面向等待 ticks"), config.getFacingWaitTicks())
                .setTooltip(Text.literal("标准转向时的等待时间（单位：ticks）"))
                .setDefaultValue(15)
                .setMin(1)
                .setMax(100)
                .setSaveConsumer(value -> config.facingWaitTicks = value)
                .build());

        timingCategory.addEntry(entryBuilder.startIntField(Text.literal("短面向等待 ticks"), config.getShortFacingWaitTicks())
                .setTooltip(Text.literal("短距离转向时的等待时间（单位：ticks）"))
                .setDefaultValue(4)
                .setMin(1)
                .setMax(50)
                .setSaveConsumer(value -> config.shortFacingWaitTicks = value)
                .build());

        timingCategory.addEntry(entryBuilder.startIntField(Text.literal("移动等待 ticks"), config.getMoveWaitTicks())
                .setTooltip(Text.literal("移动后等待稳定的时间（单位：ticks）"))
                .setDefaultValue(3)
                .setMin(1)
                .setMax(20)
                .setSaveConsumer(value -> config.moveWaitTicks = value)
                .build());

        timingCategory.addEntry(entryBuilder.startIntField(Text.literal("最大行走 ticks"), config.getMaxWalkTicks())
                .setTooltip(Text.literal("到达目标位置的最大时间（单位：ticks）"))
                .setDefaultValue(200)
                .setMin(10)
                .setMax(500)
                .setSaveConsumer(value -> config.maxWalkTicks = value)
                .build());

        timingCategory.addEntry(entryBuilder.startIntField(Text.literal("最大卡住 ticks"), config.getMaxStuckTicks())
                .setTooltip(Text.literal("判定为卡住的时间（单位：ticks）"))
                .setDefaultValue(20)
                .setMin(5)
                .setMax(100)
                .setSaveConsumer(value -> config.maxStuckTicks = value)
                .build());

        timingCategory.addEntry(entryBuilder.startIntField(Text.literal("最大挖掘 ticks"), config.getMaxBreakTicks())
                .setTooltip(Text.literal("挖掘单个方块的最大时间（单位：ticks）"))
                .setDefaultValue(400)
                .setMin(10)
                .setMax(1000)
                .setSaveConsumer(value -> config.maxBreakTicks = value)
                .build());

        ConfigCategory distanceCategory = builder.getOrCreateCategory(Text.literal("距离配置"));
        distanceCategory.addEntry(entryBuilder.startDoubleField(Text.literal("最大到达距离平方"), config.getMaxReachSquared())
                .setTooltip(Text.literal("玩家到达目标的最大水平距离的平方"))
                .setDefaultValue(16.0)
                .setMin(1.0)
                .setMax(100.0)
                .setSaveConsumer(value -> config.maxReachSquared = value)
                .build());

        distanceCategory.addEntry(entryBuilder.startDoubleField(Text.literal("到达阈值"), config.getArriveThreshold())
                .setTooltip(Text.literal("到达目标位置的距离阈值"))
                .setDefaultValue(1.2)
                .setMin(0.5)
                .setMax(5.0)
                .setSaveConsumer(value -> config.arriveThreshold = value)
                .build());

        distanceCategory.addEntry(entryBuilder.startDoubleField(Text.literal("坠落危险阈值"), config.getFallDangerThreshold())
                .setTooltip(Text.literal("判定为有坠落危险的高度差"))
                .setDefaultValue(3.0)
                .setMin(1.0)
                .setMax(10.0)
                .setSaveConsumer(value -> config.fallDangerThreshold = value)
                .build());

        distanceCategory.addEntry(entryBuilder.startDoubleField(Text.literal("最大垂直距离"), config.getMaxVerticalDistance())
                .setTooltip(Text.literal("玩家与目标方块的最大垂直距离"))
                .setDefaultValue(4.0)
                .setMin(1.0)
                .setMax(10.0)
                .setSaveConsumer(value -> config.maxVerticalDistance = value)
                .build());

        ConfigCategory retryCategory = builder.getOrCreateCategory(Text.literal("重试配置"));
        retryCategory.addEntry(entryBuilder.startIntField(Text.literal("最大行走重试次数"), config.getMaxWalkRetries())
                .setTooltip(Text.literal("行走失败后的最大重试次数"))
                .setDefaultValue(2)
                .setMin(0)
                .setMax(10)
                .setSaveConsumer(value -> config.maxWalkRetries = value)
                .build());

        retryCategory.addEntry(entryBuilder.startIntField(Text.literal("最大面向重试次数"), config.getMaxFacingRetries())
                .setTooltip(Text.literal("转向失败后的最大重试次数"))
                .setDefaultValue(2)
                .setMin(0)
                .setMax(10)
                .setSaveConsumer(value -> config.maxFacingRetries = value)
                .build());

        retryCategory.addEntry(entryBuilder.startIntField(Text.literal("每 tick 最大空气跳过数"), config.getMaxAirSkipPerTick())
                .setTooltip(Text.literal("每 tick 跳过的最大空气方块数量"))
                .setDefaultValue(5)
                .setMin(1)
                .setMax(20)
                .setSaveConsumer(value -> config.maxAirSkipPerTick = value)
                .build());

        ConfigCategory debugCategory = builder.getOrCreateCategory(Text.literal("调试配置"));
        debugCategory.addEntry(entryBuilder.startBooleanToggle(Text.literal("调试模式"), config.isDebug())
                .setTooltip(Text.literal("启用调试日志输出"))
                .setDefaultValue(false)
                .setSaveConsumer(value -> config.debug = value)
                .build());
        debugCategory.addEntry(entryBuilder.startSelector(Text.literal("挖掘模式"), MinerMod.values(), config.getMinerMod())
                .setTooltip(Text.literal("选择挖掘模式"))
                .setDefaultValue(MinerMod.FROM_TOP_DOWN)
                .setNameProvider(mod -> {
                    return switch (mod) {
                        case FROM_TOP_DOWN -> Text.literal("从顶部向下");
                        case FROM_BOTTOM_UP -> Text.literal("从底部向上");
                    };
                })
                .setSaveConsumer(value -> config.minerMod = value)
                .build());

        return builder.build();
    }
}