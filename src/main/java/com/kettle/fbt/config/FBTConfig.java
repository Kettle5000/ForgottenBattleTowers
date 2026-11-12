package com.kettle.fbt.config;

import com.kettle.fbt.structures.rlcraft.RLTowerType;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** fbt's settings, in config/fbt-common.toml. */
public final class FBTConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TOWER_GROUND;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("battle_towers");
        TOWER_GROUND = b.comment(
                        "Which ground makes which tower type, like 1.12 Battle Towers. Each line: blocks or #tags, separated by commas, = a tower type.",
                        "A tower counts what the ground is at 9 spots under it (a snow layer on top, or snow the world will add later, counts as minecraft:snow).",
                        "The line matched by the most spots picks the type; on a tie the line higher up wins.",
                        "If more spots match no line at all, it's a normal tower: 1 in 5 smooth stone, else cobblestone",
                        "(Nether towers: netherrack, End towers: end).",
                        "Types: cobblestone, cobblestone_mossy, sandstone, ice, smoothstone, netherrack, jungle, basalt, end.",
                        "The obsidian tower (fbt:obsidian_battletower) is always obsidian, whatever the ground.")
                .defineListAllowEmpty(List.of("groundTypes"), () -> List.of(
                        "minecraft:sand, minecraft:red_sand, minecraft:sandstone = sandstone",
                        "minecraft:snow, minecraft:ice = ice",
                        "minecraft:water = cobblestone_mossy",
                        "#minecraft:leaves, #minecraft:logs, minecraft:lily_pad = cobblestone_mossy",
                        "minecraft:basalt, minecraft:polished_basalt, minecraft:smooth_basalt = basalt"),
                        o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    /** One line of groundTypes: which ground it matches and the tower type it gives. */
    public record GroundRule(Predicate<BlockState> matches, RLTowerType type, String line) {
    }

    private static List<GroundRule> rules; // parsed groundTypes, null until first use or after a config change

    private FBTConfig() {
    }

    /** groundTypes, parsed (once, then again after the config file changes). */
    public static List<GroundRule> groundRules() {
        List<GroundRule> parsed = rules;
        if (parsed == null) {
            parsed = parse(TOWER_GROUND.get());
            rules = parsed;
        }
        return parsed;
    }

    /** Re-reads groundTypes after the config loads or changes. */
    public static void onConfigChange(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) rules = null;
    }

    private static List<GroundRule> parse(List<? extends String> lines) {
        List<GroundRule> out = new ArrayList<>();
        for (String line : lines) {
            int eq = line.lastIndexOf('=');
            RLTowerType type = eq < 0 ? null : RLTowerType.byName(line.substring(eq + 1).trim());
            if (type == null || type == RLTowerType.NULL) {
                LOGGER.warn("fbt groundTypes: skipping \"{}\" (needs \"blocks = tower type\" with a known type)", line);
                continue;
            }
            Predicate<BlockState> matches = s -> false;
            for (String entry : line.substring(0, eq).split(",")) {
                Predicate<BlockState> one = blockOrTag(entry.trim(), line);
                if (one != null) matches = matches.or(one);
            }
            out.add(new GroundRule(matches, type, line));
        }
        return List.copyOf(out);
    }

    /** "modid:block" or "#modid:tag" as a block test; null (logged) if it's not a known block. */
    private static Predicate<BlockState> blockOrTag(String entry, String line) {
        if (entry.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(1));
            if (id == null) {
                LOGGER.warn("fbt groundTypes: bad tag \"{}\" in \"{}\"", entry, line);
                return null;
            }
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
            return s -> s.is(tag);
        }
        ResourceLocation id = ResourceLocation.tryParse(entry);
        Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
        if (block == null || !ForgeRegistries.BLOCKS.containsKey(id)) {
            LOGGER.warn("fbt groundTypes: unknown block \"{}\" in \"{}\"", entry, line);
            return null;
        }
        return s -> s.is(block);
    }
}
