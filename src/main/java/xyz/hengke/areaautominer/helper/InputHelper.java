package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

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
