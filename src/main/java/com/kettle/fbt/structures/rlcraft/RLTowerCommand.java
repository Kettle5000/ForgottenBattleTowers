package com.kettle.fbt.structures.rlcraft;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * /fbt spawntower x y z type underground: builds a 1.12 RLCraft Battle Tower, like 1.12's /spawnbattletower.
 * type is the 1.12 number: 1 cobblestone, 2 mossy, 3 sandstone, 4 ice, 5 smooth stone, 6 netherrack, 7 jungle;
 * 8 is the 12 floor obsidian tower (fbt:obsidian_battletower), 9 basalt, 10 end.
 * /fbt towertype x z: which type a tower there would get from the ground (fbt-common.toml groundTypes), or why the
 * site fails 1.12's checks. Nothing is built.
 * y is the ground height the tower is built from (1.12 builds surface towers from y - 6 up to y 120).
 * The tower isn't registered as a structure, so killing its golem doesn't collapse it.
 */
@EventBusSubscriber
public class RLTowerCommand {
    // runs when the server sets up its commands
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        // /fbt (needs permission level 2, i.e. an operator) spawntower <pos> <type 1-10> <underground true/false>, or towertype <pos>
        event.getDispatcher().register(Commands.literal("fbt").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawntower")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("type", IntegerArgumentType.integer(1, 10))
                                        .then(Commands.argument("underground", BoolArgumentType.bool())
                                                .executes(c -> spawn(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"),
                                                        IntegerArgumentType.getInteger(c, "type"), BoolArgumentType.getBool(c, "underground")))))))
                .then(Commands.literal("towertype")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> towerType(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"))))));
    }

    // the type the ground at pos's x and z picks, like world generation does (outside the Nether the y is ignored:
    // it uses the real surface)
    private static int towerType(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        // in the Nether, the cavern floor nearest the given y (like fbt:nether_battletower, which falls back to netherrack)
        boolean nether = level.dimension() == net.minecraft.world.level.Level.NETHER;
        int y = nether ? RLTowerBuilder.caveFloorNear(level, pos.getX(), pos.getY(), pos.getZ()) : RLTowerBuilder.getSpawnHeight(level, pos.getX(), pos.getZ());
        RLTowerType t = y == Integer.MIN_VALUE ? null
                : RLTowerBuilder.chooseType(level, level.getRandom(), pos.getX(), y, pos.getZ(), nether ? RLTowerType.NETHERRACK : null, nether);
        String result = t != null ? "a " + t.name().toLowerCase() + " tower" : "no tower (" + RLTowerBuilder.lastFail + ")";
        source.sendSuccess(() -> Component.literal("Ground at " + pos.getX() + " " + y + " " + pos.getZ() + " gives " + result), false);
        return t != null ? 1 : 0;
    }

    // builds the tower right away at pos and reports it in chat
    private static int spawn(CommandSourceStack source, BlockPos pos, int type, boolean underground) {
        ServerLevel level = source.getLevel();
        // the type number is the enum's position (1 cobblestone ... 8 obsidian, 9 basalt, 10 end)
        RLTowerType t = RLTowerType.byOrdinal(type);
        // the obsidian tower always gets 12 floors; 0 means the normal 1.12 height
        RLTowerBuilder.generate(level, level.getRandom(), pos.getX(), pos.getY(), pos.getZ(), t, underground, t == RLTowerType.OBSIDIAN ? 12 : 0);
        source.sendSuccess(() -> Component.literal("Battle Tower " + t + (underground ? " (underground)" : "") + " built at " + pos.toShortString()), true);
        // 1 = command succeeded
        return 1;
    }
}
