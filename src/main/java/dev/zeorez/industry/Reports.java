package dev.zeorez.industry;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.*;
import dev.zeorez.historicalnations.api.*;
import dev.zeorez.historicalnations.core.WorldRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import static net.minecraft.commands.Commands.*;

final class Reports {
    static void register(RegisterCommandsEvent e) {
        var d=e.getDispatcher();
        var root=literal("industry");
        root.then(literal("info").executes(c -> {
            var s=c.getSource();
            send(s,"industry.info");
            return 1;
        }));
        root.then(literal("factories").executes(c -> {
            var s=c.getSource();
            var t=town(s);
            int count=0;
            for (var m:Production.get(s.getServer()).loaded) {
                if (!t.id().equals(m.town()) || !m.licensed()) continue;
                send(s,"industry.location",m.getDisplayName(),m.getLevel().dimension().location().toString(),
                        m.getBlockPos().toShortString(),m.owner().toString());
                count++;
            }
            send(s,"industry.factories",count);
            return 1;
        }));
        root.then(literal("production").executes(c -> {
            var t=town(c.getSource());
            return report(c.getSource(),t.id(),t.name(),false);
        }));
        root.then(literal("nation").then(argument("nation",StringArgumentType.greedyString()).executes(c -> {
            var s=c.getSource();
            String name=StringArgumentType.getString(c,"nation").strip();
            var nation=WorldRegistry.get(s.getServer()).nations().stream()
                    .filter(n -> n.name().equalsIgnoreCase(name) || n.id().toString().equalsIgnoreCase(name))
                    .findFirst().orElseThrow(() -> error("industry.no_nation"));
            return report(s,nation.id(),nation.name(),true);
        })));
        d.register(root);
        d.register(literal("nation").then(literal("economy").then(literal("industry").executes(c -> {
            var s=c.getSource();
            var n=HistoricalNationsApi.get(s.getServer()).getPlayerNation(s.getPlayerOrException().getUUID())
                    .orElseThrow(() -> error("industry.no_nation"));
            return report(s,n.id(),n.name(),true);
        }))));
    }
    private static Town town(CommandSourceStack s) throws CommandSyntaxException {
        return HistoricalNationsApi.get(s.getServer()).getPlayerTown(s.getPlayerOrException().getUUID())
                .orElseThrow(() -> error("industry.no_town"));
    }
    private static CommandSyntaxException error(String key) { return new SimpleCommandExceptionType(Component.translatable(key)).create(); }
    private static int report(CommandSourceStack s,UUID id,String name,boolean national) {
        var data=Production.get(s.getServer());
        var towns=new HashSet<UUID>();
        if (national) HistoricalNationsApi.get(s.getServer()).getNationTowns(id).forEach(t -> towns.add(t.id()));
        else towns.add(id);
        long active=data.loaded.stream().filter(m -> towns.contains(m.town()) && m.licensed() &&
                m.getBlockState().getValue(MachineBlock.ACTIVE)).count();
        send(s,"industry.report",name,active);
        var total=data.totals(id,national,-1);
        var today=data.totals(id,national,s.getServer().overworld().getGameTime()/24000);
        for (String product:List.of("steel_ingot","machine_parts","textile")) total.putIfAbsent(Industry.id(product),0L);
        total.forEach((item,n) -> {
            var registered=ForgeRegistries.ITEMS.getValue(item);
            Component label=registered==null ? Component.literal(item.toString()) : registered.getDescription();
            send(s,"industry.quantity",label,n,today.getOrDefault(item,0L));
        });
        return 1;
    }
    private static void send(CommandSourceStack source,String key,Object... args) {
        source.sendSuccess(() -> Component.translatable(key,args),false);
    }
}
