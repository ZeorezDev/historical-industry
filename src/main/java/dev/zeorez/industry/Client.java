package dev.zeorez.industry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraftforge.client.event.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid=Industry.ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class Client {
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        for (Kind kind : Kind.values()) if (kind.animated()) event.register(Industry.id("block/"+kind.id()+"_drive"));
        event.register(Industry.id("block/rolling_mill_counter"));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Industry.MACHINE.get(),ctx -> new DriveRenderer());
    }
    private static final class DriveRenderer implements BlockEntityRenderer<Machine> {
        public void render(Machine machine,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay) {
            var state=machine.getBlockState();
            if (!state.getValue(MachineBlock.ACTIVE)) return;
            var client=Minecraft.getInstance();
            var model=client.getModelManager().getModel(Industry.id("block/"+machine.kind.id()+"_drive"));
            float phase=(machine.getLevel().getGameTime()%40+partial)/40f;
            pose.pushPose();
            pose.translate(0.5,0,0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180-state.getValue(MachineBlock.FACING).toYRot()));
            pose.translate(-0.5,0,-0.5);
            double wave=Math.sin(phase*Math.PI*2);
            switch(machine.kind) {
                case ROLLING_MILL -> {
                    pose.pushPose();
                    roll(pose,6,360*phase);
                    draw(client,pose,buffer,state,model,light,overlay);
                    pose.popPose();
                    roll(pose,10.5,-360*phase);
                    model=client.getModelManager().getModel(Industry.id("block/rolling_mill_counter"));
                }
                case INDUSTRIAL_CONVEYOR -> pose.translate(0,0,-(phase*5%1)*2/16);
                case MACHINE_WORKSHOP, HEAVY_ASSEMBLY_PLANT -> pose.translate(0,-(1+wave)*0.042,0);
                case TEXTILE_FACTORY -> pose.translate(wave*0.12,0,0);
                default -> {
                    double x=(machine.kind==Kind.COAL_GENERATOR ? 11.6 : 3.4)/16;
                    double y=(machine.kind==Kind.COAL_GENERATOR ? 5.6 : 6.4)/16;
                    pose.translate(x,y,0);
                    pose.mulPose(Axis.ZP.rotationDegrees(360*phase));
                    pose.translate(-x,-y,0);
                }
            }
            draw(client,pose,buffer,state,model,light,overlay);
            pose.popPose();
        }
        private void roll(PoseStack pose,double z,float degrees) {
            pose.translate(0,9.0/16,z/16);
            pose.mulPose(Axis.XP.rotationDegrees(degrees));
            pose.translate(0,-9.0/16,-z/16);
        }
        private void draw(Minecraft client,PoseStack pose,MultiBufferSource buffer,net.minecraft.world.level.block.state.BlockState state,
                          net.minecraft.client.resources.model.BakedModel model,int light,int overlay) {
            client.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffer.getBuffer(Sheets.solidBlockSheet()),
                    state,model,1,1,1,light,overlay);
        }
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(Industry.MENU.get(),Screen::new));
    }
    private static final class Screen extends AbstractContainerScreen<MachineMenu> {
        Screen(MachineMenu menu, Inventory inv, Component name) { super(menu,inv,name); }
        protected void renderBg(GuiGraphics g,float t,int mx,int my) {
            int x=leftPos,y=topPos;
            g.fill(x,y,x+176,y+166,0xff34413d);
            g.fill(x+2,y+2,x+174,y+164,0xffbdb8a5);
            for(var slot:menu.slots) {
                g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xffddd7bc);
                g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+16,y+slot.y+16,0xff535d57);
            }
            int energy=menu.data.get(0),progress=menu.data.get(1),duration=menu.data.get(2);
            g.drawString(font,energy+" / "+Machine.CAPACITY+" FE",x+8,y+23,0xff293731,false);
            g.drawString(font,Component.translatable("industry.upgrade"),x+148-font.width(Component.translatable("industry.upgrade")),y+21,0xff293731,false);
            g.fill(x+80,y+49,x+117,y+53,0xff62645b);
            if(duration>0) g.fill(x+80,y+49,x+80+37*progress/duration,y+53,0xffb46631);
        }
        public void render(GuiGraphics g,int mx,int my,float t) {
            renderBackground(g);
            super.render(g,mx,my,t);
            renderTooltip(g,mx,my);
        }
    }
}
