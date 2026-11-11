package com.kettle.fbt.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** Client only: registers the model shape of the golem's magma projectile. */
@EventBusSubscriber(
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public class ClientModLayerRegister {
   // id of the magma cube model; MagmaProjectileRenderer bakes its model from this
   public static final ModelLayerLocation FIRE_CUBE_LAYER = new ModelLayerLocation(new ResourceLocation("fbt", "magma"), "main");

   // the game asks every mod for its model shapes at startup
   @SubscribeEvent
   public static void registerLayers(RegisterLayerDefinitions event) {
      event.registerLayerDefinition(FIRE_CUBE_LAYER, FireCubeModel::createBodyLayer);
   }
}
