package com.example.fairnesstracker.report.render;

import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Kpi;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.ScoresStatus;
import com.example.fairnesstracker.scoring.dto.Scores;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** How reports word figures, the same way the web app does. */
public final class ReportText {

    private static final Locale LOCALE = Locale.UK;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", LOCALE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", LOCALE);

    /** How a KPI's change is worded: relative for counts and durations, in points for percentages and Gini. */
    public enum Kind { COUNT, MINUTES, PCT, GINI }

    private ReportText() {
    }

    public static String day(LocalDate day) {
        return DAY.format(day);
    }

    public static String range(LocalDate start, LocalDate end) {
        return start.equals(end) ? day(start) : day(start) + " – " + day(end);
    }

    public static String time(@Nullable Instant instant, ZoneId zone) {
        return instant == null ? "–" : TIME.format(instant.atZone(zone));
    }

    /** 45 s, 12 min, 1 h 5 min, 2 d 3 h */
    public static String minutes(@Nullable Double minutes) {
        if (minutes == null) {
            return "–";
        }
        if (minutes < 1) {
            return Math.round(minutes * 60) + " s";
        }
        long total = Math.round(minutes);
        if (total < 60) {
            return total + " min";
        }
        if (total < 24 * 60) {
            long rest = total % 60;
            return total / 60 + " h" + (rest == 0 ? "" : " " + rest + " min");
        }
        long hours = Math.round(minutes / 60);
        long rest = hours % 24;
        return hours / 24 + " d" + (rest == 0 ? "" : " " + rest + " h");
    }

    /** 35%, 2.5% */
    public static String pct(@Nullable Double value) {
        if (value == null) {
            return "–";
        }
        return Math.abs(value) < 10 ? trim(Math.round(value * 10) / 10.0) + "%" : Math.round(value) + "%";
    }

    public static String count(@Nullable Double value) {
        return value == null ? "–" : String.format(LOCALE, "%,d", Math.round(value));
    }

    public static String gini(@Nullable Double value) {
        return value == null ? "–" : String.format(LOCALE, "%.2f", value);
    }

    public static String value(Kind kind, @Nullable Double value) {
        return switch (kind) {
            case COUNT -> count(value);
            case MINUTES -> minutes(value);
            case PCT -> pct(value);
            case GINI -> gini(value);
        };
    }

    /** +12%, −3 pts, +0.04, No change; null without a previous figure. Lower is better for every KPI. */
    public static @Nullable String change(Kind kind, Kpi kpi) {
        Double value = kpi.value();
        Double previous = kpi.previous();
        if (value == null || previous == null) {
            return null;
        }
        double delta;
        String text;
        switch (kind) {
            case PCT -> {
                delta = Math.round((value - previous) * 10) / 10.0;
                text = trim(Math.abs(delta)) + " pts";
            }
            case GINI -> {
                delta = Math.round((value - previous) * 100) / 100.0;
                text = String.format(LOCALE, "%.2f", Math.abs(delta));
            }
            default -> {
                if (kind == Kind.COUNT && previous == 0) {
                    delta = value;
                    text = count(Math.abs(delta));
                } else if (kpi.changePct() == null) {
                    return null;
                } else {
                    delta = Math.round(kpi.changePct());
                    text = Math.round(Math.abs(delta)) + "%";
                }
            }
        }
        if (delta == 0) {
            return "No change";
        }
        return (delta > 0 ? "+" : "−") + text;
    }

    /** Whether a change is for the worse: every KPI is better lower. */
    public static boolean worse(@Nullable String change) {
        return change != null && change.startsWith("+");
    }

    /** Why the scorer's figures are missing. */
    public static String scoresMissing(ScoresStatus scores) {
        return "SEVERAL_TEAMS".equals(scores.reason())
                ? "Fairness and burnout compare engineers within one team; download the report for one team to see them."
                : "The scoring service didn't answer, so fairness and burnout are missing. Counts are complete.";
    }

    /** FAIR → Fair, ONCALL_ROTATION → Oncall rotation */
    public static String humanize(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "–";
        }
        String words = value.toLowerCase(LOCALE).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** Engineers with the most alerts first. */
    public static List<Scores.EngineerTimeOfDay> busiestFirst(List<Scores.EngineerTimeOfDay> engineers) {
        return engineers.stream().sorted(Comparator.comparingInt(ReportText::total).reversed()).toList();
    }

    public static int total(Scores.EngineerTimeOfDay row) {
        return row.morning() + row.afternoon() + row.evening() + row.night();
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
