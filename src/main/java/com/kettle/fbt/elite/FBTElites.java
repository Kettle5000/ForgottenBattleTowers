package com.kettle.fbt.elite;

import com.kettle.fbt.BattleTowerGolemEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes chosen mobs always a Simple Difficulty blight and always an Infernal Mobs elite.
 *
 * Both mods decide on a mob in their own EntityJoinLevelEvent handler (normal priority) with a random roll:
 * Simple Difficulty against SimpleDifficultyConfig.blight_chance, Infernal Mobs against nextInt(eliteRarity) == 0.
 * Just before they run (highest priority) this sets those two settings to "always" for a marked mob, and right after
 * (lowest priority) puts them back. So each mod still does its own work (blight effects, extra health, enchanted armor,
 * infernal modifiers, health bar, loot) and nothing of theirs is copied here. Both handlers run on the server thread one
 * after another for the same event, so no other mob can see the changed settings.
 *
 * A mob is marked with the entity tags {@link #BLIGHT_TAG} and {@link #INFERNAL_TAG} (spawner mobs carry them in their
 * spawn data, see RLTowerBuilder). Battle tower golems in a mega battle tower are marked when they first join the world.
 * Neither mod is needed: when one isn't installed (or changes its fields) that part is skipped.
 */
@EventBusSubscriber(modid = "fbt")
public final class FBTElites {
    // entity tags that mark a mob to be made a blight / an infernal
    public static final String BLIGHT_TAG = "fbt_blight";
    public static final String INFERNAL_TAG = "fbt_infernal";
    /** Golems whose fireballs also break obsidian: the obsidian tower's and the mega battle towers'. */
    public static final String BREAKS_OBSIDIAN_TAG = "fbt_breaks_obsidian";
    /** Mobs whose iron/diamond armor from the spawn data must stay iron/diamond after Simple Difficulty's blight armor. */
    public static final String KEEP_GEAR_TAG = "fbt_keep_gear";
    // saved on a golem once it has been checked for a mega tower, so the check runs only once
    private static final String GOLEM_CHECKED = "fbt_golem_checked";
    /** Mega battle towers whose golem is always a blight and infernal: RLCraft's (RLStructure) and fbt's own. */
    private static final List<ResourceLocation> MEGA_TOWERS = List.of(
            new ResourceLocation("rec_migrated", "rlcraft_megabattletower"),
            new ResourceLocation("rec_migrated", "rlcraft_megabattletower_water"),
            new ResourceLocation("fbt", "mega_battletower"));
    private static final Logger LOGGER = LoggerFactory.getLogger("fbt");

    // settings changed before the other mods ran, waiting to be put back after them (a stack, in case a join triggers another join)
    private static final Deque<Frame> FRAMES = new ArrayDeque<>();

    private FBTElites() {
    }

    // one changed mob: the old blight chance and elite rarity (null = not changed) and the armour to keep (null = none)
    private record Frame(Mob mob, Float oldBlightChance, Integer oldEliteRarity, ItemStack[] gear) {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    // runs first when a mob joins the world (server only)
    public static void beforeOtherMods(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if ((mob instanceof BattleTowerGolemEntity || isMarked(mob)) && level.getChunkSource().getChunkNow(mob.getBlockX() >> 4, mob.getBlockZ() >> 4) == null) {
            // A golem (or marked mob) placed by world generation joins while its chunk is still being finished. Simple
            // Difficulty's join handler asks for that chunk (getCurrentDifficultyAt) and the server thread waits for it
            // forever. So it joins one tick later instead, when the chunk is done; every mod then handles it as usual.
            event.setCanceled(true);
            com.kettle.fbt.ForgottenBattleTowers.queueServerWork(1, () -> {
                if (!mob.isRemoved()) {
                    level.addFreshEntityWithPassengers(mob);
                }
            });
            return;
        }
        // a golem joining for the first time inside a mega battle tower gets both marks
        if (mob instanceof BattleTowerGolemEntity && !mob.getPersistentData().getBoolean(GOLEM_CHECKED)) {
            mob.getPersistentData().putBoolean(GOLEM_CHECKED, true);
            if (isInMegaTower(level, mob)) {
                mob.addTag(BLIGHT_TAG);
                mob.addTag(INFERNAL_TAG);
                // mega battle towers are largely obsidian, so their golem can blast through it
                mob.addTag(BREAKS_OBSIDIAN_TAG);
            }
        }
        // unmarked mobs are left alone
        boolean blight = mob.getTags().contains(BLIGHT_TAG);
        boolean infernal = mob.getTags().contains(INFERNAL_TAG);
        if (!blight && !infernal) {
            return;
        }
        // force the rolls to always succeed, remembering the old settings
        Float oldChance = blight ? SimpleDifficultyHook.forceBlight() : null;
        Integer oldRarity = infernal ? InfernalHook.forceElite(level) : null;
        // remember the spawner's armour, to put it back if Simple Difficulty swaps it for weaker armour
        ItemStack[] gear = null;
        if (mob.getTags().contains(KEEP_GEAR_TAG)) {
            gear = new ItemStack[4];
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                    gear[slot.getIndex()] = mob.getItemBySlot(slot).copy();
                }
            }
        }
        // remembered for afterOtherMods
        FRAMES.push(new Frame(mob, oldChance, oldRarity, gear));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    // runs last (even if a mod cancelled the join): put the settings back and restore the armour
    public static void afterOtherMods(EntityJoinLevelEvent event) {
        // only for the mob beforeOtherMods changed the settings for
        if (FRAMES.isEmpty() || FRAMES.peek().mob() != event.getEntity()) {
            return;
        }
        Frame frame = FRAMES.pop();
        if (frame.oldBlightChance() != null) {
            SimpleDifficultyHook.restore(frame.oldBlightChance());
        }
        if (frame.oldEliteRarity() != null) {
            InfernalHook.restore(frame.oldEliteRarity());
        }
        if (frame.gear() != null) {
            keepIronOrDiamondArmor(frame.mob(), frame.gear());
        }
    }

