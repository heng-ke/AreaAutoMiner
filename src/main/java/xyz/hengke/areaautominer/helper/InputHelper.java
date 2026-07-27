package xyz.hengke.areaautominer.helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import xyz.hengke.areaautominer.context.MiningContext;

public class InputHelper {
    private final MiningContext context;

    public InputHelper(MiningContext context) {
        this.context = context;
    }

    public void setKeyPressed(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
    }

    public void releaseAllKeys() {
        MinecraftClient client = context.client;
        if (client.options != null) {
            setKeyPressed(client.options.forwardKey, false);
            setKeyPressed(client.options.jumpKey, false);
        }
    }
}