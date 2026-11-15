package dev.zeorez.industry;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.*;

final class Logistics {
    static void step(ServerLevel world, Machine machine, BlockState state) {
        var site=machine.site();
        Direction front=state.getValue(MachineBlock.FACING);
        boolean moved=false;
        if (site!=null) {
            if (machine.kind==Kind.INDUSTRIAL_CONVEYOR) moved=belt(world,machine,front,site);
            else if (ModList.get().isLoaded("historical_railways")) {
                var store=neighbor(world,machine,front.getOpposite(),site);
                if (store!=null && store.kind==Kind.INDUSTRIAL_WAREHOUSE)
                    RailDock.transfer(world,machine,store,front,site,state.getValue(MachineBlock.UNLOAD));
            }
        }
        if (machine.kind==Kind.INDUSTRIAL_CONVEYOR && state.getValue(MachineBlock.ACTIVE)!=moved)
            world.setBlockAndUpdate(machine.getBlockPos(),state.setValue(MachineBlock.ACTIVE,moved));
    }
    private static Machine neighbor(ServerLevel world,Machine machine,Direction face,Land.Site site) {
        var pos=machine.getBlockPos().relative(face);
        return world.hasChunkAt(pos) && world.getBlockEntity(pos) instanceof Machine other && site.equals(other.site()) ? other : null;
    }
    private static boolean belt(ServerLevel world,Machine belt,Direction front,Land.Site site) {
        var held=belt.slots.getStackInSlot(0);
        if (!held.isEmpty()) {
            var target=neighbor(world,belt,front,site);
            if (target==null) return false;
            var port=target.getCapability(ForgeCapabilities.ITEM_HANDLER,front.getOpposite()).resolve();
            if (port.isEmpty()) return false;
            var rest=ItemHandlerHelper.insertItemStacked(port.get(),held.copyWithCount(1),false);
            if (!rest.isEmpty()) return false;
            belt.slots.extractItem(0,1,false);
            return true;
        }
        var source=neighbor(world,belt,front.getOpposite(),site);
        if (source==null || source.kind==Kind.INDUSTRIAL_CONVEYOR) return false;
        var from=source.getCapability(ForgeCapabilities.ITEM_HANDLER,front).resolve();
        if (from.isEmpty()) return false;
        int first=source.kind==Kind.INDUSTRIAL_WAREHOUSE ? 0 : 2;
        for(int slot=first;slot<source.slots.getSlots();slot++) {
            // Skip empty slots before paying for another physical claim check at the public port.
            if (source.slots.getStackInSlot(slot).isEmpty()) continue;
            var extracted=from.get().extractItem(slot,1,false);
            if (!extracted.isEmpty()) {
                belt.slots.setStackInSlot(0,extracted);
                return true;
            }
        }
        return false;
    }
}
