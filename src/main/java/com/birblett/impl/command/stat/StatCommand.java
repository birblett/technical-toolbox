package com.birblett.impl.command.stat;

import com.birblett.accessor.command.stat.StatTracker;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.loader.impl.util.StringUtil;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ScoreboardCriterionArgumentType;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatCommand {

    private static final Pattern CAMELCASE_WORD = Pattern.compile("[A-Z]?[a-z]+|:");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess commandRegistryAccess) {
        dispatcher.register((CommandManager.literal("stat")
                .then(CommandManager.literal("track")
                        .requires(ServerCommandSource::isExecutedByPlayer)
                        .then(CommandManager.literal("criterion")
                                .then(CommandManager.argument("criterion", ScoreboardCriterionArgumentType.scoreboardCriterion())
                                        .then(CommandManager.literal("below_name")
                                                .executes(context -> StatCommand.addAndTrackCriterion(context, ScoreboardDisplaySlot.BELOW_NAME)))
                                        .then(CommandManager.literal("list")
                                                .executes(context -> StatCommand.addAndTrackCriterion(context, ScoreboardDisplaySlot.LIST)))
                                        .then(CommandManager.literal("sidebar")
                                                .executes(context -> StatCommand.addAndTrackCriterion(context, ScoreboardDisplaySlot.SIDEBAR)))))
                        .then(CommandManager.literal("aggregate")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .suggests(StatCommand::aggregateSuggestions)
                                        .then(CommandManager.literal("below_name")
                                                .executes(context -> StatCommand.trackAggregate(context, ScoreboardDisplaySlot.BELOW_NAME)))
                                        .then(CommandManager.literal("list")
                                                .executes(context -> StatCommand.trackAggregate(context, ScoreboardDisplaySlot.LIST)))
                                        .then(CommandManager.literal("sidebar")
                                                .executes(context -> StatCommand.trackAggregate(context, ScoreboardDisplaySlot.SIDEBAR))))))
                .then(CommandManager.literal("add")
                        .then(CommandManager.literal("aggregate")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .then(CommandManager.argument("displayName", TextArgumentType.text(commandRegistryAccess))
                                                .executes(StatCommand::addAggregate)))))
                .then(CommandManager.literal("modify")
                        .then(CommandManager.literal("aggregate")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .suggests(StatCommand::aggregateSuggestions)
                                        .then(CommandManager.literal("add")
                                                .then(CommandManager.argument("criterion", ScoreboardCriterionArgumentType.scoreboardCriterion())
                                                        .executes(StatCommand::addAggregateCriterion))))))));
    }

    /**
     * Adds a scoreboard objective based on an existing criterion (if it does not yet exist)
     * and sends packets to the executing player to force that scoreboard to display.
     */
    private static int addAndTrackCriterion(CommandContext<ServerCommandSource> context, ScoreboardDisplaySlot scoreboardDisplaySlot) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            StatTracker tracker = (StatTracker) player;
            ServerScoreboard scoreboard = context.getSource().getServer().getScoreboard();
            ScoreboardCriterion criterion = ScoreboardCriterionArgumentType.getCriterion(context, "criterion");
            String name = TrackedStatManager.TRACKED_STAT_PREFIX + criterion.getName().replace(":", ".");
            ScoreboardObjective objective = scoreboard.getNullableObjective(name);
            boolean shouldAdd = true;
            // create new object if dne
            if (objective == null) {
                MutableText text;
                String translatableStat = criterion.getName(), prefix;
                String[] split = translatableStat.split(":"), split2;
                // handle format minecraft.[action]:minecraft:[block/item]
                if (split.length == 2 && (split2 = split[1].split("\\.")).length == 2) {
                    prefix = "stat_type." + split[0];
                    if (Registries.ITEM.get(Identifier.of(split2[0], split2[1])) instanceof BlockItem) {
                        translatableStat = "block." + translatableStat.split(":")[1];
                    }
                    else if (Registries.ITEM.get(Identifier.of(split2[0], split2[1])) != Items.AIR) {
                        translatableStat = "item." + translatableStat.split(":")[1];
                    }
                    else {
                        translatableStat = "stat." + translatableStat.split(":")[1];
                        prefix = "";
                    }
                    text = TextUtils.formattable("");
                    if (!prefix.isEmpty()) {
                        text = TextUtils.translatable(prefix).append(": ");
                    }
                    text = text.append(TextUtils.translatable(translatableStat));
                }
                // handle simple criterion
                else {
                    translatableStat = translatableStat.replace(".", ": ");
                    Matcher m = CAMELCASE_WORD.matcher(translatableStat);
                    StringBuilder str = new StringBuilder();
                    while (m.find()) {
                        String match = m.group();
                        str.append(StringUtil.capitalize((":".equals(match) ? "" : " ") + match));
                    }
                    text = TextUtils.formattable(str.toString().strip());
                }
                objective = TrackedStatManager.createNewObjective(context.getSource().getServer(), name.replace(":", "."),
                        criterion, text);
                tracker.technicalToolbox$UpdateSlot(scoreboardDisplaySlot, objective);
                shouldAdd = false;
            }
            StatCommand.updatePlayerScoreboard(player, objective, scoreboardDisplaySlot, shouldAdd);
        }
        else {
            context.getSource().sendError(TextUtils.formattable("Stat tracking can only be done by players"));
        }
        return 1;
    }

    private static CompletableFuture<Suggestions> aggregateSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(TrackedStatManager.getAggregateNames(), builder);
    }

    /**
     * Add an empty aggregate stat.
     * @param context command context
     */
    private static int addAggregate(CommandContext<ServerCommandSource> context) {
        String name = context.getArgument("name", String.class);
        String objectiveName = TrackedStatManager.TRACKED_AGGREGATE_PREFIX + name;
        Text text = context.getArgument("displayName", Text.class);
        ServerScoreboard scoreboard = context.getSource().getServer().getScoreboard();
        if (scoreboard.getNullableObjective(objectiveName) == null) {
            context.getSource().getServer().getPlayerManager().getPlayerList().forEach(player ->
                    ((StatTracker) player).technicalToolbox$UpdateObjective(TrackedStatManager.createNewObjective(context.getSource()
                                    .getServer(), objectiveName, ScoreboardCriterion.DUMMY, text)));
            context.getSource().sendFeedback(() -> TextUtils.formattable("Created new aggregate ").append(TextUtils.formattable(name)
                    .formatted(Formatting.GREEN)), false);
        }
        else {
            context.getSource().sendError(TextUtils.formattable("Aggreggate " + name +  " already exists"));
        }
        return 1;
    }

    /**
     * Add a tracked criterion to an existing aggregate stat
     * @param context command context
     */
    private static int addAggregateCriterion(CommandContext<ServerCommandSource> context) {
        String name = context.getArgument("name", String.class);
        TrackedStatManager.AggregateStatWrapper stat = TrackedStatManager.getAggregate(name);
        if (stat != null) {
            ScoreboardCriterion criterion = context.getArgument("criterion", ScoreboardCriterion.class);
            stat.addCriteria(criterion);
            TrackedStatManager.refreshAggregate(context.getSource().getServer(), stat);
            context.getSource().sendFeedback(() -> TextUtils.formattable("Aggregate ").append(TextUtils.formattable(name)
                    .formatted(Formatting.GREEN)).append(TextUtils.formattable(" is now tracking stat ").formatted(Formatting.WHITE)
                    .append(TextUtils.formattable(criterion.getName()).formatted(Formatting.AQUA))), false);
        }
        else {
            context.getSource().sendError(TextUtils.formattable("Aggregate " + name + " does not exist"));
        }
        return 1;
    }

    /**
     * Start tracking an aggregate stat.
     * @param context command context
     * @param scoreboardDisplaySlot slot to display to
     */
    private static int trackAggregate(CommandContext<ServerCommandSource> context, ScoreboardDisplaySlot scoreboardDisplaySlot) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            String name = context.getArgument("name", String.class);
            TrackedStatManager.AggregateStatWrapper stat = TrackedStatManager.getAggregate(name);
            if (stat != null) {
                StatCommand.updatePlayerScoreboard(player, stat.objective, scoreboardDisplaySlot,
                        !((StatTracker) player).technicalToolbox$HasObjective(stat.objective));
            }
            else {
                context.getSource().sendError(TextUtils.formattable("Aggregate " + name + " does not exist"));
            }
        }
        else {
            context.getSource().sendError(TextUtils.formattable("Stat tracking can only be done by players"));
        }
        return 1;
    }

    private static int statRefresher(CommandContext<ServerCommandSource> context) {
        return 1;
    }

    /**
     * Sends packets to set the player scoreboard to a specific objective, and update its values
     * @param player target player
     * @param objective objective to force
     * @param displaySlot display slot to display objective to
     * @param shouldAdd should default to true, but can pass if processed with other logic beforehand
     */
    public static void updatePlayerScoreboard(ServerPlayerEntity player, ScoreboardObjective objective, ScoreboardDisplaySlot displaySlot, boolean shouldAdd) {
        if (player.getServer() != null) {
            Scoreboard scoreboard = player.getServer().getScoreboard();
            // don't add if scoreboard already has objective in slot
            for (ScoreboardDisplaySlot slot : ScoreboardDisplaySlot.values()) {
                if (scoreboard.getObjectiveForSlot(slot) == objective) {
                    shouldAdd = false;
                }
            }
            // add objective if not already registered on the client
            StatTracker tracker = (StatTracker) player;
            if (shouldAdd && !tracker.technicalToolbox$HasObjective(objective)) {
                player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(objective,
                        ScoreboardObjectiveUpdateS2CPacket.ADD_MODE));
                tracker.technicalToolbox$UpdateSlot(displaySlot, objective);
            }
            // send display packet and update objective values
            player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(displaySlot, objective));
            for (ScoreboardEntry scoreboardEntry : scoreboard.getScoreboardEntries(objective)) {
                player.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(scoreboardEntry.owner(), objective.getName(),
                        scoreboardEntry.value(), Optional.ofNullable(scoreboardEntry.display()), Optional.ofNullable(
                        scoreboardEntry.numberFormatOverride())));
            }
        }
    }

}
