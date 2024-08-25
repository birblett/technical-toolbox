package com.birblett.impl.command.stat;

import com.birblett.TechnicalToolbox;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compound stat class; can aggregate values from multiple different criteria and apply a multiplier or divisor to the value
 * before updating the scoreboard.
 */
public class CompoundStat {

    private static final Pattern SERIALIZE_HEADER = Pattern.compile("(?<!\\\\)\".*(?<!\\\\)\"|(?<!\\\\)\\{.*(?<!\\\\)}|[^ :]+|:");
    public final ScoreboardObjective objective;
    public final HashSet<ScoreboardCriterion> criteria;
    public boolean isGlobal;
    private final HashMap<String, Long> scoresMap = new HashMap<>();
    private double modifier = 1;
    private boolean mode = true;

    public CompoundStat(ScoreboardObjective objective, HashSet<ScoreboardCriterion> criteria) {
        this.objective = objective;
        this.criteria = criteria;
        this.isGlobal = false;
    }

    public void clearScores() {
        this.scoresMap.clear();
    }

    /**
     * Starts tracking a criterion.
     */
    public void addCriteria(ScoreboardCriterion criterion) {
        this.criteria.add(criterion);
        TrackedStatManager.beginListening(criterion, this);
    }

    /**
     * Removes a tracked criterion.
     */
    public void removeCriteria(ScoreboardCriterion criterion) {
        this.criteria.remove(criterion);
        TrackedStatManager.stopListening(criterion, this);
    }

    /**
     * Sets the numeric value of the modifier to apply.
     */
    public void setModifier(double modifier) {
        this.modifier = modifier;
    }

    /**
     * Sets execution mode to multiply or divide
     * @param mode boolean value corresponds to multiply if true, divide otherwise
     */
    public void setMode(boolean mode) {
        this.mode = mode;
    }

    /**
     * Sets a score to the provided value.
     * @param scoreboard scoreboard to update
     * @param scoreHolder scoreholder to update
     * @param score value to set score to
     */
    public void setScore(Scoreboard scoreboard, ScoreHolder scoreHolder, int score) {
        String name = scoreHolder.getNameForScoreboard();
        this.scoresMap.put(name, (long) score);
        ScoreAccess access = scoreboard.getOrCreateScore(scoreHolder, this.objective);
        access.setScore((int) (this.mode ? this.scoresMap.get(name) * modifier : this.scoresMap.get(name) / modifier));
    }

    /**
     * Updates a scoreholder's entry for this compound stat with a delta
     * @param scoreboard scoreboard to update
     * @param scoreHolder scoreholder to update
     * @param delta change to previous score
     * @param score value to set score to, if applicalbe
     */
    public void updateScore(Scoreboard scoreboard, ScoreHolder scoreHolder, int delta, int score) {
        String name = scoreHolder.getNameForScoreboard();
        this.scoresMap.put(name, this.scoresMap.getOrDefault(name, (long) score) + delta);
        ScoreAccess access = scoreboard.getOrCreateScore(scoreHolder, this.objective, true);
        int newVal = (int) (this.mode ? this.scoresMap.get(name) * modifier : this.scoresMap.get(name) / modifier);
        if (newVal != access.getScore()) {
            access.setScore(newVal);
        }
    }

    /**
     * Removes a scoreholder's entry for this compound stat.
     */
    public void removeScore(Scoreboard scoreboard, ScoreHolder scoreHolder) {
        this.scoresMap.remove(scoreHolder.getNameForScoreboard());
        scoreboard.removeScore(scoreHolder, objective);
        scoreboard.onScoreHolderRemoved(scoreHolder);
    }

