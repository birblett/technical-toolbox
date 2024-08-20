package com.birblett.impl;

import com.birblett.TechnicalToolbox;
import com.birblett.accessor.command.stat.StatTracker;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.impl.util.StringUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ScoreboardCriterionArgumentType;
import net.minecraft.command.argument.ScoreboardSlotArgumentType;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatCommand {

    private static final Pattern CAMELCASE_WORD = Pattern.compile("[A-Z]?[a-z]+|: ");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register((CommandManager.literal("stat")
                .then(CommandManager.literal("track")
                        .requires(ServerCommandSource::isExecutedByPlayer)
                        .then(CommandManager.argument("stat", ScoreboardCriterionArgumentType.scoreboardCriterion())
                                .suggests((context, builder) ->ScoreboardCriterionArgumentType.scoreboardCriterion()
                                        .listSuggestions(context, builder))
                                .then(CommandManager.argument("slot", ScoreboardSlotArgumentType.scoreboardSlot())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(
                                                ScoreboardDisplaySlot.values()).map(ScoreboardDisplaySlot::asString), builder))
                                        .executes(StatCommand::getStat))))));
    }

    private static int getStat(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            StatTracker tracker = (StatTracker) player;
            ServerScoreboard scoreboard = context.getSource().getServer().getScoreboard();
            ScoreboardCriterion stat = ScoreboardCriterionArgumentType.getCriterion(context, "stat");
            ScoreboardDisplaySlot scoreboardDisplaySlot = ScoreboardSlotArgumentType.getScoreboardSlot(context, "slot");
            String name = "technical_toolbox.tracked_stats." + stat.getName().replace(":", ".");
            ScoreboardObjective objective = scoreboard.getNullableObjective(name);
            boolean shouldAdd = true;
            // create new object if dne
            if (objective == null) {
                Text t;
                String translatableStat = stat.getName(), prefix = "";
                String[] split = translatableStat.split(":"), split2;
                // handle format minecraft.[action]:minecraft:[block/item]
                if (split.length == 2 && (split2 = split[1].split("\\.")).length == 2) {
                    prefix = "stat_type." + split[0];
                    if (Registries.ITEM.get(Identifier.of(split2[0], split2[1])) instanceof BlockItem) {
                        translatableStat = "block." + translatableStat.split(":")[1];
                    } else {
                        translatableStat = "item." + translatableStat.split(":")[1];
                    }
                    t = TextUtils.translatable(prefix).append(": ").append(TextUtils.translatable(translatableStat));
                }
                // handle simple criterion
                else {
                    translatableStat = translatableStat.replace(".", ": ");
                    Matcher m = CAMELCASE_WORD.matcher(translatableStat);
                    StringBuilder str = new StringBuilder();
                    while (m.find()) {
                        String match = m.group();
                        str.append(StringUtil.capitalize(match + (": ".equals(match) ? " " : "")));
                    }
                    t = TextUtils.formattable(str.toString());
                }
                objective = scoreboard.addObjective(name.replace(",", "."), stat, t, ScoreboardCriterion.RenderType.INTEGER,
                        true, null);
                scoreboard.addScoreboardObjective(objective);
                tracker.technicalToolbox$UpdateSlot(scoreboardDisplaySlot, objective);
                shouldAdd = false;
            }
            // don't add if scoreboard already has objective in slot
            for (ScoreboardDisplaySlot slot : ScoreboardDisplaySlot.values()) {
                if (scoreboard.getObjectiveForSlot(slot) == objective) {
                    shouldAdd = false;
                }
            }
            // add objective if not already registered on the client
            if (shouldAdd && !tracker.technicalToolbox$HasObjective(objective)) {
                player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(objective,
                        ScoreboardObjectiveUpdateS2CPacket.ADD_MODE));
                tracker.technicalToolbox$UpdateSlot(scoreboardDisplaySlot, objective);
            }
            // send display packet and update objective values
            player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(scoreboardDisplaySlot, objective));
            for (ScoreboardEntry scoreboardEntry : scoreboard.getScoreboardEntries(objective)) {
                player.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(scoreboardEntry.owner(), objective.getName(),
                        scoreboardEntry.value(), Optional.ofNullable(scoreboardEntry.display()), Optional.ofNullable(
                        scoreboardEntry.numberFormatOverride())));
            }
        }
        return 1;
    }

}
