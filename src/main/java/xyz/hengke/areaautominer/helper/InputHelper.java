package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * 模拟按键输入：让移动由游戏原生物理驱动。
 * 仅依赖 MinecraftClient，与挖掘状态无关。
 */
public class InputHelper {
    private final MinecraftClient client;

    public InputHelper(MinecraftClient client) {
        this.client = client;
    }

    public void setKeyPressed(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
    }

    public void releaseAllKeys() {
        if (client.options != null) {
            setKeyPressed(client.options.forwardKey, false);
            setKeyPressed(client.options.jumpKey, false);
        }
    }
}
