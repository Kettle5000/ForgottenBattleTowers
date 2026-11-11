package com.kettle.fbt;

import com.kettle.fbt.structures.processors.FBTStructureProcessors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;

/** The mod's main class: sets up all of fbt's registries and runs delayed server tasks. */
@Mod("fbt")
public class ForgottenBattleTowers {
   public static final String MODID = "fbt";
   // tasks waiting to run, each with the number of server ticks left before it runs
   private static final Collection<SimpleEntry<Runnable, Integer>> workQueue = new ConcurrentLinkedQueue<>();

   // runs once when the mod loads
   public ForgottenBattleTowers() {
      IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
      // this class's @SubscribeEvent methods (below) listen to game events
      MinecraftForge.EVENT_BUS.register(this);
      // hook each registry to the mod event bus so its entries get registered: sounds, entities, processors, structures
      ForgottenBattleTowersSounds.REGISTRY.register(modEventBus);
      ForgottenBattleTowersEntities.REGISTRY.register(modEventBus);
      FBTStructureProcessors.PROCESSORS.register(modEventBus);
      com.kettle.fbt.structures.FBTStructures.STRUCTURE_TYPES.register(modEventBus);
      com.kettle.fbt.structures.FBTStructures.PIECE_TYPES.register(modEventBus);
   }

   // run action after the given number of server ticks (at least 1); only works when called on the server thread
   public static void queueServerWork(int tick, Runnable action) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null && server.isSameThread()) {
         // skip the action if the server has changed since (e.g. the world was closed and another opened)
         Runnable guardedAction = () -> {
            if (ServerLifecycleHooks.getCurrentServer() == server && server.isSameThread()) {
               action.run();
            }
         };
         workQueue.add(new SimpleEntry<>(guardedAction, Math.max(1, tick)));
      }
   }

   @SubscribeEvent
   // throw away tasks left over from a previous world when a server starts or stops
   public void onServerAboutToStart(ServerAboutToStartEvent event) {
      workQueue.clear();
   }

   @SubscribeEvent
   public void onServerStopped(ServerStoppedEvent event) {
      workQueue.clear();
   }

   @SubscribeEvent
   // at the end of each server tick: count every task down by 1 and run the ones that reach 0
   public void tick(ServerTickEvent event) {
      if (event.phase == Phase.END) {
         List<SimpleEntry<Runnable, Integer>> actions = new ArrayList<>();
         workQueue.forEach(work -> {
            work.setValue(work.getValue() - 1);
            if (work.getValue() == 0) {
               actions.add((SimpleEntry<Runnable, Integer>)work);
            }
         });
         // run them after the count, then remove them from the queue
         actions.forEach(e -> e.getKey().run());
         workQueue.removeAll(actions);
      }
   }
}
