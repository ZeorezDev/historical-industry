package dev.zeorez.industry;

import dev.zeorez.historicalnations.api.HistoricalNationsApi;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.*;
import javax.annotation.Nullable;
import java.util.*;

final class Machine extends BlockEntity implements MenuProvider {
    static final int CAPACITY = 16000, RANGE = 4;
    final Kind kind;
    private UUID owner, town, plot, parcel;
    private ResourceLocation recipeId;
    private int energy, progress, duration, burnPower;
    final ItemStackHandler slots;
    // Kept separate so the three legacy inventory indices and their serialized size stay stable.
    final ItemStackHandler upgrade = new ItemStackHandler(1) {
        public int getSlotLimit(int slot) { return 1; }
        public boolean isItemValid(int slot, ItemStack stack) { return !kind.logistics() && Upgrade.accepts(stack); }
        protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final EnumMap<Direction,LazyOptional<IItemHandler>> itemPorts = new EnumMap<>(Direction.class);
    private final EnumMap<Direction,LazyOptional<IEnergyStorage>> energyPorts = new EnumMap<>(Direction.class);
    final ContainerData display = new ContainerData() {
        public int get(int i) { return switch (i) { case 0 -> energy; case 1 -> progress; default -> duration; }; }
        public int getCount() { return 3; }
        public void set(int i,int value) {}
    };

    Machine(BlockPos pos, BlockState state) {
        super(Industry.MACHINE.get(),pos,state);
        kind = ((MachineBlock)state.getBlock()).kind;
        int size = switch (kind) {
            case INDUSTRIAL_WAREHOUSE -> 54;
            case INDUSTRIAL_CONVEYOR -> 1;
            case RAILWAY_LOADING_DOCK -> 0;
            default -> 3;
        };
        slots = new ItemStackHandler(size) {
            public int getSlotLimit(int slot) { return kind==Kind.INDUSTRIAL_CONVEYOR ? 1 : super.getSlotLimit(slot); }
            protected void onContentsChanged(int slot) { setChanged(); }
        };
    }
    void bind(ServerPlayer player) {
        var site = Land.site(player.serverLevel(),worldPosition);
        if (site == null) return;
        owner = player.getUUID();
        town = site.town();
        plot = site.plot();
        parcel = site.parcel();
        setChanged();
    }
    UUID town() { return town; }
    @Override public void onLoad() {
        super.onLoad();
        if (!kind.logistics() && level instanceof ServerLevel w) Production.get(w.getServer()).loaded.add(this);
    }
    @Override public void setRemoved() {
        if (!kind.logistics() && level instanceof ServerLevel w) Production.get(w.getServer()).loaded.remove(this);
        super.setRemoved();
    }
    @Override public void onChunkUnloaded() {
        if (!kind.logistics() && level instanceof ServerLevel w) Production.get(w.getServer()).loaded.remove(this);
        super.onChunkUnloaded();
    }
    UUID owner() { return owner; }
    Land.Site site() {
        if (owner == null || isRemoved() || !(level instanceof ServerLevel world) || !world.getServer().isSameThread()) return null;
        if (!world.hasChunkAt(worldPosition) || world.getBlockEntity(worldPosition) != this) return null;
        var site = Land.site(world,worldPosition);
        return site != null && site.town().equals(town) && site.plot().equals(plot) && site.parcel().equals(parcel) ? site : null;
    }
    boolean licensed() { return site() != null; }
    boolean portAllowed(Direction face) {
        var site = site();
        if (site == null) return false;
        var other = worldPosition.relative(face);
        return level.hasChunkAt(other) && site.equals(Land.site((ServerLevel)level,other));
    }

    static void tick(Level level, BlockPos pos, BlockState state, Machine m) {
        ServerLevel world = (ServerLevel)level;
        boolean running = false;
        boolean hasWork = !m.slots.getStackInSlot(0).isEmpty() ||
                m.kind == Kind.COAL_GENERATOR && (m.progress > 0 || m.energy > 0 && world.getGameTime() % 5 == 0);
        if (hasWork && m.licensed()) {
            if (m.kind == Kind.COAL_GENERATOR) {
                if (world.getGameTime() % 5 == 0 && m.energy > 0) m.distribute(world);
                running = m.generate(world);
            } else running = m.process(world);
        }
        if (state.getValue(MachineBlock.ACTIVE) != running)
            world.setBlock(pos,state.setValue(MachineBlock.ACTIVE,running),Block.UPDATE_CLIENTS);
    }
    private ProcessRecipe recipe(ServerLevel world) {
        var inv = new SimpleContainer(slots.getStackInSlot(0), slots.getStackInSlot(1));
        var manager = world.getRecipeManager();
        if (recipeId != null && manager.byKey(recipeId).orElse(null) instanceof ProcessRecipe old
                && old.machine() == kind && old.matches(inv,world)) return old;
        return manager.getAllRecipesFor(Industry.PROCESS.get()).stream()
                .filter(r -> r.machine() == kind && r.matches(inv,world)).min(Comparator.comparing(ProcessRecipe::id)).orElse(null);
    }
    private boolean fits(ItemStack output) {
        var current = slots.getStackInSlot(2);
        return current.isEmpty() || ItemStack.isSameItemSameTags(current,output)
                && output.getCount() <= current.getMaxStackSize() - current.getCount();
    }
    private void consume(ProcessRecipe r) {
        for (int i=0; i<r.inputs().size(); i++) slots.extractItem(i,r.inputs().get(i).count(),false);
    }
    private boolean generate(ServerLevel world) {
        if (progress == 0) {
            var r = recipe(world);
            if (r == null || CAPACITY-energy < r.power()) return false;
            consume(r);
            duration = r.ticks();
            progress = duration;
            burnPower = r.power();
        }
        var module = upgrade.getStackInSlot(0);
        int steps = module.is(Upgrade.speed()) ? Math.min(progress,2) : 1;
        int output = module.is(Upgrade.efficiency()) ? Math.min(CAPACITY,burnPower+burnPower/4) : burnPower;
        steps = Math.min(steps,(CAPACITY-energy)/Math.max(1,output));
        if (steps==0) return false;
        energy += steps*output;
        progress-=steps;
        setChanged();
        return true;
    }
    private boolean process(ServerLevel world) {
        var r = recipe(world);
        if (r == null || !r.id().equals(recipeId)) {
            if (recipeId != null || r != null) {
                progress = 0;
                duration = 0;
                recipeId = r == null ? null : r.id();
                setChanged();
            }
        }
        if (r == null || !fits(r.output())) return false;
        var module = upgrade.getStackInSlot(0);
        duration = r.ticks();
        int work = module.is(Upgrade.speed()) ? Math.min(2, Math.max(1,duration-progress)) : 1;
        int cost = module.is(Upgrade.efficiency()) ? (3*r.power()+3)/4 : r.power();
        if (module.is(Upgrade.speed())) cost = work*r.power() + (r.power()+3)/4;
        if (energy < cost) return false;
        energy -= cost;
        progress += work;
        if (progress >= duration) {
            consume(r);
            slots.insertItem(2,r.output().copy(),false);
            progress = 0;
            var nation = HistoricalNationsApi.get(world.getServer()).getTownById(town)
                    .flatMap(t -> HistoricalNationsApi.get(world.getServer()).getPlayerNation(t.mayor()));
            Production.get(world.getServer()).record(town,nation.map(n -> n.id()).orElse(null),r.output(),world.getServer().overworld().getGameTime()/24000);
        }
        setChanged();
        return true;
    }
    private void distribute(ServerLevel world) {
        var origin = site();
        if (origin == null) return;
        int budget = Math.min(energy,400);
        var faces = Direction.values();
        int first = (int)(world.getGameTime()/5 % faces.length);
        for (int offset=0; offset<faces.length && budget>0; offset++) {
            var face = faces[(first+offset)%faces.length];
            for (int distance=1; distance<=RANGE && budget>0; distance++) {
                var pos = worldPosition.relative(face,distance);
                if (!world.hasChunkAt(pos) || !origin.equals(Land.site(world,pos))) break;
                var target = world.getBlockEntity(pos);
                if (target == null) continue;
                var port = target.getCapability(ForgeCapabilities.ENERGY,face.getOpposite()).resolve();
                if (port.isPresent() && port.get().canReceive()) {
                    int accepted = Math.max(0,Math.min(budget,port.get().receiveEnergy(budget,false)));
                    energy -= accepted;
                    budget -= accepted;
                    if (accepted > 0) setChanged();
                }
                break;
            }
        }
    }
    void drop() {
        Containers.dropItemStack(level,worldPosition.getX()+0.5,worldPosition.getY()+0.5,worldPosition.getZ()+0.5,upgrade.extractItem(0,1,false));
        for (int i=0;i<slots.getSlots();i++)
            Containers.dropItemStack(level,worldPosition.getX()+0.5,worldPosition.getY()+0.5,worldPosition.getZ()+0.5,slots.extractItem(i,64,false));
    }
    public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        if (kind==Kind.INDUSTRIAL_WAREHOUSE) return new WarehouseMenu(id,inv,this);
        return new MachineMenu(id,inv,this,slots,upgrade,display);
    }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (side != null && !isRemoved()) {
            if (cap == ForgeCapabilities.ITEM_HANDLER && kind!=Kind.RAILWAY_LOADING_DOCK) return itemPorts.computeIfAbsent(side,
                    d -> LazyOptional.of(() -> new ItemsPort(d))).cast();
            if (cap == ForgeCapabilities.ENERGY && !kind.logistics()) return energyPorts.computeIfAbsent(side,
                    d -> LazyOptional.of(() -> new EnergyPort(d))).cast();
        }
        return super.getCapability(cap,side);
    }
    public void invalidateCaps() {
        super.invalidateCaps();
        itemPorts.values().forEach(LazyOptional::invalidate);
        energyPorts.values().forEach(LazyOptional::invalidate);
        itemPorts.clear();
        energyPorts.clear();
    }
    private final class ItemsPort implements IItemHandler {
        final Direction face;
        ItemsPort(Direction face) { this.face=face; }
        public int getSlots() { return slots.getSlots(); }
        public int getSlotLimit(int i) { return slots.getSlotLimit(i); }
        public boolean isItemValid(int i, ItemStack s) {
            if (kind==Kind.INDUSTRIAL_WAREHOUSE) return i>=0 && i<getSlots();
            if (kind==Kind.INDUSTRIAL_CONVEYOR) return i==0 && face==getBlockState().getValue(MachineBlock.FACING).getOpposite();
            return i>=0 && i<2;
        }
        public ItemStack getStackInSlot(int i) { return portAllowed(face) ? slots.getStackInSlot(i).copy() : ItemStack.EMPTY; }
        public ItemStack insertItem(int i,ItemStack s,boolean sim) {
            return isItemValid(i,s) && portAllowed(face) ? slots.insertItem(i,s,sim) : s;
        }
        public ItemStack extractItem(int i,int n,boolean sim) {
            boolean output = switch (kind) {
                case INDUSTRIAL_WAREHOUSE -> i>=0 && i<getSlots();
                case INDUSTRIAL_CONVEYOR -> i==0 && face==getBlockState().getValue(MachineBlock.FACING);
                default -> i==2;
            };
            return output && n>0 && portAllowed(face) ? slots.extractItem(i,n,sim) : ItemStack.EMPTY;
        }
    }
    private final class EnergyPort implements IEnergyStorage {
        final Direction face;
        EnergyPort(Direction face) { this.face=face; }
        public boolean canReceive() { return kind != Kind.COAL_GENERATOR; }
        public boolean canExtract() { return kind == Kind.COAL_GENERATOR; }
        public int getEnergyStored() { return energy; }
        public int getMaxEnergyStored() { return CAPACITY; }
        public int receiveEnergy(int amount,boolean sim) {
            if (!canReceive() || !portAllowed(face)) return 0;
            int n = Math.min(CAPACITY-energy,Math.max(0,amount));
            if (!sim && n>0) { energy+=n; setChanged(); }
            return n;
        }
        public int extractEnergy(int amount,boolean sim) {
            if (!canExtract() || !portAllowed(face)) return 0;
            int n=Math.min(energy,Math.max(0,amount));
            if (!sim && n>0) { energy-=n; setChanged(); }
            return n;
        }
    }
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Slots",slots.serializeNBT());
        if (!kind.logistics()) tag.put("Upgrade",upgrade.serializeNBT());
        if (owner != null) {
            tag.putUUID("Owner",owner);
            tag.putUUID("Town",town);
            tag.putUUID("Plot",plot);
            tag.putUUID("Parcel",parcel);
        }
        if (recipeId != null) tag.putString("Recipe",recipeId.toString());
        tag.putInt("Energy",energy);
        tag.putInt("Progress",progress);
        tag.putInt("Duration",duration);
        tag.putInt("BurnPower",burnPower);
    }
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Slots",Tag.TAG_COMPOUND)) slots.deserializeNBT(tag.getCompound("Slots"));
        if (tag.contains("Upgrade",Tag.TAG_COMPOUND)) upgrade.deserializeNBT(tag.getCompound("Upgrade"));
        owner=tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        town=tag.hasUUID("Town") ? tag.getUUID("Town") : null;
        plot=tag.hasUUID("Plot") ? tag.getUUID("Plot") : null;
        parcel=tag.hasUUID("Parcel") ? tag.getUUID("Parcel") : plot;
        if (town==null || plot==null) owner=null;
        recipeId=tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        energy=Math.max(0,Math.min(CAPACITY,tag.getInt("Energy")));
        duration=Math.max(0,Math.min(32000,tag.getInt("Duration")));
        progress=Math.max(0,Math.min(duration,tag.getInt("Progress")));
        burnPower=Math.max(0,Math.min(CAPACITY,tag.getInt("BurnPower")));
    }
}
