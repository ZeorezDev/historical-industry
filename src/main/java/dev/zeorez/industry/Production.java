package dev.zeorez.industry;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

final class Production extends SavedData {
    private record Batch(UUID town, UUID nation, long day, ResourceLocation item) {}
    private final Map<Batch,Long> quantities = new HashMap<>();
    final Set<Machine> loaded = Collections.newSetFromMap(new WeakHashMap<>());
    static Production get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Production::read,Production::new,"historical_industry");
    }
    void record(UUID town, UUID nation, ItemStack product, long day) {
        quantities.merge(new Batch(town,nation,day,ForgeRegistries.ITEMS.getKey(product.getItem())),(long)product.getCount(),Long::sum);
        setDirty();
    }
    Map<ResourceLocation,Long> totals(UUID id,boolean national,long day) {
        Map<ResourceLocation,Long> out = new TreeMap<>();
        quantities.forEach((batch,n) -> {
            if (id.equals(national ? batch.nation : batch.town) && (day<0 || day==batch.day))
                out.merge(batch.item,n,Long::sum);
        });
        return out;
    }
    public CompoundTag save(CompoundTag tag) {
        var rows = new ListTag();
        quantities.forEach((b,n) -> {
            var row = new CompoundTag();
            row.putUUID("Town",b.town);
            if (b.nation!=null) row.putUUID("Nation",b.nation);
            row.putLong("Day",b.day);
            row.putString("Item",b.item.toString());
            row.putLong("Quantity",n);
            rows.add(row);
        });
        tag.put("Output",rows);
        return tag;
    }
    private static Production read(CompoundTag tag) {
        var result = new Production();
        for (var entry : tag.getList("Output",Tag.TAG_COMPOUND)) {
            var r=(CompoundTag)entry;
            var b=new Batch(r.getUUID("Town"),r.hasUUID("Nation") ? r.getUUID("Nation") : null,
                    r.getLong("Day"),new ResourceLocation(r.getString("Item")));
            result.quantities.put(b,r.getLong("Quantity"));
        }
        return result;
    }
}
