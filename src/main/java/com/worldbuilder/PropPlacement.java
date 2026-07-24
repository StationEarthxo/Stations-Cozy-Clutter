package com.worldbuilder;

import java.util.Objects;
import java.util.UUID;

final class PropPlacement
{
    String id = UUID.randomUUID().toString();
    String name;
    int objectId = -1;
    int objectType = 10;
    int objectOrientation;
    int modelId = -1;
    int npcId = -1;
    int animationId = -1;
    boolean animationLoop = true;
    int worldX;
    int worldY;
    int plane;
    boolean instanceSpecific;
    int instanceSceneX = -1;
    int instanceSceneY = -1;
    int instancePlane = -1;
    int instanceTemplateChunk = -1;
    int offsetX;
    int offsetY;
    int rotation;
    int height;
    int scale = 128;

    PropPlacement()
    {
    }

    PropPlacement copy()
    {
        PropPlacement p = new PropPlacement();
        p.id = id;
        p.name = name;
        p.objectId = objectId;
        p.objectType = objectType;
        p.objectOrientation = objectOrientation;
        p.modelId = modelId;
        p.npcId = npcId;
        p.animationId = animationId;
        p.animationLoop = animationLoop;
        p.worldX = worldX;
        p.worldY = worldY;
        p.plane = plane;
        p.instanceSpecific = instanceSpecific;
        p.instanceSceneX = instanceSceneX;
        p.instanceSceneY = instanceSceneY;
        p.instancePlane = instancePlane;
        p.instanceTemplateChunk = instanceTemplateChunk;
        p.offsetX = offsetX;
        p.offsetY = offsetY;
        p.rotation = rotation;
        p.height = height;
        p.scale = scale;
        return p;
    }

    PropPlacement duplicateAt(int x, int y, int z)
    {
        PropPlacement p = copy();
        p.id = UUID.randomUUID().toString();
        p.worldX = x;
        p.worldY = y;
        p.plane = z;
        return p;
    }

    boolean isValid()
    {
        return id != null && name != null
            && ((objectId >= 0 && npcId == -1) || (npcId >= 0 && objectId == -1))
            && modelId == -1 && animationId >= -1
            && worldX >= 0 && worldY >= 0 && plane >= 0 && plane <= 3
            && (!instanceSpecific
                || (instanceSceneX >= 0 && instanceSceneX < 104
                    && instanceSceneY >= 0 && instanceSceneY < 104
                    && instancePlane >= 0 && instancePlane <= 3
                    && instanceTemplateChunk >= 0))
            && offsetX >= -64 && offsetX <= 64
            && offsetY >= -64 && offsetY <= 64
            && scale >= 16 && scale <= 1024;
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof PropPlacement && Objects.equals(id, ((PropPlacement) other).id);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(id);
    }
}
