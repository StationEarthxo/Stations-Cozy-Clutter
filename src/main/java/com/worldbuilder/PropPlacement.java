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
    int worldX;
    int worldY;
    int plane;
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
        p.worldX = worldX;
        p.worldY = worldY;
        p.plane = plane;
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
            && objectId >= 0 && modelId == -1
            && worldX >= 0 && worldY >= 0 && plane >= 0 && plane <= 3
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
