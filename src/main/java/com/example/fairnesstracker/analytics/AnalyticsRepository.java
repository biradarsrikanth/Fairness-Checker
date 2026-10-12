package com.example.fairnesstracker.analytics;

import com.example.fairnesstracker.security.DataScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Descriptive statistics as plain SQL over {@code analytics_alert_v}: counts, averages and distributions
 * PostgreSQL computes faster than anything loading rows could. Every query is limited to a window and to
 * what the caller may see.
 */
@Repository
class AnalyticsRepository {

    /** Alerts triggered in [from, to), UTC; {@code teamId} narrows to one team; {@code scope} is the caller's. */
    record Slice(LocalDateTime from, LocalDateTime to, Long teamId, DataScope scope) {
    }

    record KpiRow(long alerts, Double mttaMinutes, Double mttrMinutes, Double afterHoursPct, Double escalationRatePct) {
    }

    record SeverityBucket(LocalDate bucket, String severity, int count) {
    }

    record HourCount(int dayOfWeek, int hour, int count) {
    }

    record ServiceRow(Long serviceId, String name, int alertCount, Double mttrMinutes, double afterHoursPct) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    AnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    KpiRow kpis(Slice slice) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        return jdbc.queryForObject("""
                SELECT count(*)                                                             AS alerts,
                       avg(ack_minutes)                                                     AS mtta,
                       avg(resolve_minutes)                                                 AS mttr,
                       100.0 * count(*) FILTER (WHERE is_after_hours) / nullif(count(*), 0)  AS after_hours_pct,
                       100.0 * count(*) FILTER (WHERE escalation_count > 0) / nullif(count(*), 0) AS escalation_rate_pct
                FROM analytics_alert_v
                WHERE """ + where(slice, params), params, (rs, i) -> new KpiRow(rs.getLong("alerts"),
                nullable(rs, "mtta"), nullable(rs, "mttr"), nullable(rs, "after_hours_pct"),
                nullable(rs, "escalation_rate_pct")));
    }

    /** Alert counts per severity and period; periods start in {@code zone}. {@code unit}: day, week or month. */
    List<SeverityBucket> countsBySeverity(Slice slice, String unit, String zone) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("unit", unit).addValue("zone", zone);
        return jdbc.query("""
                SELECT date_trunc(:unit, (triggered_at AT TIME ZONE 'UTC') AT TIME ZONE :zone)::date AS bucket,
                       severity, count(*) AS alerts
                FROM analytics_alert_v
                WHERE """ + where(slice, params) + """
                 GROUP BY 1, 2
                 ORDER BY 1""", params,
                (rs, i) -> new SeverityBucket(rs.getObject("bucket", LocalDate.class), rs.getString("severity"),
                        rs.getInt("alerts")));
    }

    List<HourCount> countsByWeekdayAndHour(Slice slice) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        return jdbc.query("""
                SELECT dow_local, hour_local, count(*) AS alerts
                FROM analytics_alert_v
                WHERE """ + where(slice, params) + """
                 GROUP BY 1, 2""", params,
                (rs, i) -> new HourCount(rs.getInt("dow_local"), rs.getInt("hour_local"), rs.getInt("alerts")));
    }

    List<ServiceRow> noisiestServices(Slice slice, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        return jdbc.query("""
                SELECT service_id, coalesce(service_name, 'No service') AS name, count(*) AS alerts,
                       avg(resolve_minutes) AS mttr,
                       100.0 * count(*) FILTER (WHERE is_after_hours) / count(*) AS after_hours_pct
                FROM analytics_alert_v
                WHERE """ + where(slice, params) + """
                 GROUP BY service_id, service_name
                 ORDER BY alerts DESC, name
                 LIMIT :limit""", params,
                (rs, i) -> new ServiceRow(rs.getObject("service_id", Long.class), rs.getString("name"),
                        rs.getInt("alerts"), nullable(rs, "mttr"), rs.getDouble("after_hours_pct")));
    }

    // The window, the chosen team, and the caller's scope; only bound parameters, never concatenated values.
    // Padded with spaces: the text blocks around it lose their trailing whitespace and indentation
    private static String where(Slice slice, MapSqlParameterSource params) {
        params.addValue("from", slice.from()).addValue("to", slice.to());
        StringBuilder where = new StringBuilder("triggered_at >= :from AND triggered_at < :to");
        if (slice.teamId() != null) {
            where.append(" AND team_id = :teamId");
            params.addValue("teamId", slice.teamId());
        }
        DataScope scope = slice.scope();
        if (scope.engineerId() != null) {
            where.append(" AND engineer_id = :engineerId");
            params.addValue("engineerId", scope.engineerId());
        } else if (scope.teamIds() != null) {
            if (scope.teamIds().isEmpty()) {
                where.append(" AND FALSE");
            } else {
                where.append(" AND team_id IN (:teamIds)");
                params.addValue("teamIds", scope.teamIds());
            }
        }
        return " " + where + " ";
    }

    private static Double nullable(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
