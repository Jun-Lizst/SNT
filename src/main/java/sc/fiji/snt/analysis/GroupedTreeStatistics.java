/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2019 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package sc.fiji.snt.analysis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.Outlier;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.chart.renderer.category.CategoryItemRendererState;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.statistics.BoxAndWhiskerCategoryDataset;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;
import org.scijava.util.ColorRGB;

import net.imagej.ImageJ;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.TreeStatistics.HistogramDatasetPlus;
import sc.fiji.snt.util.SNTColor;

/**
 * Computes statistics from {@link Tree} groups.
 *
 * @see TreeStatistics
 * @see MultiTreeStatistics
 * 
 * @author Tiago Ferreira
 */
public class GroupedTreeStatistics {

	private final LinkedHashMap<String, MultiTreeStatistics> groups;

	/**
	 * Instantiates a new grouped tree statistics.
	 */
	public GroupedTreeStatistics() {
		groups = new LinkedHashMap<>();
	}

	/**
	 * Adds a comparison group to the analysis queue.
	 *
	 * @param group      the group
	 * @param groupLabel a unique label identifying the group
	 */
	public void addGroup(final Collection<Tree> group, final String groupLabel) {
		final MultiTreeStatistics mStats = new MultiTreeStatistics(group);
		mStats.setLabel(groupLabel);
		groups.put(groupLabel, mStats);
	}

	/**
	 * Gets the group statistics.
	 *
	 * @param groupLabel the unique label identifying the group
	 * @return the group statistics or null if no group is mapped to
	 *         {@code groupLabel}
	 */
	public MultiTreeStatistics getGroupStats(final String groupLabel) {
		return groups.get(groupLabel);
	}

	/**
	 * Gets the group identifiers currently queued for analysis.
	 *
	 * @return the group identifiers
	 */
	public Collection<String> getGroups() {
		return groups.keySet();
	}

