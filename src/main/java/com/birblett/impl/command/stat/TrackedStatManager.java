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

package com.birblett.impl.command.stat;

import com.birblett.TechnicalToolbox;
import com.birblett.util.ServerUtil;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stat;
import net.minecraft.text.Text;
import net.minecraft.util.UserCache;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class TrackedStatManager {

    public static HashSet<CompoundStat> TRACKED_COMPOUNDS = new HashSet<>();
    public static HashSet<ScoreboardObjective> TRACKED_STATS = new HashSet<>();
    public static HashMap<ScoreboardCriterion, HashSet<CompoundStat>> CRITERION_LISTENERS = new HashMap<>();
    public static String COMPOUND_STAT_PREFIX = "technical_toolbox.compound_stats.";
    public static String TRACKED_STAT_PREFIX = "technical_toolbox.tracked_stats.";
    public static final String COMPOUND_FILE_NAME = "compound_stats.conf";
    private static final Map<UUID, GameProfile> PROFILE_CACHE = new HashMap<>();

    public static Collection<String> getCompoundNames() {
        ArrayList<String> list = new ArrayList<>();
        for (CompoundStat stat : TRACKED_COMPOUNDS) {
            list.add(stat.objective.getName().replaceFirst(COMPOUND_STAT_PREFIX, ""));
        }
        return list;
    }

    public static CompoundStat getCompoundStat(String name) {
        for (CompoundStat stat : TRACKED_COMPOUNDS) {
            if ((COMPOUND_STAT_PREFIX + name).equals(stat.objective.getName())) {
                return stat;
            }
        }
        return null;
    }

    public static void maybeRemoveScore(Object obj) {
        if (!TRACKED_COMPOUNDS.removeIf(compound -> compound.equals(obj))) {
            if (obj instanceof ScoreboardObjective objective) {
                TRACKED_STATS.remove(objective);
            }
        }
    }

    public static void addTrackedObjective(ScoreboardObjective objective) {
        String name = objective.getName();
        if (name.startsWith(TRACKED_STAT_PREFIX)) {
            TRACKED_STATS.add(objective);
        }
    }

    public static void beginListening(ScoreboardCriterion criterion, CompoundStat listener) {
        CRITERION_LISTENERS.computeIfAbsent(criterion, k -> new HashSet<>()).add(listener);
    }

    public static void stopListening(ScoreboardCriterion criterion, CompoundStat listener) {
        CRITERION_LISTENERS.computeIfAbsent(criterion, k -> new HashSet<>()).remove(listener);
    }

    public static void informListeners(ServerScoreboard scoreboard, ScoreHolder scoreHolder, ScoreboardCriterion criterion, int delta, int score) {
        if (CRITERION_LISTENERS.get(criterion) != null) {
            for (CompoundStat listener : CRITERION_LISTENERS.get(criterion)) {
                listener.updateScore(scoreboard, scoreHolder, delta, score);
            }
        }
    }

    /**
     * Add and return new scoreboard objective.
     * @param name full name of the created objective
     * @param criterion criterion to track
     * @param text displayed text - can be a translatable
     * @return created scoreboard objective corresponding to stat
     */
    public static ScoreboardObjective createNewObjective(MinecraftServer server, String name, ScoreboardCriterion criterion, Text text) {
        ServerScoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.addObjective(name, criterion, text,
                ScoreboardCriterion.RenderType.INTEGER, true, null);
        scoreboard.addScoreboardObjective(objective);
        if (criterion != ScoreboardCriterion.DUMMY) {
            TrackedStatManager.refreshStat(server, objective, null);
        }
        TrackedStatManager.addTrackedObjective(objective);
        return objective;
    }

    /**
     * Stat importer, modified from
     * <a href="https://github.com/qixils/scoreboard-stats-import">qixils' stat importer</a>
     */
    public static void refreshStat(MinecraftServer server, ScoreboardObjective objective, Map<GameProfile, ServerStatHandler> profiles) {
        ScoreboardCriterion criterion = objective.getCriterion();
        Scoreboard scoreboard = server.getScoreboard();
        if (criterion instanceof Stat<?>) {
            if (profiles == null) {
                server.getPlayerManager().saveAllPlayerData();
                profiles = TrackedStatManager.getStatistics(server);
            }
            for (GameProfile profile : profiles.keySet()) {
                ScoreHolder scoreHolder = ScoreHolder.fromProfile(profile);
                ScoreAccess score = scoreboard.getOrCreateScore(scoreHolder, objective, true);
                int playerScore = TrackedStatManager.getCriterionValue(profiles.get(profile), criterion);
                if (playerScore != 0) {
                    score.setScore(playerScore);
                }
                else {
                    scoreboard.removeScore(scoreHolder, objective);
                }
            }
        }
    }

    /**
     * Stat importer, modified to handle compound stats
     * <a href="https://github.com/qixils/scoreboard-stats-import">qixils' stat importer</a>
     */
    public static void refreshCompound(MinecraftServer server, CompoundStat compound, Map<GameProfile, ServerStatHandler> profiles) {
        compound.clearScores();
        Scoreboard scoreboard = server.getScoreboard();
        if (profiles == null) {
            server.getPlayerManager().saveAllPlayerData();
            profiles = TrackedStatManager.getStatistics(server);
        }
        for (GameProfile profile : profiles.keySet()) {
            ScoreHolder scoreHolder = ScoreHolder.fromProfile(profile);
            int totalScore = 0;
            for (ScoreboardCriterion criterion : compound.criteria) {
                totalScore += TrackedStatManager.getCriterionValue(profiles.get(profile), criterion);
            }
            if (totalScore != 0) {
                compound.setScore(scoreboard, scoreHolder, totalScore);
            }
            else {
                compound.removeScore(scoreboard, scoreHolder);
            }
        }
    }

    private static int getCriterionValue(@Nullable ServerStatHandler statHandler, ScoreboardCriterion criterion) {
        if (statHandler != null && criterion instanceof Stat<?> stat) {
            return statHandler.getStat(stat);
        }
        return 0;
    }

    /**
     * Stat importer, modified from
     * <a href="https://github.com/qixils/scoreboard-stats-import">qixils' stat importer</a>
     */
    public static Map<GameProfile, ServerStatHandler> getStatistics(MinecraftServer server) {
        UserCache userCache = server.getUserCache();
        HashMap<GameProfile, ServerStatHandler> profiles = new HashMap<>();
        if (userCache != null) {
            MinecraftSessionService sessionService = server.getSessionService();
            for (File file : FileUtils.listFiles(ServerUtil.getWorldPath(server, "stats").toFile(), new String[]{"json"},
                    false)) {
                String name = file.getName();
                UUID uuid = UUID.fromString(name.substring(0, name.length() - 5));
                userCache.getByUuid(uuid).or(() -> {
                    if (PROFILE_CACHE.containsKey(uuid)) {
                        return Optional.ofNullable(PROFILE_CACHE.get(uuid));
                    }
                    return Optional.ofNullable(PROFILE_CACHE.put(uuid, Optional.ofNullable(sessionService.fetchProfile(uuid,
                            false)).map(ProfileResult::profile).orElse(null)));
                }).ifPresent(profile -> profiles.put(profile, new ServerStatHandler(server, file)));
            }
        }
        return profiles;
    }

    public static void loadTrackedStats(MinecraftServer server) {
        TRACKED_COMPOUNDS.clear();
        TRACKED_STATS.clear();
        ServerUtil.getToolboxPath(server, "").toFile().mkdirs();
        if (ServerUtil.getToolboxPath(server, COMPOUND_FILE_NAME).toFile().isFile()) {
            int i = 0, j = i;
            try (BufferedReader bufferedWriter = Files.newBufferedReader(ServerUtil.getToolboxPath(server, COMPOUND_FILE_NAME))) {
                String line;
                while ((line = bufferedWriter.readLine()) != null) {
                    if (!line.isEmpty()) {
                        try {
                            CompoundStat stat = CompoundStat.deserialize(server, line, i);
                            if (stat != null) {
                                TRACKED_COMPOUNDS.add(stat);
                                TrackedStatManager.refreshCompound(server, stat, null);
                                j++;
                            }
                        }
                        catch (Exception e) {
                            TechnicalToolbox.error("Something went wrong parsing compound stat at line {}", i);
                        }
                    }
                    i++;
                }
            }
            catch (Exception e) {
                TechnicalToolbox.error("Something went wrong reading from {}", COMPOUND_FILE_NAME);
                return;
            }
            if (j > 0) {
                TechnicalToolbox.log("Loaded {} compound stats", j);
            }
        }
        else {
            TechnicalToolbox.log("No compound stats loaded: {} does not exist", COMPOUND_FILE_NAME);
        }
    }

    public static void saveTrackedStats(MinecraftServer server) {
        ServerUtil.getToolboxPath(server, "").toFile().mkdirs();
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(ServerUtil.getToolboxPath(server, COMPOUND_FILE_NAME))) {
            for (CompoundStat stat : TRACKED_COMPOUNDS) {
                bufferedWriter.write(stat.serialize(server));
                bufferedWriter.write("\n");
            }
        }
        catch (Exception e) {
            TechnicalToolbox.error("Something went wrong creating {}", COMPOUND_FILE_NAME);
        }
    }

}