    /**
     * Allows comparison to scoreboard objectives and strings.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof com.birblett.impl.command.stat.CompoundStat) {
            return this == obj;
        } else if (obj instanceof ScoreboardObjective scoreboardObjective) {
            return this.objective == scoreboardObjective;
        } else if (obj instanceof String str) {
            return this.objective.getName().equals(str);
        }
        return false;
    }

    /**
     * Returns a string representation of the compound stat as [name] [displayName] [modifier]: [tracked criteria]...
     */
    public String serialize(MinecraftServer server) {
        StringBuilder s = new StringBuilder(this.objective.getName().replaceFirst(TrackedStatManager.COMPOUND_STAT_REGEX, ""));
        s.append(" ").append(Text.Serialization.toJsonString(this.objective.getDisplayName(), server.getRegistryManager())).append(" ")
                .append(this.mode ? "*" : "/").append(this.modifier).append(": ");
        for (ScoreboardCriterion criterion : this.criteria) {
            s.append(criterion.getName()).append(" ");
        }
        return s.toString();
    }

    /**
     * Returns a compound stat from its string representation, if possible
     * @param string a valid string representation of a compound stat
     * @param line the line number, used for errors
     * @return a compound stat if deserialized correctly, otherwise null
     */
    public static CompoundStat deserialize(MinecraftServer server, String string, int line) {
        string = string.strip();
        String name = null;
        Text text = null;
        double modifier = 1;
        boolean mode = true;
        if (!string.isEmpty()) {
            Matcher m = SERIALIZE_HEADER.matcher(string);
            int i = 0, index = 0;
            while (m.find() && i <= 3) {
                index = m.start();
                String found = m.group();
                if (Objects.equals(found, ":")) {
                    break;
                }
                switch (i) {
                    case 0 -> {
                        if (!found.matches("[a-zA-Z0-9.\\-_+]+")) {
                            TechnicalToolbox.error("Error at line {}: name must match the character set [a-zA-Z0-9.-_+]", line);
                            return null;
                        }
                        name = found;
                        if (TrackedStatManager.getCompoundStat(name) != null) {
                            TechnicalToolbox.error("Compound stat for {} already exists", name);
                            return null;
                        }
                    }
                    case 1 -> {
                        text = Text.Serialization.fromJson(found, server.getRegistryManager());
                        if (text == null) {
                            TechnicalToolbox.error("Couldn't parse json \"{}\" at line {}", found, i);
                            return null;
                        }
                    }
                    case 2 -> {
                        if (!found.matches("[/*][0-9]+(\\.[0-9]+)?")) {
                            TechnicalToolbox.error("Error at line {}: third argument expected to be modifier [* or /][num]", line);
                            return null;
                        }
                        if (found.charAt(0) == '/') {
                            mode = false;
                        }
                        try {
                            modifier = Double.parseDouble(found.substring(1));
                        }
                        catch (Exception e) {
                            TechnicalToolbox.error("Something went wrong with parsing \"{}\" at line {}", found, line);
                            return null;
                        }
                    }
                    case 3 -> {
                        if (!found.matches(":")) {
                            TechnicalToolbox.error("Expected 3 max arguments at line {}", line);
                            return null;
                        }
                    }
                }
                i++;
            }
            if (name == null) {
                TechnicalToolbox.error("No name provided for compound stat at line {}", line);
                return null;
            }
            if (text == null) {
                TechnicalToolbox.error("No display text provided for compound stat at line {}", line);
                return null;
            }
            String objectiveName = TrackedStatManager.COMPOUND_STAT_PREFIX + name;
            ScoreboardObjective objective = server.getScoreboard().getNullableObjective(objectiveName);
            if (objective == null) {
                objective = TrackedStatManager.createNewObjective(server, objectiveName, ScoreboardCriterion.DUMMY, text);
            }
            CompoundStat stat = new CompoundStat(objective, new HashSet<>());
            stat.setModifier(modifier);
            stat.setMode(mode);
            for (String s : string.substring(index + 1).strip().split(" ")) {
                if (!s.isEmpty()) {
                    ScoreboardCriterion.getOrCreateStatCriterion(s).ifPresentOrElse(stat::addCriteria, () ->
                            TechnicalToolbox.warn("No such criterion \"{}\"", s));
                }
            }
            return stat;
        }
        return null;
    }

}
