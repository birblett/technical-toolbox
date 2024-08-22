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

    public static HashSet<AggregateStatWrapper> TRACKED_AGGREGATORS = new HashSet<>();
    public static HashSet<ScoreboardObjective> TRACKED_MODIFIED_STATS = new HashSet<>();
    public static HashSet<ScoreboardObjective> TRACKED_STATS = new HashSet<>();
    public static HashMap<ScoreboardCriterion, HashSet<ScoreboardObjective>> CRITERION_LISTENERS = new HashMap<>();
    public static String TRACKED_AGGREGATE_PREFIX = "technical_toolbox.tracked_aggregators.";
    public static String TRACKED_MODIFIED_PREFIX = "technical_toolbox.modified_stats.";
    public static String TRACKED_STAT_PREFIX = "technical_toolbox.tracked_stats.";
    private static final Map<UUID, GameProfile> PROFILE_CACHE = new HashMap<>();

    public static Collection<String> getAggregateNames() {
        ArrayList<String> list = new ArrayList<>();
        for (AggregateStatWrapper stat : TRACKED_AGGREGATORS) {
            list.add(stat.objective.getName().replaceFirst(TRACKED_AGGREGATE_PREFIX, ""));
        }
        return list;
    }

    public static AggregateStatWrapper getAggregate(String name) {
        for (AggregateStatWrapper stat : TRACKED_AGGREGATORS) {
            if ((TRACKED_AGGREGATE_PREFIX + name).equals(stat.objective.getName())) {
                return stat;
            }
        }
        return null;
    }

    public interface StatListener {

    }

    public static class AggregateStatWrapper {

        public final ScoreboardObjective objective;
        public final HashSet<ScoreboardCriterion> criteria;

        public AggregateStatWrapper( ScoreboardObjective objective, HashSet<ScoreboardCriterion> criteria) {
            this.objective = objective;
            this.criteria = criteria;
        }

        public static AggregateStatWrapper create(ScoreboardObjective objective) {
            return new AggregateStatWrapper(objective, new HashSet<>());
        }

        public void addCriteria(ScoreboardCriterion criterion) {
            this.criteria.add(criterion);
            TrackedStatManager.beginListening(criterion, this.objective);
        }

        public void removeCriteria(ScoreboardCriterion criterion) {
            this.criteria.remove(criterion);
            TrackedStatManager.stopListening(criterion, this.objective);
        }

        public void deregisterAll() {
            for (ScoreboardCriterion criterion : this.criteria) {
                if (CRITERION_LISTENERS.containsKey(criterion)) {
                    TrackedStatManager.stopListening(criterion, this.objective);
                }
            }
        }

        public void registerAll() {
            for (ScoreboardCriterion criterion : this.criteria) {
                if (CRITERION_LISTENERS.containsKey(criterion)) {
                    TrackedStatManager.beginListening(criterion, this.objective);
                }
            }
        }

    }

    public static void addTrackedObjective(ScoreboardObjective objective) {
        String name = objective.getName();
        if (name.startsWith(TRACKED_AGGREGATE_PREFIX)) {
            TRACKED_AGGREGATORS.add(AggregateStatWrapper.create(objective));
        }
        else if (name.startsWith(TRACKED_MODIFIED_PREFIX)) {
            TRACKED_MODIFIED_STATS.add(objective);
        }
        else if (name.startsWith(TRACKED_STAT_PREFIX)) {
            TRACKED_STATS.add(objective);
        }
    }

    public static void beginListening(ScoreboardCriterion criterion, ScoreboardObjective listener) {
        CRITERION_LISTENERS.computeIfAbsent(criterion, k -> new HashSet<>()).add(listener);
    }

    public static void stopListening(ScoreboardCriterion criterion, ScoreboardObjective listener) {
        CRITERION_LISTENERS.computeIfAbsent(criterion, k -> new HashSet<>()).remove(listener);
    }

    public static void informListeners(ServerScoreboard scoreboard, ScoreHolder scoreHolder, ScoreboardCriterion criterion, int delta) {
        if (CRITERION_LISTENERS.get(criterion) != null) {
            for (ScoreboardObjective listener : CRITERION_LISTENERS.get(criterion)) {
                ScoreAccess access = scoreboard.getOrCreateScore(scoreHolder, listener, true);
                access.incrementScore(delta);
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
            TrackedStatManager.importStat(server, objective);
        }
        TrackedStatManager.addTrackedObjective(objective);
        return objective;
    }

    /**
     * Stat importer, modified from
     * <a href="https://github.com/qixils/scoreboard-stats-import">qixils' stat importer</a>
     */
    private static void importStat(MinecraftServer server, ScoreboardObjective objective) {
        ScoreboardCriterion criterion = objective.getCriterion();
        Scoreboard scoreboard = server.getScoreboard();
        if (criterion instanceof Stat<?>) {
            Map<GameProfile, ServerStatHandler> profiles = TrackedStatManager.getStatistics(server);
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
     * Stat importer, modified to handle aggregates
     * <a href="https://github.com/qixils/scoreboard-stats-import">qixils' stat importer</a>
     */
    public static void refreshAggregate(MinecraftServer server, AggregateStatWrapper aggregateStat) {
        ScoreboardObjective objective = aggregateStat.objective;
        Scoreboard scoreboard = server.getScoreboard();
        Map<GameProfile, ServerStatHandler> profiles = TrackedStatManager.getStatistics(server);
        for (GameProfile profile : profiles.keySet()) {
            ScoreHolder scoreHolder = ScoreHolder.fromProfile(profile);
            ScoreAccess score = scoreboard.getOrCreateScore(scoreHolder, objective, true);
            int totalScore = 0;
            for (ScoreboardCriterion criterion : aggregateStat.criteria) {
                totalScore += TrackedStatManager.getCriterionValue(profiles.get(profile), criterion);
            }
            if (totalScore != 0) {
                score.setScore(totalScore);
            }
            else {
                scoreboard.removeScore(scoreHolder, objective);
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
    private static Map<GameProfile, ServerStatHandler> getStatistics(MinecraftServer server) {
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
        TRACKED_AGGREGATORS.clear();
        TRACKED_MODIFIED_STATS.clear();
        TRACKED_STATS.clear();
        ServerUtil.getToolboxPath(server, "").toFile().mkdirs();
        if (ServerUtil.getToolboxPath(server, "aggregates.conf").toFile().isFile()) {
            int i = 0;
            try (BufferedReader bufferedWriter = Files.newBufferedReader(ServerUtil.getToolboxPath(server, "aggregates.conf"))) {
                String line;
                while ((line = bufferedWriter.readLine()) != null) {
                    if (!line.isEmpty()) {
                        String[] entry = line.split(": ", 2);
                        if (entry.length < 1) {
                            TechnicalToolbox.log("Invalid line \"{}\": expects format \"objective_name display_json: stats...\"");
                            continue;
                        }
                        String[] objectiveStrings = entry[0].split(" ", 2);
                        if (objectiveStrings.length != 2) {
                            TechnicalToolbox.log("Invalid line \"{}\": expects format \"objective_name display_json: stats...\"");
                            continue;
                        }
                        String name = TRACKED_AGGREGATE_PREFIX + objectiveStrings[0];
                        ScoreboardObjective objective = server.getScoreboard().getNullableObjective(name);
                        if (objective == null) {
                            objective = TrackedStatManager.createNewObjective(server, name, ScoreboardCriterion.DUMMY,
                                    Text.Serialization.fromJson(objectiveStrings[1], server.getRegistryManager()));
                        }
                        AggregateStatWrapper stat = AggregateStatWrapper.create(objective);
                        TRACKED_AGGREGATORS.add(stat);
                        if (entry.length == 2) {
                            for (String st : entry[1].split(" ")) {
                                if (!st.isEmpty()) {
                                    ScoreboardCriterion.getOrCreateStatCriterion(st).ifPresentOrElse(stat::addCriteria, () ->
                                            TechnicalToolbox.warn("No such criterion \"{}\"", st));
                                }
                            }
                            TrackedStatManager.refreshAggregate(server, stat);
                        }
                        i++;
                    }
                }
            }
            catch (Exception e) {
                TechnicalToolbox.error("Something went wrong reading from aggregates.conf");
                return;
            }
            if (i > 0) {
                TechnicalToolbox.log("Loaded {} aggregate stats", i);
            }
        }
        else {
            TechnicalToolbox.log("No aggregate stats loaded: aggregates.conf does not exist");
        }
    }

    public static void saveTrackedStats(MinecraftServer server) {
        ServerUtil.getToolboxPath(server, "").toFile().mkdirs();
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(ServerUtil.getToolboxPath(server, "aggregates.conf"))) {
            for (AggregateStatWrapper stat : TRACKED_AGGREGATORS) {
                TechnicalToolbox.log("{}", stat.objective.getName());
                bufferedWriter.write(stat.objective.getName().replaceFirst(TRACKED_AGGREGATE_PREFIX, "") + " " +
                        Text.Serialization.toJsonString(stat.objective.getDisplayName(), server.getRegistryManager()) +  ": ");
                for (ScoreboardCriterion criterion : stat.criteria) {
                    bufferedWriter.write(criterion.getName() + " ");
                }
                bufferedWriter.write("\n");
            }
        }
        catch (Exception e) {
            TechnicalToolbox.error("Something went wrong creating aggregates.conf");
        }
    }

}
