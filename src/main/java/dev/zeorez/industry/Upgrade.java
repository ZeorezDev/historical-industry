package dev.zeorez.industry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class Upgrade {
    static Item speed() { return Industry.SPEED.get(); }
    static Item efficiency() { return Industry.EFFICIENCY.get(); }
    static boolean accepts(ItemStack stack) { return stack.is(speed()) || stack.is(efficiency()); }
}
