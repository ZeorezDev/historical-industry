package dev.zeorez.industry;

import java.util.Locale;

enum Kind {
    COAL_GENERATOR, INDUSTRIAL_FURNACE, MACHINE_WORKSHOP, TEXTILE_FACTORY;
    String id() { return name().toLowerCase(Locale.ROOT); }
}
