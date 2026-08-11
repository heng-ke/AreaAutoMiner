package xyz.hengke.areaautominer.config;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;


@Environment(EnvType.CLIENT)
public class MiningConfigScreen {
    @SuppressWarnings("removal")
    public static Screen create(Screen parent) {
        return AutoConfig.getConfigScreen(MiningConfig.class, parent).get();
    }
}
