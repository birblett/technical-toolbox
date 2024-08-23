package com.birblett.impl.command.stat;

import com.birblett.accessor.command.stat.StatTracker;
import com.birblett.util.TextUtils;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Map;
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
                        .then(CommandManager.literal("compound")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .suggests(StatCommand::getCompoundSuggestions)
                                        .then(CommandManager.literal("below_name")
                                                .executes(context -> StatCommand.trackCompound(context, ScoreboardDisplaySlot.BELOW_NAME)))
                                        .then(CommandManager.literal("list")
                                                .executes(context -> StatCommand.trackCompound(context, ScoreboardDisplaySlot.LIST)))
                                        .then(CommandManager.literal("sidebar")
                                                .executes(context -> StatCommand.trackCompound(context, ScoreboardDisplaySlot.SIDEBAR))))))
                .then(CommandManager.literal("compound")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .then(CommandManager.argument("displayName", TextArgumentType.text(commandRegistryAccess))
                                                .executes(StatCommand::addCompound))))
                        .then(CommandManager.literal("modify")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests(StatCommand::getCompoundSuggestions)
                                        .then(CommandManager.literal("modifier")
                                                .then(CommandManager.argument("modifier", DoubleArgumentType.doubleArg())
                                                        .executes(StatCommand::setCompoundModifier)))
                                        .then(CommandManager.literal("modifier_mode")
                                                .then(CommandManager.literal("multiply")
                                                        .executes(context -> StatCommand.setCompoundModifierMode(context, true)))
                                                .then(CommandManager.literal("divide")
                                                        .executes(context -> StatCommand.setCompoundModifierMode(context, false))))
                                        .then(CommandManager.literal("remove")
                                                .executes(StatCommand::removeCompound))
                                        .then(CommandManager.literal("track")
                                                .then(CommandManager.argument("criterion",
                                                                ScoreboardCriterionArgumentType.scoreboardCriterion())
                                                        .executes(StatCommand::addCompoundCriterion)))
                                        .then(CommandManager.literal("untrack")
                                                .then(CommandManager.argument("criterion", StringArgumentType.greedyString())
                                                        .suggests(StatCommand::getCriterionSuggestions)
                                                        .executes(StatCommand::removeCompoundCriterion))))))
                .then(CommandManager.literal("refresh")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(StatCommand::refreshStats))));
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

    /**
     * @return suggestions containing existing compound stats.
     */
    private static CompletableFuture<Suggestions> getCompoundSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(TrackedStatManager.getCompoundNames(), builder);
    }

    /**
     * Add an empty compound stat.
     * @param context command context
     */
    private static int addCompound(CommandContext<ServerCommandSource> context) {
        String name = context.getArgument("name", String.class);
        String objectiveName = TrackedStatManager.COMPOUND_STAT_PREFIX + name;
        Text text = context.getArgument("displayName", Text.class);
        ServerScoreboard scoreboard = context.getSource().getServer().getScoreboard();
        if (scoreboard.getNullableObjective(objectiveName) == null) {
            for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
                ((StatTracker) player).technicalToolbox$UpdateObjective(TrackedStatManager.createNewObjective(context.getSource()
                        .getServer(), objectiveName, ScoreboardCriterion.DUMMY, text));
            }
            context.getSource().sendFeedback(() -> TextUtils.formattable("Created new compound ").append(TextUtils.formattable(name)
                    .formatted(Formatting.GREEN)), false);
        }
        else {
            context.getSource().sendError(TextUtils.formattable("Objective " + name +  " already exists"));
        }
        return 1;
    }

    /**
     * Add a tracked criterion to an existing compound stat
     * @param context command context
     */
    private static int addCompoundCriterion(CommandContext<ServerCommandSource> context) {
        String name = context.getArgument("name", String.class);
        CompoundStat stat = TrackedStatManager.getCompoundStat(name);
        if (stat != null) {
            ScoreboardCriterion criterion = context.getArgument("criterion", ScoreboardCriterion.class);
            stat.addCriteria(criterion);
            TrackedStatManager.refreshCompound(context.getSource().getServer(), stat, null);
            context.getSource().sendFeedback(() -> TextUtils.formattable("Compound stat ").append(TextUtils.formattable(name)
                    .formatted(Formatting.GREEN)).append(TextUtils.formattable(" is now tracking stat ").formatted(Formatting.WHITE)
                    .append(TextUtils.formattable(criterion.getName()).formatted(Formatting.AQUA))), false);
        }
        else {
            context.getSource().sendError(TextUtils.formattable("Compound stat " + name + " does not exist"));
        }
        return 1;
    }

    /**
     * @return criteria associated with a provided compound stat, if it exists
     */
    private static CompletableFuture<Suggestions> getCriterionSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        String name = context.getArgument("name", String.class);
        CompoundStat stat = TrackedStatManager.getCompoundStat(name);
        if (stat != null) {
            return CommandSource.suggestMatching(stat.criteria.stream().map(ScoreboardCriterion::getName), builder);
        }
        return Suggestions.empty();
    }

    /**
     * Remove a tracked criterion from a compound stat
     * @param context command context
     */
    private static int removeCompoundCriterion(CommandContext<ServerCommandSource> context) {
        String name = context.getArgument("name", String.class);
        CompoundStat stat = TrackedStatManager.getCompoundStat(name);
        if (stat != null) {
            String criterionName = context.getArgument("criterion", String.class);
            ScoreboardCriterion.getOrCreateStatCriterion(criterionName).ifPresentOrElse(criterion -> {
                if (stat.criteria.contains(criterion)) {
                    stat.removeCriteria(criterion);
                    TrackedStatManager.refreshCompound(context.getSource().getServer(), stat, null);
                    context.getSource().sendFeedback(() -> TextUtils.formattable("Compound stat ").append(TextUtils.formattable(name)
                                    .formatted(Formatting.GREEN)).append(TextUtils.formattable(" is no longer tracking tracking stat ")
                                    .formatted(Formatting.WHITE).append(TextUtils.formattable(criterion.getName()).formatted(Formatting.YELLOW))),
                            false);
                }
                else {
                    context.getSource().sendError(TextUtils.formattable("Compound stat " + name + " is not tracking " + criterionName));
                }
            }, () -> context.getSource().sendError(TextUtils.formattable( criterionName + " not found")));
        }
        else {
            context.getSource().sendError(TextUtils.formattable("Compound stat " + name + " does not exist"));
        }
        return 1;
    }

    /**
     * Start tracking a compound stat.
     * @param context command context
     * @param scoreboardDisplaySlot slot to display to
     */
    private static int trackCompound(CommandContext<ServerCommandSource> context, ScoreboardDisplaySlot scoreboardDisplaySlot) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            String name = context.getArgument("name", String.class);
            CompoundStat stat = TrackedStatManager.getCompoundStat(name);
            if (stat != null) {
                StatCommand.updatePlayerScoreboard(player, stat.objective, scoreboardDisplaySlot,
                        !((StatTracker) player).technicalToolbox$HasObjective(stat.objective));
            }
            else {
                context.getSource().sendError(TextUtils.formattable("Compound stat " + name + " does not exist"));
            }
        }
        else {
            context.getSource().sendError(TextUtils.formattable("Stat tracking can only be done by players"));
        }
        return 1;
    }

    /**
     * Removes and stops tracking a compound stat.
     */
    private static int removeCompound(CommandContext<ServerCommandSource> context) {
        String name = context.getArgument("name", String.class);
        CompoundStat stat = TrackedStatManager.getCompoundStat(name);
        if (stat != null) {
            context.getSource().getServer().getScoreboard().removeObjective(stat.objective);
            context.getSource().sendFeedback(() -> TextUtils.formattable("Removed compound stat ").append(TextUtils.formattable(name)
                    .formatted(Formatting.GREEN)), false);
        }
        else {
            context.getSource().sendError(TextUtils.formattable("Compound stat " + name + " does not exist"));
        }
        return 1;
    }

    /**
     * Sets the modifier value for a compound stat.
     * @param context command context
     */
    private static int setCompoundModifier(CommandContext<ServerCommandSource> context) {
        String name = context.getArgument("name", String.class);
        CompoundStat stat = TrackedStatManager.getCompoundStat(name);
        if (stat != null) {
            double modifier = context.getArgument("modifier", Double.class);
            stat.setModifier(modifier);
            TrackedStatManager.refreshCompound(context.getSource().getServer(), stat, null);
            context.getSource().sendFeedback(() -> TextUtils.formattable("Set modifier for compound stat ")
                    .append(TextUtils.formattable(name).formatted(Formatting.GREEN)).append(TextUtils.formattable(" to ")
                            .formatted(Formatting.WHITE).append(TextUtils.formattable(String.valueOf(modifier))
                                    .formatted(Formatting.AQUA))), false);
        }
        else {
            context.getSource().sendError(TextUtils.formattable("Compound stat " + name + " does not exist"));
            return 0;
        }
        return 1;
    }

    private static int setCompoundModifierMode(CommandContext<ServerCommandSource> context, boolean mode) {
        String name = context.getArgument("name", String.class);
        CompoundStat stat = TrackedStatManager.getCompoundStat(name);
        if (stat != null) {
            stat.setMode(mode);
            TrackedStatManager.refreshCompound(context.getSource().getServer(), stat, null);
            context.getSource().sendFeedback(() -> TextUtils.formattable("Set mode for compound stat ")
                    .append(TextUtils.formattable(name).formatted(Formatting.GREEN)).append(TextUtils.formattable(" to ")
                            .formatted(Formatting.WHITE).append(TextUtils.formattable(mode ? "multiply" : "divide")
                                    .formatted(Formatting.AQUA))), false);
        }
        else {
            context.getSource().sendError(TextUtils.formattable("Compound stat " + name + " does not exist"));
            return 0;
        }
        return 1;
    }

    /**
     * Refreshes all tracked objective values.
     */
    private static int refreshStats(CommandContext<ServerCommandSource> context) {
        MinecraftServer server = context.getSource().getServer();
        server.getPlayerManager().saveAllPlayerData();
        Map<GameProfile, ServerStatHandler> profiles = TrackedStatManager.getStatistics(server);
        for (ScoreboardObjective objective : TrackedStatManager.TRACKED_STATS) {
            TrackedStatManager.refreshStat(server, objective, profiles);
        }
        for (CompoundStat compound : TrackedStatManager.TRACKED_COMPOUNDS) {
            TrackedStatManager.refreshCompound(server, compound, profiles);
        }
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