    /**
     * A blight gets a random enchanted helmet (and maybe more) from Simple Difficulty, from leather to diamond. Where that
     * replaced the spawner's iron or diamond piece with a weaker one, the spawner's piece goes back on with the blight's
     * enchantments, so the mob keeps both.
     */
    private static void keepIronOrDiamondArmor(Mob mob, ItemStack[] gear) {
        // for each armour slot: if the mob now wears weaker armour than the spawner gave it,
        // put the spawner's piece back, with every enchantment the blight armour had that fits it
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }
            ItemStack wanted = gear[slot.getIndex()];
            ItemStack now = mob.getItemBySlot(slot);
            if (wanted == null || wanted.isEmpty() || isIronOrDiamond(now)) {
                continue;
            }
            ItemStack restored = wanted.copy();
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(now);
            enchantments.forEach((enchantment, lvl) -> {
                if (enchantment.canEnchant(restored)) {
                    restored.enchant(enchantment, lvl);
                }
            });
            mob.setItemSlot(slot, restored);
        }
    }

    // has either mark
    private static boolean isMarked(Mob mob) {
        return mob.getTags().contains(BLIGHT_TAG) || mob.getTags().contains(INFERNAL_TAG);
    }

    // iron or diamond armour
    private static boolean isIronOrDiamond(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor
                && (armor.getMaterial() == ArmorMaterials.IRON || armor.getMaterial() == ArmorMaterials.DIAMOND);
    }

    // true if the mob is inside a piece of one of the MEGA_TOWERS structures
    private static boolean isInMegaTower(ServerLevel level, Mob mob) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (ResourceLocation id : MEGA_TOWERS) {
            Optional<Holder.Reference<Structure>> holder = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, id));
            if (holder.isPresent() && level.structureManager().getStructureWithPieceAt(mob.blockPosition(), holder.get().value()).isValid()) {
                return true;
            }
        }
        return false;
    }

    /** Simple Difficulty (Kettle): com.kettle.simpledifficultyreforged.SimpleDifficultyConfig.blight_chance. */
    static final class SimpleDifficultyHook {
        // found once by reflection, so fbt doesn't need Simple Difficulty to compile or run
        private static Field chance;
        private static boolean looked;

        // look the setting up the first time; null if Simple Difficulty isn't installed
        private static Field field() {
            if (!looked) {
                looked = true;
                try {
                    chance = Class.forName("com.kettle.simpledifficultyreforged.SimpleDifficultyConfig").getField("blight_chance");
                } catch (ReflectiveOperationException | LinkageError e) {
                    LOGGER.info("Simple Difficulty blight_chance not found, tower mobs won't be forced blights ({})", e.toString());
                }
            }
            return chance;
        }

        /** Returns the old chance, or null when Simple Difficulty isn't there. */
        static Float forceBlight() {
            Field f = field();
            if (f == null) {
                return null;
            }
            try {
                float old = f.getFloat(null);
                f.setFloat(null, 2.0F); // the roll is nextFloat() <= chance
                return old;
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }

        // put the old chance back
        static void restore(float old) {
            try {
                field().setFloat(null, old);
            } catch (ReflectiveOperationException | NullPointerException ignored) {
            }
        }
    }

    /** Infernal Mobs (AtomicStryker): InfernalMobsCore.instance().getConfig().get/setEliteRarity. */
    static final class InfernalHook {
        // Infernal Mobs' methods, found once by reflection (null if it isn't installed)
        private static Method instance, initIfNeeded, getConfig, getRarity, setRarity;
        private static boolean looked;

        // look them up the first time; false if Infernal Mobs isn't installed
        private static boolean find() {
            if (!looked) {
                looked = true;
                try {
                    Class<?> core = Class.forName("atomicstryker.infernalmobs.common.InfernalMobsCore");
                    Class<?> config = Class.forName("atomicstryker.infernalmobs.common.InfernalMobsConfig");
                    instance = core.getMethod("instance");
                    initIfNeeded = core.getMethod("initIfNeeded", net.minecraft.world.level.Level.class);
                    getConfig = core.getMethod("getConfig");
                    getRarity = config.getMethod("getEliteRarity");
                    setRarity = config.getMethod("setEliteRarity", int.class);
                } catch (ReflectiveOperationException | LinkageError e) {
                    instance = null;
                    LOGGER.info("Infernal Mobs config not found, tower mobs won't be forced infernal ({})", e.toString());
                }
            }
            return instance != null;
        }

        // Infernal Mobs' config object (loading it first if needed)
        private static Object config(ServerLevel level) throws ReflectiveOperationException {
            Object core = instance.invoke(null);
            if (core == null) {
                return null;
            }
            if (level != null) {
                initIfNeeded.invoke(core, level); // Infernal Mobs loads its config on the first mob that joins
            }
            return getConfig.invoke(core);
        }

        /** Returns the old elite rarity, or null when Infernal Mobs isn't there. */
        static Integer forceElite(ServerLevel level) {
            if (!find()) {
                return null;
            }
            try {
                Object config = config(level);
                if (config == null) {
                    return null;
                }
                int old = (Integer) getRarity.invoke(config);
                setRarity.invoke(config, 1); // the roll is nextInt(eliteRarity) == 0
                return old;
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }

        // put the old rarity back
        static void restore(int old) {
            try {
                Object config = config(null);
                if (config != null) {
                    setRarity.invoke(config, old);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }
}
