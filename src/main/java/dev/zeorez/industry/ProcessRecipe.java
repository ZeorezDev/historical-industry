package dev.zeorez.industry;

import com.google.gson.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;

record ProcessRecipe(ResourceLocation id, Kind machine, List<Input> inputs, ItemStack output, int ticks, int power)
        implements Recipe<Container> {
    record Input(Ingredient ingredient, int count) {
        boolean accepts(ItemStack stack) { return stack.getCount() >= count && ingredient.test(stack); }
    }
    public boolean matches(Container inventory, Level level) {
        for (int i = 0; i < inputs.size(); i++)
            if (!inputs.get(i).accepts(inventory.getItem(i))) return false;
        return true;
    }
    public ItemStack assemble(Container inventory, RegistryAccess access) { return output.copy(); }
    public boolean canCraftInDimensions(int w, int h) { return false; }
    public boolean isSpecial() { return true; }
    public ItemStack getResultItem(RegistryAccess access) { return output.copy(); }
    public ResourceLocation getId() { return id; }
    public RecipeSerializer<?> getSerializer() { return Industry.CODEC.get(); }
    public RecipeType<?> getType() { return Industry.PROCESS.get(); }

    static final class Serializer implements RecipeSerializer<ProcessRecipe> {
        private static int positive(JsonObject json, String key, int max) {
            int value = GsonHelper.getAsInt(json, key);
            if (value < 1 || value > max) throw new JsonSyntaxException("Invalid " + key);
            return value;
        }
        public ProcessRecipe fromJson(ResourceLocation id, JsonObject json) {
            Kind kind = Arrays.stream(Kind.values()).filter(k -> k.id().equals(GsonHelper.getAsString(json, "machine")))
                    .findFirst().orElseThrow(() -> new JsonSyntaxException("Unknown machine"));
            var rows = GsonHelper.getAsJsonArray(json, "inputs");
            if (rows.size() < 1 || rows.size() > 2) throw new JsonSyntaxException("Expected one or two inputs");
            var inputs = new ArrayList<Input>();
            for (var row : rows) {
                var obj = row.getAsJsonObject();
                inputs.add(new Input(Ingredient.fromJson(obj.get("ingredient")), positive(obj, "count", 64)));
            }
            var output = kind == Kind.COAL_GENERATOR ? ItemStack.EMPTY :
                    ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            if (kind != Kind.COAL_GENERATOR && (output.isEmpty() || output.getCount() > output.getMaxStackSize()))
                throw new JsonSyntaxException("Invalid result stack");
            return new ProcessRecipe(id, kind, List.copyOf(inputs), output, positive(json,"ticks",32000), positive(json,"fe_per_tick",16000));
        }
        public ProcessRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new ProcessRecipe(id, buf.readEnum(Kind.class), buf.readList(b -> new Input(Ingredient.fromNetwork(b), b.readVarInt())),
                    buf.readItem(), buf.readVarInt(), buf.readVarInt());
        }
        public void toNetwork(FriendlyByteBuf buf, ProcessRecipe r) {
            buf.writeEnum(r.machine);
            buf.writeCollection(r.inputs, (b,i) -> { i.ingredient.toNetwork(b); b.writeVarInt(i.count); });
            buf.writeItem(r.output);
            buf.writeVarInt(r.ticks);
            buf.writeVarInt(r.power);
        }
    }
}
