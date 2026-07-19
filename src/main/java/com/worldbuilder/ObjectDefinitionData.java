package com.worldbuilder;

final class ObjectDefinitionData
{
    String name;
    int[] modelIds;
    int[] modelTypes;
    boolean extendedModelIds;
    int animationId = -1;
    boolean transforms;
    short[] recolorFrom;
    short[] recolorTo;
    short[] retextureFrom;
    short[] retextureTo;
    boolean rotated;
    int scaleX = 128;
    int scaleHeight = 128;
    int scaleY = 128;
    int offsetX;
    int offsetHeight;
    int offsetY;
    int ambient;
    int contrast;

    boolean isSafeForCustomRendering()
    {
        // Extended model IDs are now the normal cache encoding even for old,
        // small IDs (for example Flower #1187 uses it for model 1610). The
        // encoding is not a safety signal; the decoded ID and resulting model
        // geometry are validated separately.
        if (animationId != -1 || transforms || modelIds == null || modelIds.length == 0 || modelIds.length > 8)
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
