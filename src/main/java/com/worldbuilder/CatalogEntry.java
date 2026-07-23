package com.worldbuilder;

final class CatalogEntry
{
    static final int SOURCE_OBJECT = 0;
    static final int SOURCE_NPC = 1;

    final int sourceType;
    final int objectId;
    final int objectType;
    final int npcId;
    final int animationId;
    final String animationName;
    final String name;

    CatalogEntry(int objectId, int objectType, String name)
    {
        this(SOURCE_OBJECT, objectId, objectType, -1, -1, null, name);
    }

    static CatalogEntry animatedObject(int objectId, int objectType, int animationId, String name)
    {
        return new CatalogEntry(SOURCE_OBJECT, objectId, objectType, -1, animationId, "Loop", name);
    }

    static CatalogEntry animatedNpc(int npcId, int animationId, String animationName, String name)
    {
        return new CatalogEntry(SOURCE_NPC, -1, 10, npcId, animationId, animationName, name);
    }

    private CatalogEntry(int sourceType, int objectId, int objectType, int npcId,
        int animationId, String animationName, String name)
    {
        this.sourceType = sourceType;
        this.objectId = objectId;
        this.objectType = objectType;
        this.npcId = npcId;
        this.animationId = animationId;
        this.animationName = animationName;
        this.name = name;
    }

    boolean isNpc()
    {
        return sourceType == SOURCE_NPC;
    }

    boolean isAnimated()
    {
        return animationId >= 0;
    }

    int sourceId()
    {
        return isNpc() ? npcId : objectId;
    }

    String sourceLabel()
    {
        return isNpc() ? "NPC" : "Object";
    }

    String cacheKey()
    {
        return sourceType + ":" + sourceId() + ":" + objectType + ":" + animationId;
    }
}
