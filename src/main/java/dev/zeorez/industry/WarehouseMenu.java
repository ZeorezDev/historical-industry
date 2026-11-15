package dev.zeorez.industry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

final class WarehouseMenu extends ChestMenu {
    WarehouseMenu(int id, Inventory inventory, Machine machine) {
        super(MenuType.GENERIC_9x6,id,inventory,new Contents(machine),6);
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player) {
        if (stillValid(player)) super.clicked(slot,button,type,player);
    }

    private record Contents(Machine machine) implements Container {
        public int getContainerSize() { return machine.slots.getSlots(); }
        public boolean isEmpty() {
            for (int i=0;i<getContainerSize();i++) if (!getItem(i).isEmpty()) return false;
            return true;
        }
        public ItemStack getItem(int i) { return machine.slots.getStackInSlot(i); }
        public ItemStack removeItem(int i,int n) { return machine.slots.extractItem(i,n,false); }
        public ItemStack removeItemNoUpdate(int i) { return removeItem(i,64); }
        public void setItem(int i,ItemStack stack) { machine.slots.setStackInSlot(i,stack); }
        public void setChanged() { machine.setChanged(); }
        public boolean stillValid(Player p) { return p instanceof ServerPlayer sp && Land.mayUse(sp,machine); }
        public void clearContent() { for(int i=0;i<getContainerSize();i++) setItem(i,ItemStack.EMPTY); }
    }
}
