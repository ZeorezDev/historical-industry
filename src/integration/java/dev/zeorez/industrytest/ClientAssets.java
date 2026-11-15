package dev.zeorez.industrytest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;

@Mod.EventBusSubscriber(modid="industry_integration",value=Dist.CLIENT)
public final class ClientAssets {
    private static boolean done;
    @SubscribeEvent public static void checkModels(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("industry.clientTest") || event.phase!=TickEvent.Phase.END) return;
        var client=Minecraft.getInstance();
        if (!(client.screen instanceof TitleScreen) || client.getOverlay()!=null) return;
        done=true;
        try {
            var manager=client.getModelManager();
            for (String name:List.of("coal_generator","industrial_furnace","machine_workshop","textile_factory")) {
                var key=new ResourceLocation("historical_industry",name);
                var block=ForgeRegistries.BLOCKS.getValue(key);
                for (var state:block.getStateDefinition().getPossibleStates())
                    verify(manager,client.getBlockRenderer().getBlockModel(state),state.toString());
                verify(manager,manager.getModel(new ResourceLocation("historical_industry","block/"+name+"_drive")),name+" drive");
                verify(manager,manager.getModel(new ModelResourceLocation(key,"inventory")),name+" item");
            }
            LogUtils.getLogger().info("INDUSTRY PASS clientAssets");
        } catch (AssertionError ex) { LogUtils.getLogger().error("INDUSTRY FAIL clientAssets",ex); }
        client.stop();
    }
    private static void verify(ModelManager manager,BakedModel model,String name) {
        if (model==manager.getMissingModel()) throw new AssertionError("Missing "+name);
        var quads=model.getQuads(null,null,RandomSource.create(0));
        if(quads.isEmpty()) throw new AssertionError("Empty "+name);
        for(var quad:quads) if(quad.getSprite().contents().name().getPath().equals("missingno"))
            throw new AssertionError("Missing texture "+name);
    }
}
