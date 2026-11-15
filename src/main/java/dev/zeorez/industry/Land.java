package dev.zeorez.industry;

import dev.zeorez.historicalnations.api.*;
import dev.zeorez.historicalnations.property.ParcelProtection;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import xaero.pac.common.server.api.OpenPACServerAPI;
import java.util.*;

final class Land {
    record Site(UUID town, UUID plot, UUID parcel) {}
    static Site site(ServerLevel world, BlockPos pos) {
        var server = world.getServer();
        var dim = world.dimension().location();
        var plot = Parcels.plot(server, dim, pos).orElse(null);
        if (plot == null || !"INDUSTRIAL".equals(plot.type())) return null;
        var parcel = Parcels.quarter(server, dim, pos).orElse(plot);
        return new Site(plot.town(), plot.id(), parcel.id());
    }
    static boolean mayUse(ServerPlayer p, Machine m) {
        if (p.level() != m.getLevel() || !m.licensed() || !m.getBlockPos().closerToCenterThan(p.position(), 8)) return false;
        var pos = m.getBlockPos();
        var hand = InteractionHand.MAIN_HAND;
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var check = new PlayerInteractEvent.RightClickBlock(p, hand, pos, hit);
        ParcelProtection.interact(check);
        if (check.getUseBlock() == Event.Result.DENY) return false;
        return !OpenPACServerAPI.get(p.server).getChunkProtection()
                .onBlockInteraction(p, hand, ItemStack.EMPTY, p.serverLevel(), pos, Direction.UP, false, false, true);
    }
}
