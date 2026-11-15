package dev.zeorez.industry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.items.*;

final class MachineMenu extends AbstractContainerMenu {
    final Machine machine;
    final ContainerData data;
    static MachineMenu client(int id, Inventory inv, FriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        return new MachineMenu(id,inv,(Machine)inv.player.level().getBlockEntity(pos),new ItemStackHandler(3),new SimpleContainerData(3));
    }
    MachineMenu(int id, Inventory inv, Machine m, ItemStackHandler slots, ContainerData data) {
        super(Industry.MENU.get(),id);
        machine=m;
        this.data=data;
        addSlot(new SlotItemHandler(slots,0,36,42));
        addSlot(new SlotItemHandler(slots,1,57,42));
        addSlot(new SlotItemHandler(slots,2,126,42) {
            public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row=0;row<3;row++)
            for (int col=0;col<9;col++) addSlot(new Slot(inv,9+row*9+col,8+18*col,84+18*row));
        for (int col=0;col<9;col++) addSlot(new Slot(inv,col,8+18*col,142));
        addDataSlots(data);
    }
    public boolean stillValid(Player player) {
        return !(player instanceof ServerPlayer p) || machine != null && !machine.isRemoved() && Land.mayUse(p,machine);
    }
    public void clicked(int slot,int button,ClickType action,Player p) {
        if (stillValid(p)) super.clicked(slot,button,action,p);
    }
    public ItemStack quickMoveStack(Player p,int index) {
        if (!stillValid(p) || index<0 || index>=slots.size()) return ItemStack.EMPTY;
        Slot slot=slots.get(index);
        ItemStack stack=slot.getItem(), old=stack.copy();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        boolean moved=index<3 ? moveItemStackTo(stack,3,39,true) : moveItemStackTo(stack,0,2,false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(p,stack);
        return old;
    }
}
