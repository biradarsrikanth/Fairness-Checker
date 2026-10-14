package com.example.fairnesstracker.common.web;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The lists' free-text search (the {@code q} parameter): case-insensitive "contains", with the user's
 * {@code %} and {@code _} taken literally. {@code lower(column) LIKE '%term%'} is also the shape the trigram
 * indexes (migration V7) are built for.
 */
public final class Search {

    private static final char ESCAPE = '\\';
    private static final Pattern NUMBER = Pattern.compile("#?(\\d{1,9})");

    private Search() {
    }

    /** The trimmed search text, or empty when there's nothing to search for. */
    public static Optional<String> term(@Nullable String q) {
        return q == null || q.isBlank() ? Optional.empty() : Optional.of(q.strip());
    }

    /** {@code %term%}, lower-cased, with LIKE's wildcards in the term escaped. */
    public static String containsPattern(String term) {
        StringBuilder pattern = new StringBuilder("%");
        for (char c : term.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '%' || c == '_' || c == ESCAPE) {
                pattern.append(ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }

    /** True when any of the columns contains the term, ignoring case. */
    public static Predicate anyContains(CriteriaBuilder cb, String term, Expression<String>... columns) {
        String pattern = containsPattern(term);
        Predicate[] matches = new Predicate[columns.length];
        for (int i = 0; i < columns.length; i++) {
            matches[i] = cb.like(cb.lower(columns[i]), pattern, ESCAPE);
        }
        return cb.or(matches);
    }

    /** A term that is a number, like {@code 4521} or {@code #4521}: an incident number to look for too. */
    public static Optional<Integer> number(String term) {
        var match = NUMBER.matcher(term);
        return match.matches() ? Optional.of(Integer.parseInt(match.group(1))) : Optional.empty();
    }
}
