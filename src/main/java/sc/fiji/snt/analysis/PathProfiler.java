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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imagej.ImageJ;
import net.imagej.plot.LineStyle;
import net.imagej.plot.MarkerStyle;
import net.imagej.plot.PlotService;
import net.imagej.plot.XYPlot;
import net.imagej.plot.XYSeries;

import org.scijava.app.StatusService;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Plot;
import ij.measure.Calibration;
import sc.fiji.snt.Path;
import sc.fiji.snt.Tree;
import sc.fiji.snt.util.BoundingBox;
import sc.fiji.snt.util.PointInImage;
import sc.fiji.snt.util.SNTColor;

/**
 * Command to retrieve Profile plots (plots of voxel intensities values along a
 * Path) from reconstructions.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = ContextCommand.class, visible = false)
public class PathProfiler extends ContextCommand {

	/** Flag for retrieving distances from {@link #getValues(Path)} */
	public final static String X_VALUES = "x-values";

	/** Flag for retrieving intensities from {@link #getValues(Path)} */
	public final static String Y_VALUES = "y-values";

	@Parameter
	private PlotService plotService;

	@Parameter
	private UIService uiService;

	@Parameter
	private StatusService statusService;

	private final Tree tree;
	private final ImageStack stack;
	private final ImagePlus imp;
	private final BoundingBox impBox;
	private boolean valuesAssignedToTree;
	private boolean nodeIndices = false;

	/**
	 * Instantiates a new Profiler
	 *
	 * @param tree the Tree to be profiled
	 * @param imp the image from which pixel intensities will be retrieved. Note
	 *          that no effort is made to ensure that the image is suitable for
	 *          profiling, if Tree nodes lay outside the image dimensions, pixels
	 *          intensities will be retrieved as {@code Float#NaN}
	 */
	public PathProfiler(final Tree tree, final ImagePlus imp) {
		if (imp == null || tree == null) throw new IllegalArgumentException(
			"Image and Tree cannot be null");
		this.tree = tree;
		this.imp = imp;
		this.stack = imp.getImageStack();
		impBox = getImpBoundingBox(imp);
	}

	/**
	 * Instantiates a new Profiler from a single path
	 *
	 * @param path the path to be profiled
	 * @param imp the image from which pixel intensities will be retrieved. Note
	 *          that no effort is made to ensure that the image is suitable for
	 *          profiling, if Tree nodes lay outside the image dimensions, pixels
	 *          intensities will be retrieved as {@code Float#NaN}
	 */
	public PathProfiler(final Path path, final ImagePlus imp) {
		this(new Tree(Collections.singleton(path)), imp);
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		if (tree.size() == 0) {
			cancel("No path(s) to profile");
			return;
		}
		statusService.showStatus("Measuring Paths...");
		uiService.show(getPlotTitle(), getPlot());
		statusService.clearStatus();
	}

	private String getPlotTitle() {
		if (tree.size() == 1) return tree.get(0).getName() + " Profile";
		return (tree.getLabel() == null) ? "Path Profile" : tree.getLabel() +
			" Path Profile";
	}

	private BoundingBox getImpBoundingBox(final ImagePlus imp) {
		final BoundingBox impBox = new BoundingBox();
		final Calibration cal = imp.getCalibration();
		impBox.setOrigin(new PointInImage(cal.xOrigin, cal.yOrigin, cal.zOrigin));
		impBox.setDimensions(imp.getWidth(), imp.getHeight(), imp.getNSlices());
		impBox.setSpacing(cal.pixelWidth, cal.pixelHeight, cal.pixelDepth, cal
			.getUnit());
		return impBox;
	}

	/**
	 * Checks whether the specified image contains all the nodes of the profiled
	 * Tree/Path.
	 *
	 * @return true, if successful, false if Tree has nodes outside the image
	 *         boundaries.
	 */
	public boolean validImage() {
		BoundingBox bbox = tree.getBoundingBox(false);
		if (bbox == null) bbox = tree.getBoundingBox(true);
		return impBox.contains(bbox);
	}

	/**
	 * Calls {@link #assignValues(Path)} on the Paths of the profiled Tree
	 */
	public void assignValues() {
		for (final Path p : tree.list())
			assignValues(p);
		valuesAssignedToTree = true;
	}

	/**
	 * Retrieves pixel intensities at each node of the Path storing them as Path
	 * {@code values}
	 * 
	 * @see Path#setNodeValues(double[])
	 * @param p the Path to be profiled
	 */
	public void assignValues(final Path p) {
		final double[] values = new double[p.size()];
		final int c = p.getChannel();
		final int f = p.getFrame();
		for (int i = 0; i < p.size(); i++) {
			try {
				final int zPos = imp.getStackIndex(c, p.getZUnscaled(i) + 1, f);
				values[i] = stack.getVoxel(p.getXUnscaled(i), p.getYUnscaled(i), zPos);
			}
			catch (final IndexOutOfBoundsException exc) {
				values[i] = Float.NaN;
			}
		}
		p.setNodeValues(values);
	}

	private Map<String, double[]> getValuesAsArray(final Path p) {
		if (!p.hasNodeValues()) assignValues(p);
		final double[] xList = new double[p.size()];
		final double[] yList = new double[p.size()];
		final Map<String, List<Double>> values = getValues(p);
		int i = 0;
		for (final double d : values.get(X_VALUES))
			xList[i++] = d;
		i = 0;
		for (final double d : values.get(Y_VALUES))
			yList[i++] = d;
		final Map<String, double[]> map = new HashMap<>();
		map.put(X_VALUES, xList);
		map.put(Y_VALUES, yList);
		return map;
	}

	/**
	 * Sets whether the profile abscissae should be reported in real-word units
	 * (the default) or node indices (zero-based). Must be called before calling
	 * {@link #getValues(Path)}, {@link #getPlot()} or {@link #getXYPlot()}.
	 *
	 * @param nodeIndices If true, distances will be reported as indices.
	 */
	public void setNodeIndicesAsDistances(final boolean nodeIndices) {
		this.nodeIndices = nodeIndices;
	}

	/**
	 * Gets the profile for the specified path as a map of lists, with distances
	 * stored under {@link #X_VALUES} ({@value #X_VALUES}) and intensities under
	 * {@link #Y_VALUES} ({@value #Y_VALUES}).
	 *
	 * @param p the path to be profiled
	 * @return the profile
	 */
	public Map<String, List<Double>> getValues(final Path p) {
		if (!p.hasNodeValues()) assignValues(p);
		final List<Double> xList = new ArrayList<>();
		final List<Double> yList = new ArrayList<>();
		final PointInImage previous = p.getNode(0);
		double dx = 0;
		for (int i = 0; i < p.size(); i++) {
			if (nodeIndices) {
				xList.add(dx++);
			}
			else {
				final PointInImage node = p.getNode(i);
				dx += node.distanceTo(previous);
				xList.add(dx);
			}
			yList.add(p.getNodeValue(i));
		}
		final Map<String, List<Double>> map = new HashMap<>();
		map.put(X_VALUES, xList);
		map.put(Y_VALUES, yList);
		return map;
	}

	private XYSeries addSeries(final XYPlot plot, final Path p,
		final ColorRGB color, boolean setLegend)
	{
		final XYSeries series = plot.addXYSeries();
		final Map<String, List<Double>> values = getValues(p);
		series.setLabel(p.getName());
		series.setStyle(plot.newSeriesStyle(color, LineStyle.SOLID,
			MarkerStyle.CIRCLE));
		series.setValues(values.get(X_VALUES), values.get(Y_VALUES));
		series.setLegendVisible(setLegend);
		return series;
	}

	private Color[] getSeriesColorsAWT() {
		final Color[] colors = new Color[tree.size()];
		if (treeIsColorMapped()) {
			for (int i = 0; i < tree.size(); i++) {
				colors[i] = tree.get(i).getColor();
				if (colors[i] == null) colors[i] = Color.BLACK;
			}
		}
		else {
			final ColorRGB[] colorsRGB = SNTColor.getDistinctColors(tree.size());
			for (int i = 0; i < tree.size(); i++) {
				colors[i] = new Color(colorsRGB[i].getARGB());
			}
		}
		return colors;
	}

	private ColorRGB[] getSeriesColorsRGB() {
		final ColorRGB[] colors;
		if (treeIsColorMapped()) {
			colors = new ColorRGB[tree.size()];
			for (int i = 0; i < tree.size(); i++) {
				colors[i] = tree.get(i).getColorRGB();
				if (colors[i] == null) colors[i] = Colors.BLACK;
			}
		}
		else {
			colors = SNTColor.getDistinctColors(tree.size());
		}
		return colors;
	}

	private String getXAxisLabel() {
		return (nodeIndices) ? "Node indices" : "Distance";
	}

	private String getYAxisLabel() {
		return "Intensity (" + stack.getBitDepth() + "-bit)";
	}

	/**
	 * Gets the plot profile as an IJ1 {@link Plot}.
	 *
	 * @return the plot
	 */
	public Plot getPlot() {
		if (!valuesAssignedToTree) assignValues();
		final Plot plot = new Plot(getPlotTitle(), getXAxisLabel(), getYAxisLabel());
		final Color[] colors = getSeriesColorsAWT();
		final StringBuilder legend = new StringBuilder();
		for (int i = 0; i < tree.size(); i++) {
			final Path p = tree.get(i);
			legend.append(p.getName()).append("\n");
			final Map<String, double[]> values = getValuesAsArray(p);
			plot.setColor(colors[i], colors[i]);
			plot.addPoints(values.get(X_VALUES), values.get(Y_VALUES),
				Plot.CONNECTED_CIRCLES);
		}
		plot.setColor(Color.BLACK, null);
		plot.setLegend(legend.toString(), Plot.LEGEND_TRANSPARENT);
		return plot;
	}

	/**
	 * Gets the plot profile as an {@link PlotService} plot. Requires
	 * {@link #setContext(org.scijava.Context)} to be called beforehand.
	 *
	 * @return the plot
	 */
	public XYPlot getXYPlot() {
		if (!valuesAssignedToTree) assignValues();
		final XYPlot plot = plotService.newXYPlot();
		final boolean setLegend = tree.size() > 1;
		final ColorRGB[] colors = getSeriesColorsRGB();
		int colorIdx = 0;
		for (final Path p : tree.list()) {
			addSeries(plot, p, colors[colorIdx++], setLegend);
		}
		plot.xAxis().setLabel(getXAxisLabel());
		plot.yAxis().setLabel(getYAxisLabel());
		return plot;
	}

	private boolean treeIsColorMapped() {
		final ColorRGB refColor = tree.get(0).getColorRGB();
		for (final Path path : tree.list()) {
			if (path.hasNodeColors()) return true;
			if (refColor != null && !refColor.equals(path.getColorRGB())) return true;
		}
		return false;
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Tree tree = new Tree("/home/tferr/code/OP_1/OP_1.traces");
		final ImagePlus imp = IJ.openImage("/home/tferr/code/OP_1/OP_1.tif");
		final PathProfiler profiler = new PathProfiler(tree, imp);
		profiler.setContext(ij.context());
		System.out.println("Valid image? " + profiler.validImage());
		profiler.setNodeIndicesAsDistances(false);
		profiler.getPlot().show();
		profiler.run();
	}
}
