package com.worldbuilder;

final class NpcDefinitionData
{
    String name;
    int[] modelIds;
    short[] recolorFrom;
    short[] recolorTo;
    short[] retextureFrom;
    short[] retextureTo;
    int standingAnimation = -1;
    int walkingAnimation = -1;
    int runAnimation = -1;
    int crawlAnimation = -1;
    int widthScale = 128;
    int heightScale = 128;
    int ambient;
    int contrast;
    boolean transforms;

    boolean isSafeForCustomRendering()
    {
        if (transforms || modelIds == null || modelIds.length == 0 || modelIds.length > 32)
        {
            return false;
        }
        for (int modelId : modelIds)
        {
            if (modelId < 0)
            {
                return false;
            }
        }
        return true;
    }
}
