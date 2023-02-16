package com.domain.redstonetools.features.commands.colorcode;

import com.domain.redstonetools.features.Feature;
import com.domain.redstonetools.features.commands.CommandFeature;
import com.domain.redstonetools.utils.CharTreeNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.fabric.FabricPlayer;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Mask2D;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

@Feature(name = "/colorcode")
public class ColorCodeFeature extends CommandFeature<ColorCodeFeatureOptions> {

    static final CharTreeNode MATCH_TARGET_PATH = new CharTreeNode();

    static {
        MATCH_TARGET_PATH.insert("_wool");
        MATCH_TARGET_PATH.insert("_stained_glass");
        MATCH_TARGET_PATH.insert("_concrete");
        MATCH_TARGET_PATH.insert("_terracotta");
        MATCH_TARGET_PATH.insert("_concrete_powder");
    }

    // checks if the block at the position
    // is a target for the transformation
    private boolean checkBlock(World world,
                               BlockVector3 pos,
                               boolean onlyWhite) {
        BlockState state = world.getBlock(pos);
        if (state == null)
            return false;

        // check if it is a target
        String blockId = state.getBlockType().getId();
        int colorlessBlockIdIndex;
        if ((colorlessBlockIdIndex = MATCH_TARGET_PATH.findEndMatch(blockId)) == -1)
            return false;
        String colorlessBlockId = blockId.substring(colorlessBlockIdIndex);
        if (onlyWhite && !blockId.substring(0, colorlessBlockIdIndex).equals("white"))
            return false;

        return true;
    }

    // changes the block at the given position
    // to the correct color if it is a target
    // all non targets should have been excluded
    // by the mask, but just in case
    private BaseBlock changeBlock(World world,
                                  BlockVector3 pos,
                                  String color) {
        BlockState state = world.getBlock(pos);
        BlockType oldType = state.getBlockType();
        String blockId = oldType.getId();

        // get colorless id
        int colorlessBlockIdIndex = MATCH_TARGET_PATH.findEndMatch(blockId);
        if (colorlessBlockIdIndex == -1)
            return state.toBaseBlock();
        String colorlessBlockId = blockId.substring(colorlessBlockIdIndex);

        // get colored block
        String coloredId = "minecraft:" + color + colorlessBlockId;
        BlockType blockType = BlockType.REGISTRY.get(coloredId);
        if (blockType == null)
            return state.toBaseBlock();

        return blockType.getDefaultState().toBaseBlock();
    }

    /*
        Command
     */

    @Override
    protected int execute(ServerCommandSource source, ColorCodeFeatureOptions options) throws CommandSyntaxException {
        final WorldEdit worldEdit = WorldEdit.getInstance();

        ServerPlayerEntity player = source.getPlayer();
        FabricPlayer wePlayer = FabricAdapter.adaptPlayer(player);
        LocalSession playerSession = worldEdit.getSessionManager().getIfPresent(wePlayer);

        Region selection = null;
        if (playerSession != null) {
            try {
                selection = playerSession.getSelection();
            } catch (Exception ignored) { }
        }

        if (selection == null) {
            source.sendError(Text.of("Please make a selection.").getWithStyle(
                    Style.EMPTY.withColor(Formatting.RED)).get(0));
            return -1;
        }

        String color = options.argColor.getValue();
        boolean onlyWhite = options.argOnlyWhite.getValue();

        // for each block in the selection
        final World world = FabricAdapter.adapt(player.getWorld());
        try (EditSession session = worldEdit.newEditSession(FabricAdapter.adapt(player.getWorld()))) {
            AtomicInteger counter = new AtomicInteger();

            // create mask
            session.setMask(new Mask() {
                @Override
                public boolean test(BlockVector3 vector) {
                    return checkBlock(world, vector, onlyWhite);
                }

                @Nullable
                @Override
                public Mask2D toMask2D() { return null; }
            });

            // create pattern and execute block set
            counter.set(session.setBlocks(selection, new Pattern() {
                @Override
                public BaseBlock applyBlock(BlockVector3 position) {
                    return changeBlock(world, position, color);
                }
            }));

            Operations.complete(session.commit());

            // call remember to allow undo
            playerSession.remember(session);

            source.sendFeedback(Text.of("Successfully changed " + counter.get() + " blocks to color " + color)
                            .getWithStyle(Style.EMPTY.withColor(Formatting.GRAY)).get(0),
                    true);
        } catch (Exception e) {
            source.sendFeedback(Text.of("An error occurred").getWithStyle(Style.EMPTY.withColor(Formatting.RED)).get(0), false);
        }

//        source.sendFeedback(Text.of("Successfully replaced " + counter.get() + " blocks with " +
//                "color " + color + ""), false);

        return 0;
    }

}
