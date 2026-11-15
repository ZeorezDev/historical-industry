package dev.zeorez.industry;

import java.util.Locale;

enum Kind {
    COAL_GENERATOR, INDUSTRIAL_FURNACE, MACHINE_WORKSHOP, TEXTILE_FACTORY, ROLLING_MILL, HEAVY_ASSEMBLY_PLANT,
    INDUSTRIAL_WAREHOUSE, INDUSTRIAL_CONVEYOR, RAILWAY_LOADING_DOCK;
    boolean logistics() {
        return this == INDUSTRIAL_WAREHOUSE || this == INDUSTRIAL_CONVEYOR || this == RAILWAY_LOADING_DOCK;
    }

    boolean animated() { return this != INDUSTRIAL_WAREHOUSE && this != RAILWAY_LOADING_DOCK; }
    String id() { return name().toLowerCase(Locale.ROOT); }
}
