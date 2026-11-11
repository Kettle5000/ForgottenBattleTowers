package com.kettle.fbt;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
/** Client only: draws the golem as a player-shaped model (with armour) at twice normal size, and registers both fbt renderers. */
public class BattleTowerGolemRender extends HumanoidMobRenderer<BattleTowerGolemEntity, HumanoidModel<BattleTowerGolemEntity>> {
   // player body model with a 0.5 block shadow, plus an armour layer so any armour it wears shows
   public BattleTowerGolemRender(Context context) {
      super(context, new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
      this.addLayer(
         new HumanoidArmorLayer(
            this,
            new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()
         )
      );
   }

   // the golem's skin
   public ResourceLocation getTextureLocation(BattleTowerGolemEntity entity) {
      return new ResourceLocation("fbt:textures/entities/battle_golem.png");
   }

   // draw it at double size
   public void render(BattleTowerGolemEntity entity, float yaw, float particleTicks, PoseStack poseStack, MultiBufferSource multibuffersource, int packedLight) {
      poseStack.scale(2.0F, 2.0F, 2.0F);
      super.render(entity, yaw, particleTicks, poseStack, multibuffersource, packedLight);
   }

   @SubscribeEvent
   // tells the game which renderer draws which fbt entity
   public static void registerEntityRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType)ForgottenBattleTowersEntities.GOLEM.get(), BattleTowerGolemRender::new);
      event.registerEntityRenderer((EntityType)ForgottenBattleTowersEntities.MAGMA_PROJECTILE.get(), MagmaProjectileRenderer::new);
   }
}
