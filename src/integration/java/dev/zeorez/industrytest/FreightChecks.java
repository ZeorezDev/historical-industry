package dev.zeorez.industrytest;

import dev.zeorez.railways.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.*;
import static dev.zeorez.industrytest.IndustrySuite.*;

final class FreightChecks {
    static void run(IndustrySuite suite, ServerPlayer owner, BlockPos a) throws Exception {
        var world=owner.serverLevel();
        var dock=a.north();
        suite.place(owner,a,"industrial_warehouse");
        suite.place(owner,dock,"railway_loading_dock");
        var store=suite.items(a);
        var track=a.north(2);
        for(int x=-4;x<=8;x++) {
            var rail=track.offset(x,0,0);
            world.setBlockAndUpdate(rail.below(),Blocks.STONE.defaultBlockState());
            world.setBlockAndUpdate(rail,Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
        }
        for(var kind:new StockKind[]{StockKind.GONDOLA,StockKind.BOXCAR}) {
            var car=vehicle(world,track,kind);
            var stack=new ItemStack(item("steel_plate"),11);
            stack.getOrCreateTag().putString("Lot","Ruhr");
            store.insertItem(0,stack,false);
            LogisticsChecks.scheduled(world,dock);
            check(car.getItem(0).getCount()==8 && store.getStackInSlot(0).getCount()==3,"dock limit "+kind);
            toggle(world,dock,owner);
            LogisticsChecks.scheduled(world,dock);
            check(car.getItem(0).isEmpty() && store.getStackInSlot(0).getCount()==11 && ItemStack.isSameItemSameTags(stack,store.getStackInSlot(0)),"unload preserved cargo "+kind);
            toggle(world,dock,owner);
            store.extractItem(0,64,false);
            car.discard();
        }
        var box=vehicle(world,track,StockKind.BOXCAR);
        var loco=vehicle(world,track.east(3),StockKind.STEAM_LOCOMOTIVE);
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(HistoricalRailways.COUPLER.get()));
        loco.interact(owner,InteractionHand.MAIN_HAND);
        box.interact(owner,InteractionHand.MAIN_HAND);
        for (var supply:new Item[]{Items.COAL,Items.WATER_BUCKET}) {
            owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(supply));
            loco.interact(owner,InteractionHand.MAIN_HAND);
        }
        owner.startRiding(loco,true);
        loco.command(CabControls.Action.POWER);
        loco.tick();
        check(box.speed()!=0 && box.blockPosition().equals(track),"moving coupled wagon still in dock range");
        store.insertItem(0,new ItemStack(item("industrial_component"),5),false);
        LogisticsChecks.scheduled(world,dock);
        check(box.getItem(0).isEmpty() && store.getStackInSlot(0).getCount()==5,"moving wagon accepted cargo");
        owner.stopRiding();
        check(LogisticsChecks.quantity(suite.command(owner,"industry nation Ruhr"),"steel_plate")==0,"freight counted as production");
    }
    private static RailVehicle vehicle(ServerLevel world,BlockPos pos,StockKind kind) {
        var v=HistoricalRailways.VEHICLES.get(kind).get().create(world);
        v.setPos(Vec3.atBottomCenterOf(pos).add(0,.0625,0));
        var tag=v.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        tag.putLong("Rail",pos.asLong());
        tag.putInt("Forward",Direction.EAST.get3DDataValue());
        v.load(tag);
        world.addFreshEntity(v);
        return v;
    }
    private static void toggle(ServerLevel w,BlockPos pos,ServerPlayer p) {
        w.getBlockState(pos).use(w,p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }
}
