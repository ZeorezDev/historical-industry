package dev.zeorez.industry;

import dev.zeorez.railways.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;
import java.util.Comparator;

/** Referenced only behind the optional mod check. Freight inventories are vanilla Containers. */
final class RailDock {
    static void transfer(ServerLevel world,Machine dock,Machine store,Direction front,Land.Site site,boolean unload) {
        var origin=dock.getBlockPos();
        // Inspect only the three level track positions in front of the crane. Stop at the first parcel boundary.
        for (int i=1;i<=3;i++) {
            var pos=origin.relative(front,i);
            if (!world.hasChunkAt(pos) || !site.equals(Land.site(world,pos))) return;
            var cars=world.getEntitiesOfClass(RailVehicle.class,new AABB(pos),car ->
                    car.isAlive() && car.blockPosition().equals(pos) && car.speed()==0 && car.getDeltaMovement().lengthSqr()==0
                    && (car.kind==StockKind.BOXCAR || car.kind==StockKind.GONDOLA));
            if (cars.isEmpty()) continue;
            var car=cars.stream().min(Comparator.comparing(RailVehicle::getUUID)).orElseThrow();
            var wagon=new InvWrapper(car);
            if (unload) move(wagon,store.slots); else move(store.slots,wagon);
            return;
        }
    }
    // Both ends are local, synchronous mod-owned inventories. Commit only the number actually inserted.
    private static void move(IItemHandler from,IItemHandler to) {
        for (int i=0;i<from.getSlots();i++) {
            var offer=from.extractItem(i,8,true);
            if (offer.isEmpty()) continue;
            var rest=ItemHandlerHelper.insertItemStacked(to,offer.copy(),false);
            int accepted=offer.getCount()-rest.getCount();
            if (accepted>0) {
                from.extractItem(i,accepted,false);
                return;
            }
        }
    }
}
