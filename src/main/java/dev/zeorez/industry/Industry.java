package dev.zeorez.industry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(Industry.ID)
public final class Industry {
    public static final String ID = "historical_industry";
    static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);
    static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ID);
    static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, ID);
    static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ID);
    static final RegistryObject<BlockEntityType<Machine>> MACHINE = ENTITIES.register("machine",
            () -> BlockEntityType.Builder.of(Machine::new, BLOCKS.getEntries().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    static final RegistryObject<MenuType<MachineMenu>> MENU = MENUS.register("machine", () -> IForgeMenuType.create(MachineMenu::client));
    static final RegistryObject<RecipeType<ProcessRecipe>> PROCESS = TYPES.register("process", () -> new RecipeType<>() {});
    static final RegistryObject<ProcessRecipe.Serializer> CODEC = SERIALIZERS.register("process", ProcessRecipe.Serializer::new);

    public Industry() {
        for (Kind kind : Kind.values()) {
            var block = BLOCKS.register(kind.id(), () -> new MachineBlock(kind));
            ITEMS.register(kind.id(), () -> new BlockItem(block.get(), new Item.Properties()));
        }
        for (String item : new String[]{"steel_ingot", "machine_parts", "textile"})
            ITEMS.register(item, () -> new Item(new Item.Properties()));
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        TYPES.register(bus);
        SERIALIZERS.register(bus);
        bus.addListener(this::tab);
        MinecraftForge.EVENT_BUS.addListener(Reports::register);
    }

    private void tab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS)
            ITEMS.getEntries().forEach(item -> event.accept(item.get()));
    }
    static ResourceLocation id(String path) { return new ResourceLocation(ID, path); }
}
