package com.worldbuilder;

import java.util.ArrayList;
import java.util.List;

final class Tilepack
{
    static final int CURRENT_VERSION = 1;

    int version = CURRENT_VERSION;
    String name = "My World Builder Tilepack";
    List<PropPlacement> props = new ArrayList<>();
}
