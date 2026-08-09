package xyz.hengke.areaautominer.model;

import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;

/**
 * 挖掘模式。
 * 实现 {@link SelectionListEntry.Translatable} 使 AutoConfig 配置界面的下拉选项
 * 显示 lang key 对应的翻译文本（见 lang 文件 {@code enum.areaautominer.*}），
 * 而非枚举原名（默认行为是 toString()）。
 */
public enum MinerMod implements SelectionListEntry.Translatable {
    FROM_TOP_DOWN,
    FROM_BOTTOM_UP;

    @Override
    public String getKey() {
        return "enum.areaautominer." + name().toLowerCase();
    }
}
