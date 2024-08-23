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

public class CompoundStat {

    private static final Pattern SERIALIZE_HEADER = Pattern.compile("(?<!\\\\)\".*(?<!\\\\)\"|(?<!\\\\)\\{.*(?<!\\\\)}|[^ :]+|:");
    public final ScoreboardObjective objective;
    public final HashSet<ScoreboardCriterion> criteria;
    private final HashMap<ScoreHolder, Long> scoresMap = new HashMap<>();
    private double modifier = 1;
    private boolean mode = true;

    public CompoundStat(ScoreboardObjective objective, HashSet<ScoreboardCriterion> criteria) {
        this.objective = objective;
        this.criteria = criteria;
    }

    public void clearScores() {
        this.scoresMap.clear();
    }

    public void addCriteria(ScoreboardCriterion criterion) {
        this.criteria.add(criterion);
        TrackedStatManager.beginListening(criterion, this);
    }

    public void removeCriteria(ScoreboardCriterion criterion) {
        this.criteria.remove(criterion);
        TrackedStatManager.stopListening(criterion, this);
    }

    public void setModifier(double modifier) {
        this.modifier = modifier;
    }

    public void setMode(boolean mode) {
        this.mode = mode;
    }

    public void setScore(Scoreboard scoreboard, ScoreHolder scoreHolder, int score) {
        this.scoresMap.put(scoreHolder, (long) score);
        ScoreAccess access = scoreboard.getOrCreateScore(scoreHolder, this.objective, true);
        access.setScore((int) (this.mode ? this.scoresMap.get(scoreHolder) * modifier : this.scoresMap.get(scoreHolder) / modifier));
    }

    public void updateScore(Scoreboard scoreboard, ScoreHolder scoreHolder, int delta, int score) {
        this.scoresMap.put(scoreHolder, this.scoresMap.getOrDefault(scoreHolder, (long) score) + delta);
        ScoreAccess access = scoreboard.getOrCreateScore(scoreHolder, this.objective, true);
        int newVal = (int) (this.mode ? this.scoresMap.get(scoreHolder) * modifier : this.scoresMap.get(scoreHolder) / modifier);
        if (newVal != access.getScore()) {
            access.setScore(newVal);
        }
    }

    public void removeScore(Scoreboard scoreboard, ScoreHolder scoreHolder) {
        this.scoresMap.remove(scoreHolder);
        scoreboard.removeScore(scoreHolder, objective);
        scoreboard.onScoreHolderRemoved(scoreHolder);
    }

    public boolean equals(Object obj) {
        if (obj instanceof com.birblett.impl.command.stat.CompoundStat) {
            return this == obj;
        } else if (obj instanceof ScoreboardObjective) {
            return this.objective == obj;
        } else if (obj instanceof String) {
            return this.objective.getName().equals(obj);
        }
        return false;
    }

    public String serialize(MinecraftServer server) {
        StringBuilder s = new StringBuilder(this.objective.getName().replaceFirst(TrackedStatManager.COMPOUND_STAT_PREFIX, ""));
        s.append(" ").append(Text.Serialization.toJsonString(this.objective.getDisplayName(), server.getRegistryManager())).append(" ")
                .append(this.mode ? "*" : "/").append(this.modifier).append(": ");
        for (ScoreboardCriterion criterion : this.criteria) {
            s.append(criterion.getName()).append(" ");
        }
        return s.toString();
    }

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
                            TechnicalToolbox.error("Error at line {}: name must match the character set [a-zA-Z0-9.-_+]", i);
                            return null;
                        }
                        name = found;
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
                            TechnicalToolbox.error("Error at line {}: third argument expected to be modifier [* or /][num]", i);
                            return null;
                        }
                        if (found.charAt(0) == '/') {
                            mode = false;
                        }
                        try {
                            modifier = Double.parseDouble(found.substring(1));
                        }
                        catch (Exception e) {
                            TechnicalToolbox.error("Something went wrong with parsing \"{}\" at line {}", found, i);
                            return null;
                        }
                    }
                    case 3 -> {
                        if (!found.matches(":")) {
                            TechnicalToolbox.error("Expected 3 max arguments at line {}", i);
                            return null;
                        }
                    }
                }
                i++;
            }
            if (name == null) {
                TechnicalToolbox.error("No name provided for compound stat at line {}", i);
                return null;
            }
            if (text == null) {
                TechnicalToolbox.error("No display text provided for compound stat at line {}", i);
                return null;
            }
            name = TrackedStatManager.COMPOUND_STAT_PREFIX + name;
            ScoreboardObjective objective = server.getScoreboard().getNullableObjective(name);
            if (objective == null) {
                objective = TrackedStatManager.createNewObjective(server, name, ScoreboardCriterion.DUMMY, text);
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
