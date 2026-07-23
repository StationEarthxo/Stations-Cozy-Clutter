package com.worldbuilder;

public enum PlacementMode
{
    TILE_CENTRE("Placement: Tile centre", 0),
    FINE_GRID("Placement: Fine (1/8 tile)", 16),
    PRECISE_GRID("Placement: Precise (1/16 tile)", 8);

    private final String label;
    private final int localStep;

    PlacementMode(String label, int localStep)
    {
        this.label = label;
        this.localStep = localStep;
    }

    int getLocalStep()
    {
        return localStep;
    }

    boolean followsCursor()
    {
        return localStep > 0;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
