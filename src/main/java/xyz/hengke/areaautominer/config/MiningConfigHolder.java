package xyz.hengke.areaautominer.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

public final class MiningConfigHolder {
    private static MiningConfig INSTANCE;

    private MiningConfigHolder() {
    }

    public static synchronized MiningConfig get() {
        if (INSTANCE == null) {
            AutoConfig.register(MiningConfig.class, GsonConfigSerializer::new);
            INSTANCE = AutoConfig.getConfigHolder(MiningConfig.class).getConfig();
        }
        return INSTANCE;
    }
}
