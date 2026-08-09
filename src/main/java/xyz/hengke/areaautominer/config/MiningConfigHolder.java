package xyz.hengke.areaautominer.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

/**
 * {@link MiningConfig} 单例持有者。
 *
 * <p>静态单例字段放在本类而非 {@link MiningConfig} 中：AutoConfig 的配置界面遍历
 * {@code getDeclaredFields()} 时会为<b>每个声明字段</b>分配类别，无
 * {@code @ConfigEntry.Category} 注解的静态字段会被误归入 "default" 空选项卡。
 * 将 INSTANCE 移到本类后，配置类仅含 21 个已分类的业务字段，界面不再出现空类别。</p>
 */
public final class MiningConfigHolder {
    private static MiningConfig INSTANCE;

    private MiningConfigHolder() {
    }

    /** 首次访问时惰性注册 AutoConfig 并加载/创建配置文件（注册只允许一次） */
    public static synchronized MiningConfig get() {
        if (INSTANCE == null) {
            AutoConfig.register(MiningConfig.class, GsonConfigSerializer::new);
            INSTANCE = AutoConfig.getConfigHolder(MiningConfig.class).getConfig();
        }
        return INSTANCE;
    }
}