	/**
	 * Gets the relative frequencies histogram for a univariate measurement. The
	 * number of bins is determined using the Freedman-Diaconis rule.
	 *
	 * @param measurement the measurement ({@link MultiTreeStatistics#N_NODES
	 *                    N_NODES}, {@link MultiTreeStatistics#NODE_RADIUS
	 *                    NODE_RADIUS}, etc.)
	 * @return the frame holding the histogram
	 * @see MultiTreeStatistics#getMetrics()
	 * @see TreeStatistics#getMetrics()
	 */
	public ChartFrame getHistogram(final String measurement) {
		final String normMeasurement = MultiTreeStatistics.getNormalizedMeasurement(measurement, true);
		final int[] nBins = new int[] { 0 };
		// Retrieve all HistogramDatasetPlus instances
		final LinkedHashMap<String, HistogramDatasetPlus> hdpMap = new LinkedHashMap<>();
		for (final Entry<String, MultiTreeStatistics> entry : groups.entrySet()) {
			final HistogramDatasetPlus hdp = entry.getValue().new HistogramDatasetPlus(normMeasurement);
			hdp.compute();
			nBins[0] += hdp.nBins;
			hdpMap.put(entry.getKey(), hdp);
		}
		nBins[0] = nBins[0] / groups.size();

		// Add all series
		final HistogramDataset dataset = new HistogramDataset();
		dataset.setType(HistogramType.RELATIVE_FREQUENCY);
		hdpMap.forEach((label, hdp) -> {
			dataset.addSeries(label, hdp.values, nBins[0]);
		});

		final JFreeChart chart = ChartFactory.createHistogram(null, normMeasurement, "Rel. Frequency", dataset);

		// Customize plot
		final Color bColor = null; // Color.WHITE; make graph transparent so that it can be exported without
									// background
		final XYPlot plot = chart.getXYPlot();
		plot.setBackgroundPaint(bColor);
		plot.setDomainGridlinesVisible(false);
		plot.setRangeGridlinesVisible(false);
		final XYBarRenderer bar_renderer = (XYBarRenderer) plot.getRenderer();
		bar_renderer.setBarPainter(new StandardXYBarPainter());
		bar_renderer.setDrawBarOutline(true);
		bar_renderer.setSeriesOutlinePaint(0, Color.DARK_GRAY);

		final ColorRGB[] colors = SNTColor.getDistinctColors(hdpMap.size());
		for (int i = 0; i < hdpMap.size(); i++) {
			final Color awtColor = new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), 128);
			bar_renderer.setSeriesPaint(i, awtColor);
		}
		bar_renderer.setShadowVisible(false);
		return getFrame(chart, "Grouped Hist.", new Dimension(400, 400));
	}

	/**
	 * Assembles a Box and Whisker Plot for the specified measurement.
	 *
	 * @param measurement the measurement ({@link MultiTreeStatistics#N_NODES
	 *                    N_NODES}, {@link MultiTreeStatistics#NODE_RADIUS
	 *                    NODE_RADIUS}, etc.)
	 * @return the frame holding the box plot
	 * @see MultiTreeStatistics#getMetrics()
	 * @see TreeStatistics#getMetrics()
	 */
	public ChartFrame getBoxPlot(final String measurement) {

		final String normMeasurement = MultiTreeStatistics.getNormalizedMeasurement(measurement, true);
		final DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
		groups.forEach((label, mstats) -> {
			final HistogramDatasetPlus hdp = mstats.new HistogramDatasetPlus(normMeasurement);
			dataset.add(createValueList(hdp.values), normMeasurement, label);
		});
		final JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(null, null, normMeasurement, dataset, false);
		final CategoryPlot plot = (CategoryPlot) chart.getPlot();
		plot.setBackgroundPaint(null);
		plot.setRangePannable(true);
		plot.setDomainGridlinesVisible(false);
		plot.setRangeGridlinesVisible(false);
		plot.setOutlineVisible(false);
		final CustomBoxAndWhiskerRenderer renderer = new CustomBoxAndWhiskerRenderer();
		plot.setRenderer(renderer);
		renderer.setPointSize((double) plot.getRangeAxis().getTickLabelFont().getSize2D() / 2);
		renderer.setDrawOutliers(true);
		renderer.setItemMargin(0);
		renderer.setDefaultPaint(Color.BLACK);
		renderer.setSeriesOutlinePaint(0, Color.BLACK);
		renderer.setSeriesItemLabelPaint(0, Color.BLACK);
		renderer.setSeriesPaint(0, Color.GRAY);
		renderer.setUseOutlinePaintForWhiskers(true);
		renderer.setMaximumBarWidth(0.10);
		renderer.setMedianVisible(true);
		renderer.setMeanVisible(true);
		renderer.setFillBox(false);

		final int height = 400;
		final double width = (groups.size() < 4) ? height / 1.5 : height * 1.5;
		return getFrame(chart, "Box-plot", new Dimension((int) width, height));
	}

	private static ArrayList<Double> createValueList(final double[] values) {
		final ArrayList<Double> result = new ArrayList<>(values.length);
		for (int i = 0; i < values.length; i++) {
			result.add(new Double(values[i]));
		}
		return result;
	}

	private ChartFrame getFrame(final JFreeChart chart, final String title, final Dimension preferredSize) {
		chart.setBackgroundPaint(null);
		chart.setAntiAlias(true);
		chart.setTextAntiAlias(true);
		final ChartPanel cp = new ChartPanel(chart);
		// Tweak: Ensure chart is always drawn and not scaled to avoid rendering
		// artifacts
		cp.setMinimumDrawWidth(0);
		cp.setMaximumDrawWidth(Integer.MAX_VALUE);
		cp.setMinimumDrawHeight(0);
		cp.setMaximumDrawHeight(Integer.MAX_VALUE);
		cp.setBackground(null);
		final ChartFrame frame = new ChartFrame(title, chart);
		frame.setPreferredSize(preferredSize);
		frame.setBackground(Color.WHITE); // provided contrast to otherwise transparent background
		frame.pack();
		return frame;
	}

	/**
	 * This modifies the default BoxAndWhiskerRenderer to achieve the following: 1)
	 * Highlight mean w/ a more discrete marker; 2) Do not use far out markers
	 * (their definition is not transparent to the user); 3) Make rendering of
	 * outliers optional. If outliers are chosen to be rendered, then render all
	 * values (the original implementation renders summary values only!?).
	 * <p>
	 * NB: It has not been thoroughly tested. Horizontal plots are not affected
	 * because we're not overriding drawHorizontalItem()
	 * </p>
	 */
	private class CustomBoxAndWhiskerRenderer extends BoxAndWhiskerRenderer {

		private static final long serialVersionUID = 1L;
		private double pointSize = 5d;
		private boolean drawOutliers;

		private void setPointSize(final Double pointSize) {
			this.pointSize = pointSize;
		}

		private void setDrawOutliers(final boolean drawOutliers) {
			this.drawOutliers = drawOutliers;
		}

		@Override
		public void drawVerticalItem(final Graphics2D g2, final CategoryItemRendererState state,
				final Rectangle2D dataArea, final CategoryPlot plot, final CategoryAxis domainAxis,
				final ValueAxis rangeAxis, final CategoryDataset dataset, final int row, final int column) {

			final BoxAndWhiskerCategoryDataset bawDataset = (BoxAndWhiskerCategoryDataset) dataset;

			final double categoryEnd = domainAxis.getCategoryEnd(column, getColumnCount(), dataArea,
					plot.getDomainAxisEdge());
			final double categoryStart = domainAxis.getCategoryStart(column, getColumnCount(), dataArea,
					plot.getDomainAxisEdge());
			final double categoryWidth = categoryEnd - categoryStart;

			double xx = categoryStart;
			final int seriesCount = getRowCount();
			final int categoryCount = getColumnCount();

			if (seriesCount > 1) {
				final double seriesGap = dataArea.getWidth() * getItemMargin() / (categoryCount * (seriesCount - 1));
				final double usedWidth = (state.getBarWidth() * seriesCount) + (seriesGap * (seriesCount - 1));
				// offset the start of the boxes if the total width used is smaller
				// than the category width
				final double offset = (categoryWidth - usedWidth) / 2;
				xx = xx + offset + (row * (state.getBarWidth() + seriesGap));
			} else {
				// offset the start of the box if the box width is smaller than the
				// category width
				final double offset = (categoryWidth - state.getBarWidth()) / 2;
				xx = xx + offset;
			}

			double yyAverage;

			final Paint itemPaint = getItemPaint(row, column);
			g2.setPaint(itemPaint);
			final Stroke s = getItemStroke(row, column);
			g2.setStroke(s);

			final RectangleEdge location = plot.getRangeAxisEdge();

			final Number yQ1 = bawDataset.getQ1Value(row, column);
			final Number yQ3 = bawDataset.getQ3Value(row, column);
			final Number yMax = bawDataset.getMaxRegularValue(row, column);
			final Number yMin = bawDataset.getMinRegularValue(row, column);
			Shape box = null;
			if (yQ1 != null && yQ3 != null && yMax != null && yMin != null) {

				final double yyQ1 = rangeAxis.valueToJava2D(yQ1.doubleValue(), dataArea, location);
				final double yyQ3 = rangeAxis.valueToJava2D(yQ3.doubleValue(), dataArea, location);
				final double yyMax = rangeAxis.valueToJava2D(yMax.doubleValue(), dataArea, location);
				final double yyMin = rangeAxis.valueToJava2D(yMin.doubleValue(), dataArea, location);
				final double xxmid = xx + state.getBarWidth() / 2.0;
				final double halfW = (state.getBarWidth() / 2.0) * getWhiskerWidth();

				// draw the body...
				box = new Rectangle2D.Double(xx, Math.min(yyQ1, yyQ3), state.getBarWidth(), Math.abs(yyQ1 - yyQ3));
				if (getFillBox()) {
					g2.fill(box);
				}

				final Paint outlinePaint = getItemOutlinePaint(row, column);
				if (getUseOutlinePaintForWhiskers()) {
					g2.setPaint(outlinePaint);
				}
				// draw the upper shadow...
				g2.draw(new Line2D.Double(xxmid, yyMax, xxmid, yyQ3));
				g2.draw(new Line2D.Double(xxmid - halfW, yyMax, xxmid + halfW, yyMax));

				// draw the lower shadow...
				g2.draw(new Line2D.Double(xxmid, yyMin, xxmid, yyQ1));
				g2.draw(new Line2D.Double(xxmid - halfW, yyMin, xxmid + halfW, yyMin));

				g2.setStroke(getItemOutlineStroke(row, column));
				g2.setPaint(outlinePaint);
				g2.draw(box);
			}

			g2.setPaint(getArtifactPaint());

			// draw mean - SPECIAL AIMS REQUIREMENT...
			if (isMeanVisible()) {
				final Number yMean = bawDataset.getMeanValue(row, column);
				if (yMean != null) {
					yyAverage = rangeAxis.valueToJava2D(yMean.doubleValue(), dataArea, location);
					final double xxAverage = xx + state.getBarWidth() / 2.0;
					final Shape s1 = new Line2D.Double(xxAverage - pointSize, yyAverage, xxAverage + pointSize,
							yyAverage);
					final Shape s2 = new Line2D.Double(xxAverage, yyAverage - pointSize, xxAverage,
							yyAverage + pointSize);
					g2.draw(s1);
					g2.draw(s2);
				}
			}

			// draw median...
			if (isMedianVisible()) {
				final Number yMedian = bawDataset.getMedianValue(row, column);
				if (yMedian != null) {
					final double yyMedian = rangeAxis.valueToJava2D(yMedian.doubleValue(), dataArea, location);
					g2.draw(new Line2D.Double(xx, yyMedian, xx + state.getBarWidth(), yyMedian));
				}
			}

			// draw yOutliers...
			if (drawOutliers) {

				g2.setPaint(itemPaint);

				// draw outliers
				final HashMap<Outlier, Integer> outliers = new HashMap<>();
				final java.util.List<?> yOutliers = bawDataset.getOutliers(row, column);
				final double xCenter = xx + state.getBarWidth() / 2.0;
				if (yOutliers != null) {
					for (int i = 0; i < yOutliers.size(); i++) {
						final Number outlierValue = ((Number) yOutliers.get(i));
						final double yyOutlier = rangeAxis.valueToJava2D(outlierValue.doubleValue(), dataArea,
								location);
						final Outlier outlier = new Outlier(xCenter, yyOutlier, pointSize);
						outliers.put(outlier, outliers.getOrDefault(outlier, 1));
					}

					outliers.forEach((outlier, count) -> {

						if (count == 1) {
							drawOutlier(outlier, g2);
						} else {
							final int leftPoints = (int) Math.round(count / 2);
							final int rightPoints = count - leftPoints;
							for (int i = 1; i <= leftPoints; i++) {
								final double offset = Math.min(i * pointSize, state.getBarWidth() / 2);
								outlier.setPoint(new Point2D.Double(xCenter - offset, outlier.getY()));
								drawOutlier(outlier, g2);
							}
							for (int i = 1; i <= rightPoints; i++) {
								final double offset = Math.min(i * pointSize, state.getBarWidth() / 2);
								outlier.setPoint(new Point2D.Double(xCenter + offset, outlier.getY()));
								drawOutlier(outlier, g2);
							}
						}

					});
				}
			}
			// collect entity and tool tip information...
			if (state.getInfo() != null && box != null) {
				final EntityCollection entities = state.getEntityCollection();
				if (entities != null) {
					addItemEntity(entities, dataset, row, column, box);
				}
			}

		}

		private void drawOutlier(final Outlier outlier, final Graphics2D g2) {
			final Point2D point = outlier.getPoint();
			final double size = outlier.getRadius();
			final Ellipse2D dot = new Ellipse2D.Double(point.getX() + size / 2, point.getY(), size, size);
			g2.fill(dot);
		}

	}

	/* IDE debug method */
	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		final SNTService sntService = ij.context().getService(SNTService.class);
		final GroupedTreeStatistics groupedStats = new GroupedTreeStatistics();
		groupedStats.addGroup(sntService.demoTrees().subList(0, 4), "Group 1");
		groupedStats.addGroup(sntService.demoTrees().subList(2, 4), "Group 2");
		/// groupedStats.getHistogram(TreeStatistics.INTER_NODE_DISTANCE).setVisible(true);
		groupedStats.getBoxPlot("node dx sq").setVisible(true);

	}
}
