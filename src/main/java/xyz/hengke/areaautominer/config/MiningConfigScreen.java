package xyz.hengke.areaautominer.config;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;

/**
 * 配置界面入口：委托 AutoConfig 按 {@link MiningConfig} 的
 * {@code @ConfigEntry.Category} 注解自动生成分类配置界面。
 */
@Environment(EnvType.CLIENT)
public class MiningConfigScreen {
    // AutoConfig.getConfigScreen 在 21.11.153 中标记为待删除，但仍是该版本的唯一公开入口
    @SuppressWarnings("removal")
    public static Screen create(Screen parent) {
        return AutoConfig.getConfigScreen(MiningConfig.class, parent).get();
    }
}
