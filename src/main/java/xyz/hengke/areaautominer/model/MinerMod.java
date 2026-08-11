package xyz.hengke.areaautominer.model;

import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;

public enum MinerMod implements SelectionListEntry.Translatable {
    FROM_TOP_DOWN,
    FROM_BOTTOM_UP;

    @Override
    public String getKey() {
        return "enum.areaautominer." + name().toLowerCase();
    }
}
