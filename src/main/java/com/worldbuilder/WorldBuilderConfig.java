package com.worldbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(WorldBuilderConfig.GROUP)
public interface WorldBuilderConfig extends Config
{
    String GROUP = "worldbuilder";

    @ConfigItem(
        keyName = "requireShift",
        name = "Require Shift",
        description = "Only show Station's Cozy Clutter options while Shift is held"
    )
    default boolean requireShift()
    {
        return true;
    }

    @Range(min = 25, max = 2000)
    @ConfigItem(
        keyName = "maximumProps",
        name = "Maximum props",
        description = "Safety limit for active and imported props"
    )
    default int maximumProps()
    {
        return 500;
    }

}
