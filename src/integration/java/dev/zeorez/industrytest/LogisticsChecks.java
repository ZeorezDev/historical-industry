package dev.zeorez.industrytest;

import net.minecraft.core.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.*;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import static dev.zeorez.industrytest.IndustrySuite.*;

final class LogisticsChecks {
    static void run(IndustrySuite suite,ServerPlayer p,BlockPos a) throws Exception {
        var w=p.serverLevel();
        var belt=a.east();
        var mill=a.east(2);
        var plant=a.south(2);
        suite.place(p,a,"industrial_warehouse");
        suite.place(p,belt,"industrial_conveyor");
        suite.place(p,mill,"rolling_mill");
        suite.place(p,plant,"heavy_assembly_plant");
        suite.place(p,a.north(),"railway_loading_dock");
        var store=suite.items(a);
        check(store.getSlots()==54,"warehouse capacity");
        check(store.insertItem(53,new ItemStack(item("steel_ingot"),2),false).isEmpty(),"warehouse last slot");
        face(w,belt,Direction.EAST);
        for(int i=0;i<4;i++) scheduled(w,belt);
        check(store.getStackInSlot(53).isEmpty() && suite.items(mill).getStackInSlot(0).getCount()==2,"belt conservation");
        var meter=power(w,mill);
        meter.receiveEnergy(16000,false);
        runTicks(w,mill,119);
        check(suite.items(mill).getStackInSlot(2).isEmpty(),"premature plate");
        runTicks(w,mill,1);
        check(meter.getEnergyStored()==13120 && suite.items(mill).getStackInSlot(2).getCount()==1,"base mill FE");
        var menu=menu(w,mill,p);
        var socket=menu.getSlot(3);
        check(!socket.mayPlace(new ItemStack(Items.COAL)) && socket.getMaxStackSize()==1,"upgrade filter and limit");
        check(socket.safeInsert(new ItemStack(item("speed_upgrade"))).isEmpty(),"speed install");
        runTicks(w,mill,59);
        check(suite.items(mill).getStackInSlot(2).getCount()==1,"speed timing");
        runTicks(w,mill,1);
        check(meter.getEnergyStored()==9880 && suite.items(mill).getStackInSlot(2).getCount()==2,"speed FE and quantity");
        check(socket.remove(1).is(item("speed_upgrade")),"upgrade recovered");
        socket.safeInsert(new ItemStack(item("efficiency_upgrade")));
        var assembly=suite.items(plant);
        assembly.insertItem(0,suite.items(mill).extractItem(2,2,false),false);
        assembly.insertItem(1,new ItemStack(item("machine_parts")),false);
        menu(w,plant,p).getSlot(3).safeInsert(new ItemStack(item("efficiency_upgrade")));
        power(w,plant).receiveEnergy(16000,false);
        runTicks(w,plant,239);
        check(assembly.getStackInSlot(2).isEmpty(),"assembly timing");
        runTicks(w,plant,1);
        check(power(w,plant).getEnergyStored()==7360 && assembly.getStackInSlot(2).is(item("industrial_component")),"assembly recipe and efficiency");
        for (String cmd:new String[]{"industry nation Ruhr","industry production","nation economy industry"}) {
            var lines=suite.command(p,cmd);
            check(quantity(lines,"steel_plate")==2 && quantity(lines,"industrial_component")==1,"report "+cmd);
        }
        face(w,belt,Direction.SOUTH);
        store.insertItem(53,assembly.extractItem(2,1,false),false);
        w.destroyBlock(plant,false);
        check(w.getEntitiesOfClass(ItemEntity.class,new AABB(plant).inflate(1),e->e.getItem().is(item("efficiency_upgrade"))).size()==1,"broken upgrade dropped once");
        scheduled(w,a.north());
        // The warehouse is still in the plot; an adjacent apartment now covers its north face.
        p.teleportTo(a.getX(),a.getY(),a.getZ()-1);
        suite.command(p,"quarter pos1");
        suite.command(p,"quarter pos2");
        suite.command(p,"quarter create LoadingBay");
        check(store.extractItem(53,1,false).isEmpty(),"cached warehouse port crossed Quarter");
    }
    static long quantity(java.util.List<Component> lines,String product) {
        for(var line:lines) if(line.getContents() instanceof TranslatableContents t && t.getKey().equals("industry.quantity")
                && ((Component)t.getArgs()[0]).getContents() instanceof TranslatableContents item
                && item.getKey().equals("item.historical_industry."+product)) return ((Number)t.getArgs()[1]).longValue();
        return -1;
    }
    static void startHopper(IndustrySuite suite,ServerPlayer player,BlockPos a) {
        var w=player.serverLevel();
        suite.place(player,a,"industrial_warehouse");
        suite.place(player,a.east(),"industrial_conveyor");
        suite.place(player,a.east(2),"rolling_mill");
        face(w,a.east(),Direction.EAST);
        power(w,a.east(2)).receiveEnergy(10000,false);
        w.setBlockAndUpdate(a.above(),net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());
        ((HopperBlockEntity)w.getBlockEntity(a.above())).setItem(0,new ItemStack(item("steel_ingot"),3));
    }
    static boolean hopperDone(IndustrySuite suite,ServerPlayer player,BlockPos a) {
        var mill=suite.items(a.east(2));
        if(mill.getStackInSlot(2).getCount()!=3) return false;
        check(((HopperBlockEntity)player.serverLevel().getBlockEntity(a.above())).isEmpty(),"hopper retained input");
        check(suite.items(a).getStackInSlot(0).isEmpty() && mill.getStackInSlot(0).isEmpty(),"pipeline duplicated input");
        check(power(player.serverLevel(),a.east(2)).getEnergyStored()==1360,"scheduled production FE");
        return true;
    }
    static void restart(ServerLevel w,BlockPos a) {
        w.getChunkAt(a);
        var store=w.getBlockEntity(a).getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(AssertionError::new);
        check(store.getStackInSlot(53).is(item("industrial_component")) && store.getStackInSlot(53).getCount()==1,"warehouse restart");
        var tag=w.getBlockEntity(a.east(2)).saveWithoutMetadata();
        var rows=tag.getCompound("Upgrade").getList("Items",10);
        check(rows.size()==1 && rows.getCompound(0).getString("id").equals("historical_industry:efficiency_upgrade"),"upgrade restart");
        check(tag.getInt("Energy")==9880,"mill FE restart");
        check(w.getBlockState(a.east()).getValue(BlockStateProperties.HORIZONTAL_FACING)==Direction.SOUTH,"belt direction restart");
    }
    static void face(ServerLevel w,BlockPos pos,Direction dir) {
        w.setBlockAndUpdate(pos,w.getBlockState(pos).setValue(BlockStateProperties.HORIZONTAL_FACING,dir));
    }
    static void scheduled(ServerLevel w,BlockPos pos) { w.getBlockState(pos).tick(w,pos,w.random); }
    private static AbstractContainerMenu menu(ServerLevel w,BlockPos pos,ServerPlayer p) {
        return ((MenuProvider)w.getBlockEntity(pos)).createMenu(1,p.getInventory(),p);
    }
    private static IEnergyStorage power(ServerLevel w,BlockPos pos) {
        return w.getBlockEntity(pos).getCapability(ForgeCapabilities.ENERGY,Direction.NORTH).orElseThrow(AssertionError::new);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void runTicks(ServerLevel world,BlockPos pos,int ticks) {
        var entity=world.getBlockEntity(pos);
        BlockEntityTicker ticker=world.getBlockState(pos).getTicker(world,entity.getType());
        for(int i=0;i<ticks;i++) ticker.tick(world,pos,world.getBlockState(pos),entity);
    }
}
