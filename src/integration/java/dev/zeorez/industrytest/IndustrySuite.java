package dev.zeorez.industrytest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import dev.zeorez.historicalnations.api.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Mod("industry_integration")
public final class IndustrySuite {
    private final String phase=System.getProperty("industry.test","");
    private MinecraftServer server;
    private ServerPlayer a,b;
    private int started;
    private boolean done;
    private boolean workshopStarted;
    private int resumeAt;
    private ServerPlayer mayor;
    private int batches;
    private static final BlockPos A=new BlockPos(6,80,6), B=new BlockPos(38,80,6);

    public IndustrySuite() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void tick(TickEvent.ServerTickEvent e) {
        if (done || phase.isEmpty() || e.phase!=TickEvent.Phase.END || e.getServer().getTickCount()<40) return;
        server=e.getServer();
        try {
            if (phase.equals("hopper")) {
                if (started==0) {
                    started=server.getTickCount();
                    a=player("IndustryA",A);
                    command(a,"town create Essen");
                    command(a,"town claim");
                    command(a,"plot set type industrial");
                    LogisticsChecks.startHopper(this,a,A);
                } else if (LogisticsChecks.hopperDone(this,a,A)) finish();
                else check(server.getTickCount()-started<500,"hopper, scheduled conveyor and mill timed out");
                return;
            }
            if (phase.equals("logistics") || phase.equals("freight")) {
                a=player("IndustryA",A);
                command(a,"town create Essen");
                command(a,"nation create Ruhr");
                command(a,"town claim");
                command(a,"plot set type industrial");
                if (phase.equals("freight")) FreightChecks.run(this,a,A);
                else LogisticsChecks.run(this,a,A);
                finish();
                return;
            }
            if (phase.equals("storageRestart")) {
                LogisticsChecks.restart(server.overworld(),A);
                finish();
                return;
            }
            if (phase.equals("territory")) {
                if (territory()) finish();
                return;
            }
            if (phase.equals("verify")) {
                if (restart()) finish();
                return;
            }
            if (!phase.equals("setup")) throw new AssertionError("Unknown phase");
            if (started==0) {
                started=server.getTickCount();
                a=player("IndustryA",A);
                b=player("IndustryB",B);
                setup(a,"Essen","Ruhr",A,"industrial_furnace",Items.IRON_INGOT,Items.COAL);
                setup(b,"Lyon","Rhone",B,"textile_factory",Items.WHITE_WOOL,Items.STRING);
                items(A).insertItem(0,new ItemStack(Items.IRON_INGOT),false);
                items(A).insertItem(1,new ItemStack(Items.COAL),false);
                return;
            }
            if (server.getTickCount()-started>600) throw new AssertionError("Production timeout");
            if (!workshopStarted) {
                if (items(A).getStackInSlot(2).getCount()!=2 || items(B).getStackInSlot(2).getCount()!=2) return;
                var press=A.east(3).south(3);
                place(a,press,"machine_workshop");
                items(A.east(3)).insertItem(0,new ItemStack(Items.COAL,3),false);
                items(press).insertItem(0,items(A).extractItem(2,1,false),false);
                items(press).insertItem(1,new ItemStack(Items.COPPER_INGOT),false);
                workshopStarted=true;
                return;
            }
            if (resumeAt!=0) {
                if(server.getTickCount()<resumeAt) return;
                var nbt=machine(B).saveWithoutMetadata();
                check(nbt.getInt("Progress")>0 && nbt.getInt("Progress")<60,"mid-cycle save");
                finish();
                return;
            }
            if (items(A.east(3).south(3)).getStackInSlot(2).isEmpty()) return;
            trade();
            items(B).insertItem(0,new ItemStack(Items.WHITE_WOOL),false);
            items(B).insertItem(1,new ItemStack(Items.STRING),false);
            resumeAt=server.getTickCount()+5;
        } catch (Exception | AssertionError ex) {
            LogUtils.getLogger().error("INDUSTRY FAIL "+phase,ex);
            done=true;
            server.halt(false);
        }
    }
    private void finish() {
        LogUtils.getLogger().info("INDUSTRY PASS {}",phase);
        done=true;
        server.halt(false);
    }
    static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private ServerPlayer player(String name,BlockPos pos) {
        var profile=new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8)),name);
        server.getProfileCache().add(profile);
        var p=new ServerPlayer(server,server.overworld(),profile);
        var conn=new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn,p);
        p.teleportTo(pos.getX(),pos.getY()+1,pos.getZ());
        return p;
    }
    private static class Output implements CommandSource {
        final List<Component> messages=new ArrayList<>();
        public boolean acceptsSuccess(){return true;}
        public boolean acceptsFailure(){return true;}
        public boolean shouldInformAdmins(){return false;}
        public void sendSystemMessage(Component msg){messages.add(msg);}
    }
    private List<Component> execute(CommandSourceStack source,String cmd) throws CommandSyntaxException {
        var out=new Output();
        check(server.getCommands().getDispatcher().execute(cmd,source.withSource(out))==1,cmd);
        return out.messages;
    }
    List<Component> command(ServerPlayer p,String cmd) throws CommandSyntaxException {
        return execute(p.createCommandSourceStack().withPermission(0),cmd);
    }
    private void setup(ServerPlayer p,String town,String nation,BlockPos pos,String machine,Item one,Item two) throws Exception {
        command(p,"town create "+town);
        command(p,"nation create "+nation);
        command(p,"town claim");
        command(p,"plot set type industrial");
        place(p,pos,machine);
        place(p,pos.east(3),"coal_generator");
        check(items(pos.east(3)).insertItem(0,new ItemStack(Items.COAL),false).isEmpty(),"generator fuel");
        check(items(pos).insertItem(0,new ItemStack(one),false).isEmpty(),"input 1");
        check(items(pos).insertItem(1,new ItemStack(two),false).isEmpty(),"input 2");
    }
    void place(ServerPlayer p,BlockPos pos,String name) {
        var block=ForgeRegistries.BLOCKS.getValue(id(name));
        var world=p.serverLevel();
        world.setBlockAndUpdate(pos,block.defaultBlockState());
        block.setPlacedBy(world,pos,block.defaultBlockState(),p,new ItemStack(block));
    }
    private BlockEntity machine(BlockPos pos){return Objects.requireNonNull(server.overworld().getBlockEntity(pos));}
    IItemHandler items(BlockPos pos){return machine(pos).getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.NORTH).orElseThrow(AssertionError::new);}
    private static ResourceLocation id(String n){return new ResourceLocation("historical_industry",n);}
    static Item item(String n){return ForgeRegistries.ITEMS.getValue(id(n));}
    private void report(String nation,long steel,long textile) throws Exception {
        var reply=execute(server.createCommandSourceStack(),"industry nation "+nation);
        Map<String,Long> output=new HashMap<>();
        for (var line:reply) if(line.getContents() instanceof TranslatableContents t && t.getKey().equals("industry.quantity")){
            Object[] args=t.getArgs();
            output.put(((TranslatableContents)((Component)args[0]).getContents()).getKey(),((Number)args[1]).longValue());
        }
        check(Objects.equals(output.get("item.historical_industry.steel_ingot"),steel),nation+" steel "+output);
        check(Objects.equals(output.get("item.historical_industry.textile"),textile),nation+" textile "+output);
    }
    private void trade() throws Exception {
        report("Ruhr",2,0);
        report("Rhone",0,2);
        var money=EconomyApi.get(server);
        check(money.getBalance(a.getUUID())==0 && money.getBalance(b.getUUID())==0,"production created money");
        var before=(TranslatableContents)command(a,"nation trade exports").get(0).getContents();
        check(before.getArgs()[0].equals("0"),"production counted as trade");
        ItemStack steel=items(A).extractItem(2,1,false);
        check(steel.is(item("steel_ingot")),"steel output");
        a.setItemInHand(InteractionHand.MAIN_HAND,steel);
        b.teleportTo(a.getX()+1,a.getY(),a.getZ());
        var menu=((net.minecraft.world.MenuProvider)machine(A)).createMenu(1,a.getInventory(),a);
        check(menu.stillValid(a) && !menu.stillValid(b),"OPAC and parcel GUI access");
        execute(server.createCommandSourceStack(),"economy give player IndustryB 22 fixture");
        command(b,"nation tariff set 10");
        var quote=(TranslatableContents)command(a,"trade offer IndustryB 1 10 TRY").get(0).getContents();
        String offer=(String)quote.getArgs()[0];
        command(b,"nation embargo add Ruhr");
        try { command(b,"trade accept "+offer); throw new AssertionError("Embargo bypass"); }
        catch(CommandSyntaxException ex) {
            check(ex.getRawMessage() instanceof Component msg && msg.getContents() instanceof TranslatableContents t
                    && t.getKey().endsWith("trade_embargo"),"wrong embargo refusal");
        }
        command(b,"nation embargo remove Ruhr");
        command(b,"trade accept "+offer);
        UUID nation=HistoricalNationsApi.get(server).getPlayerNation(b.getUUID()).orElseThrow().id();
        check(b.getInventory().countItem(item("steel_ingot"))==1 && a.getMainHandItem().isEmpty(),"traded goods");
        check(money.getBalance(a.getUUID())==10 && money.getBalance(b.getUUID())==11 && money.getNationBalance(nation)==1,"customs settlement");
        var parts=items(A.east(3).south(3)).extractItem(2,1,false);
        check(parts.is(item("machine_parts")),"press output");
        a.setItemInHand(InteractionHand.MAIN_HAND,parts);
        quote=(TranslatableContents)command(a,"trade offer IndustryB 1 10 TRY").get(0).getContents();
        command(b,"trade accept "+quote.getArgs()[0]);
        check(b.getInventory().countItem(item("machine_parts"))==1 && money.getNationBalance(nation)==2,"parts export");
        var cached=items(A);
        command(a,"town unclaim");
        check(!menu.stillValid(a),"open menu revoked");
        check(!cached.insertItem(0,new ItemStack(Items.IRON_INGOT),false).isEmpty(),"stale automation after unclaim");
        report("Ruhr",2,0);
    }
    private boolean restart() throws Exception {
        server.overworld().getChunkAt(B);
        report("Ruhr",2,0);
        if (items(B).getStackInSlot(2).getCount()==2) {
            report("Rhone",0,2);
            server.overworld().setChunkForced(B.getX()>>4,B.getZ()>>4,true);
            check(server.getTickCount()<200,"resume timeout");
            return false;
        }
        report("Rhone",0,4);
        check(items(B).getStackInSlot(2).getCount()==4,"textile output after restart");
        check(items(B).getStackInSlot(0).isEmpty() && items(B).getStackInSlot(1).isEmpty(),"spent ingredients after restart");
        server.overworld().setChunkForced(B.getX()>>4,B.getZ()>>4,false);
        return true;
    }
    private boolean territory() throws Exception {
        var pos=B.east(3);
        if (started==0) {
            started=server.getTickCount();
            a=player("IndustryA",A);
            b=player("IndustryB",B.west(16));
            mayor=player("IndustryC",pos);
            command(a,"town create Essen");
            command(a,"nation create Ruhr");
            command(b,"town create Lyon");
            command(b,"nation create Rhone");
            command(mayor,"town create Roubaix");
            command(mayor,"town claim");
            command(mayor,"plot set type industrial");
            place(mayor,pos,"textile_factory");
            var port=items(pos);
            mayor.teleportTo(pos.getX(),pos.getY(),pos.getZ());
            command(mayor,"quarter pos1");
            command(mayor,"quarter pos2");
            command(mayor,"quarter create Bay");
            check(!port.insertItem(0,new ItemStack(Items.WHITE_WOOL),true).isEmpty(),"residential Quarter");
            place(mayor,pos,"textile_factory");
            check(!port.insertItem(0,new ItemStack(Items.WHITE_WOOL),true).isEmpty(),"Quarter boundary on cached port");
            command(mayor,"quarter delete");
            place(mayor,pos,"textile_factory");
            fillLoom(pos);
            return false;
        }
        check(server.getTickCount()-started<550,"affiliation test timeout");
        if (items(pos).getStackInSlot(2).getCount()!=(batches+1)*2) return false;
        batches++;
        switch (batches) {
            case 1 -> {
                report("Ruhr",0,0);
                command(a,"nation invite Roubaix");
                command(mayor,"nation accept Ruhr");
            }
            case 2 -> {
                report("Ruhr",0,2);
                command(mayor,"nation leave");
            }
            case 3 -> {
                report("Ruhr",0,2);
                command(b,"nation invite Roubaix");
                command(mayor,"nation accept Rhone");
            }
            case 4 -> {
                report("Ruhr",0,2);
                report("Rhone",0,2);
                var output=command(mayor,"industry production");
                check(output.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t &&
                        t.getKey().equals("industry.quantity") && t.getArgs()[1].equals(8L)),"town retained all four batches");
                command(mayor,"nation economy industry");
                var next=player("IndustryD",pos);
                command(mayor,"town invite IndustryD");
                command(next,"town accept Roubaix");
                command(mayor,"town unclaim");
                command(mayor,"town setmayor IndustryD");
                var nbt=machine(pos).saveWithoutMetadata();
                check(nbt.getUUID("Owner").equals(mayor.getUUID()) && HistoricalNationsApi.get(server)
                        .getPlayerTown(next.getUUID()).orElseThrow().id().equals(nbt.getUUID("Town")),"mayoral succession retained factory");
                return true;
            }
        }
        fillLoom(pos);
        return false;
    }
    private void fillLoom(BlockPos pos) {
        var fe=machine(pos).getCapability(ForgeCapabilities.ENERGY,Direction.NORTH).orElseThrow(AssertionError::new);
        check(fe.receiveEnergy(480,true)==480 && fe.receiveEnergy(480,false)==480,"metered loom power");
        check(items(pos).insertItem(0,new ItemStack(Items.WHITE_WOOL),false).isEmpty(),"loom wool");
        check(items(pos).insertItem(1,new ItemStack(Items.STRING),false).isEmpty(),"loom string");
    }
}
