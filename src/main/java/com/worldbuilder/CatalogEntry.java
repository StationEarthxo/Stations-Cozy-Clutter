package com.worldbuilder;

final class CatalogEntry
{
    final int objectId;
    final int objectType;
    final String name;

    CatalogEntry(int objectId, int objectType, String name)
    {
        this.objectId = objectId;
        this.objectType = objectType;
        this.name = name;
    }
}
