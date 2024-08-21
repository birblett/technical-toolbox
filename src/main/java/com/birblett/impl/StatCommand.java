/*

MIT License

Copyright (c) 2024 Lexi Larkin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

 */

package com.birblett.impl;

import com.birblett.TechnicalToolbox;
import com.birblett.accessor.command.stat.StatTracker;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.impl.util.StringUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ScoreboardCriterionArgumentType;
import net.minecraft.command.argument.ScoreboardSlotArgumentType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
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
import net.minecraft.stat.Stat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.UserCache;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatCommand {

    private static final Pattern CAMELCASE_WORD = Pattern.compile("[A-Z]?[a-z]+|: ");
    private static final Map<UUID, GameProfile> PROFILE_CACHE = new HashMap<>();

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
                                        .executes(StatCommand::addAndTrackStat))))));
    }

    /**
     * Adds a scoreboard objective based on an existing criterion (if it does not yet exist)
     * and sends packets to the executing player to force that scoreboard to display.
     */
    private static int addAndTrackStat(CommandContext<ServerCommandSource> context) {
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
                MutableText t;
                String translatableStat = stat.getName(), prefix;
                String[] split = translatableStat.split(":"), split2;
                // handle format minecraft.[action]:minecraft:[block/item]
                if (split.length == 2 && (split2 = split[1].split("\\.")).length == 2) {
                    prefix = "stat_type." + split[0];
                    if (Registries.ITEM.get(Identifier.of(split2[0], split2[1])) instanceof BlockItem) {
                        translatableStat = "block." + translatableStat.split(":")[1];
                    }
                    else if (Registries.ITEM.get(Identifier.of(split2[0], split2[1])) != Items.AIR) {
                        translatableStat = "item." + translatableStat.split(":")[1];
                        TechnicalToolbox.log("FROG");
                    }
                    else {
                        translatableStat = "stat." + translatableStat.split(":")[1];
                        prefix = "";
                    }
                    t = TextUtils.formattable("");
                    if (!prefix.isEmpty()) {
                        t = TextUtils.translatable(prefix).append(": ");
                    }
                    t = t.append(TextUtils.translatable(translatableStat));
                }
                // handle simple criterion
                else {
                    translatableStat = translatableStat.replace(".", ": ");
                    Matcher m = CAMELCASE_WORD.matcher(translatableStat);
                    StringBuilder str = new StringBuilder();
                    while (m.find()) {
                        String match = m.group();
                        str.append(StringUtil.capitalize(match + (": ".equals(match) ? "" : " ")));
                    }
                    t = TextUtils.formattable(str.toString());
                }
                objective = scoreboard.addObjective(name.replace(":", "."), stat, t, ScoreboardCriterion.RenderType.INTEGER,
                        true, null);
                scoreboard.addScoreboardObjective(objective);
                tracker.technicalToolbox$UpdateSlot(scoreboardDisplaySlot, objective);
                StatCommand.importStats(context.getSource().getServer(), scoreboard, objective, name.replace(",", "."));
                shouldAdd = false;
            }
            StatCommand.updatePlayerScoreboard(player, objective, scoreboardDisplaySlot, shouldAdd);
        }
        return 1;
    }

    private static int statRefresher() {
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

    /**
     * Stat importer, ported and slightly modified from
     * <a href="https://github.com/qixils/scoreboard-stats-import">qixils' stat importer</a>
     */
    private static void importStats(MinecraftServer server, Scoreboard scoreboard, ScoreboardObjective objective, String statName) {
        UserCache userCache = server.getUserCache();
        ScoreboardCriterion criterion = objective.getCriterion();
        if (criterion instanceof Stat<?> stat && userCache != null) {
            MinecraftSessionService sessionService = server.getSessionService();
            try {
                for (File file : FileUtils.listFiles(ServerUtil.getLocalPath(server, "stats").toFile(), new String[]{"json"},
                        false)) {
                    String name = file.getName();
                    UUID uuid = UUID.fromString(name.substring(0, name.length() - 5));
                    GameProfile profile = userCache.getByUuid(uuid).or(() -> {
                        if (PROFILE_CACHE.containsKey(uuid)) {
                            return Optional.ofNullable(PROFILE_CACHE.get(uuid));
                        }
                        return Optional.ofNullable(PROFILE_CACHE.put(uuid, Optional.ofNullable(sessionService.fetchProfile(uuid,
                                        false)).map(ProfileResult::profile).orElse(null)));
                    }).orElse(null);
                    if (profile == null) {
                        TechnicalToolbox.error("Failed to fetch stats while importing stats for objective {}", statName);
                        return;
                    }
                    ScoreAccess score = scoreboard.getOrCreateScore(ScoreHolder.fromProfile(profile), objective, true);
                    int playerScore = new ServerStatHandler(server, file).getStat(stat);
                    if (playerScore != 0) {
                    score.setScore(playerScore);
                    }
                }
            }
            catch (Exception e) {
                TechnicalToolbox.error("Something went wrong while importing stats for objective {}", statName);
            }
        }
    }

}
