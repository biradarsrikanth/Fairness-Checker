package com.example.fairnesstracker.report.render;

import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.SeriesPoint;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.TimeSeries;
import com.example.fairnesstracker.scoring.dto.Scores;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.StackedBarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.stereotype.Component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.geom.Ellipse2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * The PDF's charts, drawn with JFreeChart as PNG data URIs. Colors are the web app's (its
 * charts/palette.ts): one blue from dark P1 to light P5 for severity, slot-1 blue for single series. The
 * bundled DejaVu Sans draws the text, so the server needs no fonts of its own.
 */
@Component
public class ChartRenderer {

    /** Drawn at twice the size they're shown, for sharp print. */
    private static final int SCALE = 2;

    static final Color SURFACE = Color.WHITE;
    static final Color INK = new Color(0x52514e);
    static final Color MUTED = new Color(0x898781);
    static final Color GRID = new Color(0xe1e0d9);
    static final Color SERIES_1 = new Color(0x2a78d6);
    static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    static final Color[] SEVERITY = {
            new Color(0x0d366b), new Color(0x1c5cab), new Color(0x2a78d6), new Color(0x5598e7),
            new Color(0x86b6ef), new Color(0xc3c2b7)};
    static final String[] SEVERITY_NAMES = {"P1", "P2", "P3", "P4", "P5", "No severity"};

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.UK);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.UK);

    private final Font font;

    public ChartRenderer() {
        try (InputStream in = getClass().getResourceAsStream("/reports/fonts/DejaVuSans.ttf")) {
            font = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (FontFormatException e) {
            throw new IllegalStateException("The bundled report font is unreadable", e);
        }
    }

    /** A period by its first day: 14 Sep for days and weeks, Sep 2026 for months. */
    static String periodLabel(LocalDate start, String granularity) {
        return ("month".equals(granularity) ? MONTH : DAY).format(start);
    }

    /** Alerts per period, stacked by severity, P1 at the bottom; only the severities that occur. */
    public String alertsOverTime(TimeSeries series, int width, int height) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        boolean[] present = new boolean[SEVERITY.length];
        for (SeriesPoint point : series.points()) {
            int[] counts = counts(point);
            for (int i = 0; i < counts.length; i++) {
                present[i] |= counts[i] > 0;
            }
        }
        for (SeriesPoint point : series.points()) {
            int[] counts = counts(point);
            String label = periodLabel(point.start(), series.granularity());
            for (int i = 0; i < counts.length; i++) {
                if (present[i]) {
                    dataset.addValue(counts[i], SEVERITY_NAMES[i], label);
                }
            }
        }
        CategoryPlot plot = categoryPlot(dataset, PlotOrientation.VERTICAL, "Alerts");
        StackedBarRenderer renderer = new StackedBarRenderer();
        bars(renderer);
        int row = 0;
        for (int i = 0; i < SEVERITY.length; i++) {
            if (present[i]) {
                renderer.setSeriesPaint(row++, SEVERITY[i]);
            }
        }
        plot.setRenderer(renderer);
        integerAxis(plot);
        thinLabels(plot, series.points().size());
        return png(chart(plot, true), width, height);
    }

    /** Gini per period with the label thresholds; low-confidence periods as hollow points. */
    public String giniTrend(List<Scores.TrendPeriod> periods, String granularity, ZoneId zone, int width, int height) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Scores.TrendPeriod period : periods) {
            dataset.addValue(period.gini(), "Gini",
                    periodLabel(period.start().atZoneSameInstant(zone).toLocalDate(), granularity));
        }
        CategoryPlot plot = categoryPlot(dataset, PlotOrientation.VERTICAL, "Gini");
        LineAndShapeRenderer renderer = new LineAndShapeRenderer(true, true) {
            @Override
            public boolean getItemShapeFilled(int row, int column) {
                return !"LOW".equals(periods.get(column).confidence());
            }
        };
        renderer.setSeriesPaint(0, SERIES_1);
        renderer.setSeriesStroke(0, new BasicStroke(2f));
        renderer.setSeriesShape(0, new Ellipse2D.Double(-4, -4, 8, 8));
        renderer.setUseOutlinePaint(false);
        renderer.setDrawOutlines(true);
        renderer.setUseFillPaint(true);
        renderer.setSeriesFillPaint(0, SURFACE);
        plot.setRenderer(renderer);
        NumberAxis axis = (NumberAxis) plot.getRangeAxis();
        double highest = periods.stream().mapToDouble(Scores.TrendPeriod::gini).max().orElse(0);
        axis.setRange(0, Math.min(1, Math.max(0.7, Math.ceil((highest + 0.05) * 10) / 10)));
        axis.setTickUnit(new org.jfree.chart.axis.NumberTickUnit(0.2));
        for (double[] band : new double[][]{{0.2}, {0.4}, {0.6}}) {
            ValueMarker marker = new ValueMarker(band[0], GRID.darker(), new BasicStroke(1f));
            marker.setLabel(band[0] == 0.2 ? "Moderate" : band[0] == 0.4 ? "Unequal" : "Critical");
            marker.setLabelFont(font.deriveFont(9f));
            marker.setLabelPaint(MUTED);
            marker.setLabelAnchor(org.jfree.chart.ui.RectangleAnchor.TOP_RIGHT);
            marker.setLabelTextAnchor(org.jfree.chart.ui.TextAnchor.BOTTOM_RIGHT);
            marker.setLabelBackgroundColor(TRANSPARENT);
            plot.addRangeMarker(marker);
        }
        thinLabels(plot, periods.size());
        return png(chart(plot, false), width, height);
    }

    /** Each engineer's share of the load, largest first, against an even split. */
    public String loadShare(List<Scores.EngineerLoad> engineers, int width) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        engineers.stream()
                .sorted((a, b) -> Double.compare(b.sharePct(), a.sharePct()))
                .forEach(e -> dataset.addValue(e.sharePct(), "Share", e.name() + " "));
        CategoryPlot plot = categoryPlot(dataset, PlotOrientation.HORIZONTAL, "Share of load (%)");
        BarRenderer renderer = new BarRenderer();
        bars(renderer);
        renderer.setSeriesPaint(0, SERIES_1);
        renderer.setMaximumBarWidth(0.6);
        plot.setRenderer(renderer);
        if (engineers.size() > 1) {
            ValueMarker even = new ValueMarker(100.0 / engineers.size(), INK, new BasicStroke(1f));
            even.setLabel("Even share");
            even.setLabelFont(font.deriveFont(9f));
            even.setLabelPaint(INK);
            even.setLabelAnchor(org.jfree.chart.ui.RectangleAnchor.BOTTOM_RIGHT);
            even.setLabelTextAnchor(org.jfree.chart.ui.TextAnchor.BOTTOM_LEFT);
            even.setLabelOffset(new RectangleInsets(2, 4, 2, 4));
            even.setLabelBackgroundColor(SURFACE);
            plot.addRangeMarker(even);
        }
        return png(chart(plot, false), width, Math.max(140, engineers.size() * 24 + 60));
    }

    /** One engineer's alerts by part of the day. */
    public String timeOfDay(Scores.TimeOfDayCounts counts, int width, int height) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(counts.morning(), "Alerts", "Morning");
        dataset.addValue(counts.afternoon(), "Alerts", "Afternoon");
        dataset.addValue(counts.evening(), "Alerts", "Evening");
        dataset.addValue(counts.night(), "Alerts", "Night");
        CategoryPlot plot = categoryPlot(dataset, PlotOrientation.VERTICAL, "Alerts");
        BarRenderer renderer = new BarRenderer();
        bars(renderer);
        renderer.setSeriesPaint(0, SERIES_1);
        renderer.setMaximumBarWidth(0.15);
        plot.setRenderer(renderer);
        integerAxis(plot);
        return png(chart(plot, false), width, height);
    }

    // --- Styling --------------------------------------------------------------------------------------

    private static int[] counts(SeriesPoint point) {
        var s = point.bySeverity();
        return new int[]{s.p1(), s.p2(), s.p3(), s.p4(), s.p5(), s.other()};
    }

    private CategoryPlot categoryPlot(DefaultCategoryDataset dataset, PlotOrientation orientation, String valueLabel) {
        CategoryAxis categories = new CategoryAxis();
        NumberAxis values = new NumberAxis(valueLabel);
        CategoryPlot plot = new CategoryPlot(dataset, categories, values, null);
        plot.setOrientation(orientation);
        plot.setBackgroundPaint(SURFACE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(GRID);
        plot.setRangeGridlineStroke(new BasicStroke(1f));
        plot.setDomainGridlinesVisible(false);
        plot.setInsets(new RectangleInsets(4, 4, 4, 8));
        for (var axis : List.of(categories, values)) {
            axis.setTickLabelFont(font.deriveFont(9f));
            axis.setTickLabelPaint(MUTED);
            axis.setLabelFont(font.deriveFont(9f));
            axis.setLabelPaint(MUTED);
            axis.setAxisLinePaint(GRID);
            axis.setTickMarksVisible(false);
        }
        values.setAxisLineVisible(false);
        categories.setCategoryMargin(0.25);
        return plot;
    }

    private static void bars(BarRenderer renderer) {
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(true);
        // A surface-colored outline leaves a gap between stacked segments
        renderer.setDefaultOutlinePaint(SURFACE);
        renderer.setDefaultOutlineStroke(new BasicStroke(1f));
        renderer.setItemMargin(0);
    }

    private static void integerAxis(CategoryPlot plot) {
        ((NumberAxis) plot.getRangeAxis()).setStandardTickUnits(NumberAxis.createIntegerTickUnits());
    }

    // Long periods: slant the labels rather than overlap them
    private static void thinLabels(CategoryPlot plot, int count) {
        if (count > 14) {
            plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        }
    }

    private JFreeChart chart(CategoryPlot plot, boolean legend) {
        JFreeChart chart = new JFreeChart(null, font.deriveFont(11f), plot, legend);
        chart.setBackgroundPaint(SURFACE);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        if (legend) {
            LegendTitle title = chart.getLegend();
            title.setItemFont(font.deriveFont(9f));
            title.setItemPaint(INK);
            title.setFrame(org.jfree.chart.block.BlockBorder.NONE);
            title.setPosition(RectangleEdge.TOP);
            title.setBackgroundPaint(SURFACE);
        }
        return chart;
    }

    private static String png(JFreeChart chart, int width, int height) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ChartUtils.writeScaledChartAsPNG(out, chart, width, height, SCALE, SCALE);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
