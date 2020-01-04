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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.scijava.table.DefaultGenericTable;

import net.imagej.ImageJ;

import org.scijava.app.StatusService;
import org.scijava.command.ContextCommand;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import sc.fiji.snt.Path;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.util.PointInImage;

/**
 * Class for analysis of {@link Tree}s
 *
 * @author Tiago Ferreira
 */
@Plugin(type = ContextCommand.class, visible = false)
public class TreeAnalyzer extends ContextCommand {

	@Parameter
	protected StatusService statusService;

	@Parameter
	protected DisplayService displayService;


	protected Tree tree;
	private Tree unfilteredTree;
	private HashSet<Path> primaryBranches;
	private HashSet<Path> terminalBranches;
	private HashSet<PointInImage> joints;
	private HashSet<PointInImage> tips;
	protected DefaultGenericTable table;
	private String tableTitle;
	private StrahlerAnalyzer sAnalyzer;

	private int fittedPathsCounter = 0;
	private int unfilteredPathsFittedPathsCounter = 0;

	/**
	 * Instantiates a new Tree analyzer.
	 *
	 * @param tree Collection of Paths to be analyzed. Note that null Paths are
	 *          discarded. Also, when a Path has been fitted and
	 *          {@link Path#getUseFitted()} is true, its fitted 'flavor' is used.
	 * @see #getParsedTree()
	 */
	public TreeAnalyzer(final Tree tree) {
		this.tree = new Tree();
		this.tree.setLabel(tree.getLabel());
		for (final Path p : tree.list()) {
			if (p == null) continue;
			Path pathToAdd;
			// If fitted flavor of path exists use it instead
			if (p.getUseFitted() && p.getFitted() != null) {
				pathToAdd = p.getFitted();
				fittedPathsCounter++;
			}
			else {
				pathToAdd = p;
			}
			this.tree.add(pathToAdd);
		}
		unfilteredPathsFittedPathsCounter = fittedPathsCounter;
	}

	/**
	 * Restricts analysis to Paths sharing the specified SWC flag(s).
	 *
	 * @param types the allowed SWC flags (e.g., {@link Path#SWC_AXON}, etc.)
	 */
	public void restrictToSWCType(final int... types) {
		initializeSnapshotTree();
		tree = tree.subTree(types);
	}

	/**
	 * Ignores Paths sharing the specified SWC flag(s).
	 *
	 * @param types the SWC flags to be ignored (e.g., {@link Path#SWC_AXON},
	 *          etc.)
	 */
	public void ignoreSWCType(final int... types) {
		initializeSnapshotTree();
		final ArrayList<Integer> allowedTypes = Path.getSWCtypes();
		for (final int type : types) {
			allowedTypes.remove(Integer.valueOf(type));
		}
		tree = tree.subTree(allowedTypes.stream().mapToInt(i -> i).toArray());
	}

	/**
	 * Restricts analysis to Paths sharing the specified Path {@link Path#getOrder()
	 * order}(s).
	 *
	 * @param orders the allowed Path orders
	 */
	public void restrictToOrder(final int... orders) {
		initializeSnapshotTree();
		final Iterator<Path> it = tree.list().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final boolean valid = Arrays.stream(orders).anyMatch(t -> t == p
				.getOrder());
			if (!valid) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	/**
	 * Restricts analysis to paths having the specified number of nodes.
	 *
	 * @param minSize the smallest number of nodes a path must have in order to be
	 *          analyzed. Set it to -1 to disable minSize filtering
	 * @param maxSize the largest number of nodes a path must have in order to be
	 *          analyzed. Set it to -1 to disable maxSize filtering
	 */
	public void restrictToSize(final int minSize, final int maxSize) {
		initializeSnapshotTree();
		final Iterator<Path> it = tree.list().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final int size = p.size();
			if ((minSize > 0 && size < minSize) || (maxSize > 0 && size > maxSize)) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	/**
	 * Restricts analysis to paths sharing the specified length range.
	 *
	 * @param lowerBound the smallest length a path must have in order to be
	 *          analyzed. Set it to Double.NaN to disable lowerBound filtering
	 * @param upperBound the largest length a path must have in order to be
	 *          analyzed. Set it to Double.NaN to disable upperBound filtering
	 */
	public void restrictToLength(final double lowerBound,
		final double upperBound)
	{
		initializeSnapshotTree();
		final Iterator<Path> it = tree.list().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final double length = p.getLength();
			if (length < lowerBound || length > upperBound) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	/**
	 * Restricts analysis to Paths containing the specified string in their name.
	 *
	 * @param pattern the string to search for
	 */
	public void restrictToNamePattern(final String pattern) {
		initializeSnapshotTree();
		final Iterator<Path> it = tree.list().iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			if (!p.getName().contains(pattern)) {
				updateFittedPathsCounter(p);
				it.remove();
			}
		}
	}

	private void initializeSnapshotTree() {
		if (unfilteredTree == null) {
			unfilteredTree = new Tree(tree.list());
			unfilteredTree.setLabel(tree.getLabel());
		}
		sAnalyzer = null; // reset Strahler analyzer
	}

	/**
	 * Removes any filtering restrictions that may have been set. Once called,
	 * subsequent analysis will use all paths initially parsed by the constructor.
	 * Does nothing if no paths are currently being excluded from the analysis.
	 */
	public void resetRestrictions() {
		if (unfilteredTree == null) return; // no filtering has occurred
		tree.replaceAll(unfilteredTree.list());
		joints = null;
		primaryBranches = null;
		terminalBranches = null;
		tips = null;
		sAnalyzer = null;
		fittedPathsCounter = unfilteredPathsFittedPathsCounter;
	}

	private void updateFittedPathsCounter(final Path filteredPath) {
		if (fittedPathsCounter > 0 && filteredPath.isFittedVersionOfAnotherPath())
			fittedPathsCounter--;
	}

	/**
	 * Returns the set of parsed Paths.
	 *
	 * @return the set of paths currently being considered for analysis.
	 * @see #resetRestrictions()
	 */
	public Tree getParsedTree() {
		return tree;
	}

	/**
	 * Outputs a summary of the current analysis to the Analyzer table using the
	 * default Tree label.
	 *
	 * @param groupByType if true measurements are grouped by SWC-type flag
	 * @see #run()
	 * @see #setTable(DefaultGenericTable)
	 */
	public void summarize(final boolean groupByType) {
		summarize(tree.getLabel(), groupByType);
	}

	/**
	 * Outputs a summary of the current analysis to the Analyzer table.
	 *
	 * @param rowHeader the String to be used as label for the summary
	 * @param groupByType if true measurements are grouped by SWC-type flag
	 * @see #run()
	 * @see #setTable(DefaultGenericTable)
	 */
	public void summarize(final String rowHeader, final boolean groupByType) {
		measure(rowHeader, Arrays.asList(MultiTreeStatistics.COMMON_FLAGS), true);
	}

	private int getNextRow(final String rowHeader) {
		table.appendRow((rowHeader==null)?"":rowHeader);
		return table.getRowCount() - 1;
	}

	/**
	 * Gets a list of supported metrics. Note that this list will only include
	 * commonly used metrics. For a complete list of supported metrics see e.g.,
	 * {@link MultiTreeStatistics#getAllMetrics()}
	 * 
	 * @return the list of available metrics
	 * @see MultiTreeStatistics#getMetrics()
	 */
	public static List<String> getMetrics() {
		return MultiTreeStatistics.getMetrics();
	}

	protected Number getMetric(final String metric) {
		switch (metric) {
		case MultiTreeStatistics.ASSIGNED_VALUE:
			return tree.getAssignedValue();
		case MultiTreeStatistics.DEPTH:
			return getDepth();
		case MultiTreeStatistics.HEIGHT:
			return getHeight();
		case MultiTreeStatistics.HIGHEST_PATH_ORDER:
			return getHighestPathOrder();
		case MultiTreeStatistics.LENGTH:
			return getCableLength();
		case MultiTreeStatistics.MEAN_RADIUS:
			final MultiTreeStatistics treeStats = new MultiTreeStatistics(Collections.singleton(tree));
			return treeStats.getSummaryStats(MultiTreeStatistics.MEAN_RADIUS).getMean();
		case MultiTreeStatistics.N_BRANCH_POINTS:
			return getBranchPoints().size();
		case MultiTreeStatistics.N_BRANCHES:
			try {
				return getNBranches();
			} catch (final IllegalArgumentException ignored) {
				SNTUtils.log("Error: " + ignored.getMessage());
				return Double.NaN;
			}
		case MultiTreeStatistics.AVG_CONTRACTION:
			try {
				return getAvgContraction();
			} catch (final IllegalArgumentException ignored) {
				SNTUtils.log("Error: " + ignored.getMessage());
				return Double.NaN;
			}
		case MultiTreeStatistics.AVG_BRANCH_LENGTH:
			try {
				return getAvgBranchLength();
			} catch (final IllegalArgumentException ignored) {
				SNTUtils.log("Error: " + ignored.getMessage());
				return Double.NaN;
			}
		case MultiTreeStatistics.N_NODES:
			return tree.getNodes().size();
		case MultiTreeStatistics.N_PRIMARY_BRANCHES:
			return getPrimaryBranches().size();
		case MultiTreeStatistics.N_TERMINAL_BRANCHES:
			return getTerminalBranches().size();
		case MultiTreeStatistics.N_TIPS:
			return getTips().size();
		case MultiTreeStatistics.N_PATHS:
			return getNPaths();
		case MultiTreeStatistics.N_FITTED_PATHS:
			return getNFittedPaths();
		case MultiTreeStatistics.PRIMARY_LENGTH:
			return getPrimaryLength();
		case MultiTreeStatistics.STRAHLER_NUMBER:
			try {
				return getStrahlerNumber();
			} catch (final IllegalArgumentException ignored) {
				SNTUtils.log("Error: " + ignored.getMessage());
				return Double.NaN;
			}
		case MultiTreeStatistics.STRAHLER_RATIO:
			try {
				return getStrahlerBifurcationRatio();
			} catch (final IllegalArgumentException ignored) {
				SNTUtils.log("Error: " + ignored.getMessage());
				return Double.NaN;
			}
		case MultiTreeStatistics.TERMINAL_LENGTH:
			return getTerminalLength();
		case MultiTreeStatistics.WIDTH:
			return getWidth();
		default:
			throw new IllegalArgumentException("Unrecognizable measurement \"" + metric + "\". "
					+ "Maybe you meant one of the following?: \"" +  String.join(", ", getMetrics() +"\""));
		}
	}

	/**
	 * Measures this Tree
	 *
	 * @param metrics the metrics
	 * @param groupByType the group by type
	 * @see #getAvailableMetrics()
	 */
	public void measure(final Collection<String> metrics, final boolean groupByType) {
		measure(tree.getLabel(), metrics, groupByType);
	}

	/**
	 * Measures this Tree.
	 *
	 * @param rowHeader the row header
	 * @param metrics the metrics
	 * @param groupByType the group by type
	 * @see #getAvailableMetrics()
	 */
	public void measure(final String rowHeader, final Collection<String> metrics, final boolean groupByType) {
		if (table == null) table = new DefaultGenericTable();
		final int lastRow = table.getRowCount() - 1;
		if (groupByType) {
			for (final int type : tree.getSWCTypes()) {
				if (type == Path.SWC_SOMA) continue;
				restrictToSWCType(type);
				final int row = getNextRow(rowHeader);
				table.set(getCol("SWC Type"), row, Path.getSWCtypeName(type, true));
				metrics.forEach(metric -> table.set(getCol(metric), row, getMetric(metric)));
				resetRestrictions();
			}
		} else {
			int row = getNextRow(rowHeader);
			table.set(getCol("SWC Types"), row, getSWCTypesAsString());
			metrics.forEach(metric -> table.set(getCol(metric), row, getMetric(metric)));
		}
		if (getContext() == null) {
			System.out.println(SNTUtils.tableToString(table, lastRow + 1, table.getRowCount() - 1));
		} else
			updateAndDisplayTable();
	}

	protected String getSWCTypesAsString() {
		final StringBuilder sb = new StringBuilder();
		final Set<Integer> types = tree.getSWCTypes();
		for (int type: types) {
			sb.append(Path.getSWCtypeName(type, true)).append(" ");
		}
		return sb.toString().trim();
	}

	/**
	 * Sets the Analyzer table.
	 *
	 * @param table the table to be used by the analyzer
	 * @see #summarize(boolean)
	 */
	public void setTable(final DefaultGenericTable table) {
		this.table = table;
	}

	/**
	 * Sets the table.
	 *
	 * @param table the table to be used by the analyzer
	 * @param title the title of the table display window
	 */
	public void setTable(final DefaultGenericTable table, final String title) {
		this.table = table;
		this.tableTitle = title;
	}

	/**
	 * Gets the table currently being used by the Analyzer
	 *
	 * @return the table
	 */
	public DefaultGenericTable getTable() {
		return table;
	}

	/**
	 * Generates detailed summaries in which measurements are grouped by SWC-type
	 * flags
	 *
	 * @see #summarize(String, boolean)
	 */
	@Override
	public void run() {
		if (tree.list() == null || tree.list().isEmpty()) {
			cancel("No Paths to Measure");
			return;
		}
		statusService.showStatus("Measuring Paths...");
		summarize(true);
		statusService.clearStatus();
	}

	/**
	 * Updates and displays the Analyzer table.
	 */
	public void updateAndDisplayTable() {
		if (getContext() == null) {
			System.out.println(SNTUtils.tableToString(table, 0, table.getRowCount() - 1));
			return;
		}
		final String displayName = (tableTitle == null) ? "SNT Measurements"
			: tableTitle;
		final Display<?> display = displayService.getDisplay(displayName);
		if (display != null && display.isDisplaying(table)) {
			display.update();
		}
		else {
			displayService.createDisplay(displayName, table);
		}
	}

	protected int getCol(final String header) {
		int idx = table.getColumnIndex(header);
		if (idx == -1) {
			table.appendColumn(header);
			idx = table.getColumnCount() - 1;
		}
		return idx;
	}

	protected int getSinglePointPaths() {
		return (int) tree.list().stream().filter(p -> p.size() == 1).count();
	}

	/**
	 * Gets the no. of paths parsed by the Analyzer.
	 *
	 * @return the number of paths
	 */
	public int getNPaths() {
		return tree.list().size();
	}

	protected int getNFittedPaths() {
		return fittedPathsCounter;
	}

	public double getWidth() {
		return tree.getBoundingBox(true).width();
	}

	public double getHeight() {
		return tree.getBoundingBox(true).height();
	}

	public double getDepth() {
		return tree.getBoundingBox(true).depth();
	}

	/**
	 * Retrieves all the Paths in the analyzed Tree tagged as primary.
	 *
	 * @return the set of primary paths.
	 * @see #getPrimaryBranches()
	 */
	public Set<Path> getPrimaryPaths() {
		final HashSet<Path> primaryPaths = new HashSet<>();
		for (final Path p : tree.list()) {
			if (p.isPrimary()) primaryPaths.add(p);
		}
		return primaryPaths;
	}

	/**
	 * Retrieves the primary branches of the analyzed Tree. A primary branch
	 * corresponds to the section of a primary Path between its origin and its
	 * closest branch-point.
	 *
	 * @return the set containing the primary branches. Note that as per
	 *         {@link Path#getSection(int, int)}, these branches will not carry any
	 *         connectivity information.
	 * @see #getPrimaryPaths()
	 * @see #restrictToOrder(int...)
	 */
	public Set<Path> getPrimaryBranches() {
		primaryBranches = new HashSet<>();
		for (final Path p : tree.list()) {
			if (!p.isPrimary()) continue;
			final TreeSet<Integer> joinIndices = p.findJunctionIndices();
			final int firstJointIdx = (joinIndices.isEmpty()) ? p.size() - 1 : joinIndices.first();
			primaryBranches.add(p.getSection(0, firstJointIdx));
		}
		return primaryBranches;
	}

	/**
	 * Retrieves the terminal branches of the analyzed Tree. A terminal branch
	 * corresponds to the section of a terminal Path between its last branch-point
	 * and its terminal point (tip).
	 *
	 * @return the set containing terminal branches. Note that as per
	 *         {@link Path#getSection(int, int)}, these branches will not carry any
	 *         connectivity information.
	 * @see #getPrimaryBranches
	 * @see #restrictToOrder(int...)
	 */
	public Set<Path> getTerminalBranches() {
		terminalBranches = new HashSet<>();
		if (joints == null) getBranchPoints();
		for (final Path p : tree.list()) {
			final int lastNodeIdx = p.size() - 1;
			if (joints.contains(p.getNode(lastNodeIdx))) {
				continue; // not a terminal branch
			}
			final TreeSet<Integer> joinIndices = p.findJunctionIndices();
			final int lastJointIdx = (joinIndices.isEmpty()) ? 0 : joinIndices.last();
			terminalBranches.add(p.getSection(lastJointIdx, lastNodeIdx));
		}
		return terminalBranches;
	}

	/**
	 * Gets the position of all the tips in the analyzed tree.
	 *
	 * @return the set of terminal points
	 */
	public Set<PointInImage> getTips() {

		// retrieve all start/end points
		tips = new HashSet<>();
		for (final Path p : tree.list()) {
			tips.add(p.getNode(p.size() - 1));
		}
		// now remove any joint-associated point
		if (joints == null) getBranchPoints();
		tips.removeAll(joints);
		return tips;

	}

	/**
	 * Gets the position of all the branch points in the analyzed tree.
	 *
	 * @return the branch points positions
	 */
	public Set<PointInImage> getBranchPoints() {
		joints = new HashSet<>();
		for (final Path p : tree.list()) {
			joints.addAll(p.findJunctions());
		}
		return joints;
	}

	/**
	 * Gets the cable length.
	 *
	 * @return the cable length of the tree
	 */
	public double getCableLength() {
		return sumLength(tree.list());
	}

	/**
	 * Gets the cable length of primary branches.
	 *
	 * @return the length sum of all primary branches
	 * @see #getPrimaryBranches()
	 */
	public double getPrimaryLength() {
		if (primaryBranches == null) getPrimaryBranches();
		return sumLength(primaryBranches);
	}

	/**
	 * Gets the cable length of terminal branches
	 *
	 * @return the length sum of all terminal branches
	 * @see #getTerminalBranches()
	 */
	public double getTerminalLength() {		
		if (terminalBranches == null) getTerminalBranches();
		return sumLength(terminalBranches);
	}

	/**
	 * Gets the highest {@link sc.fiji.snt.Path#getOrder() path order} of the analyzed tree
	 *
	 * @return the highest Path order, or -1 if Paths in the Tree have no defined
	 *         order
	 * @see #getStrahlerNumber()
	 */
	public int getHighestPathOrder() {
		int root = -1;
		for (final Path p : tree.list()) {
			final int order = p.getOrder();
			if (order > root) root = order;
		}
		return root;
	}

	/**
	 * Checks whether this tree is topologically valid, i.e., contains only one root
	 * and no loops.
	 *
	 * @return true, if Tree is valid, false otherwise
	 */
	public boolean isValid() {
		if (sAnalyzer == null)
			sAnalyzer = new StrahlerAnalyzer(tree);
		try {
			sAnalyzer.getGraph();
			return true;
		} catch (final IllegalArgumentException ignored) {
			return false;
		}
	}

	/**
	 * Gets the highest {@link StrahlerAnalyzer#getRootNumber() Strahler number} of
	 * the analyzed tree.
	 *
	 * @return the highest Strahler (root) number order
	 * @throws IllegalArgumentException if tree contains multiple roots or loops
	 */
	public int getStrahlerNumber() throws IllegalArgumentException {
		if (sAnalyzer == null) sAnalyzer = new StrahlerAnalyzer(tree);
		return sAnalyzer.getRootNumber();
	}

	/**
	 * Gets the {@link StrahlerAnalyzer} instance associated with this analyzer
	 *
	 * @return the StrahlerAnalyzer instance associated with this analyzer
	 * @throws IllegalArgumentException if tree contains multiple roots or loops
	 */
	public StrahlerAnalyzer getStrahlerAnalyzer() throws IllegalArgumentException {
		if (sAnalyzer == null) sAnalyzer = new StrahlerAnalyzer(tree);
		return sAnalyzer;
	}

	/**
	 * Gets the average {@link StrahlerAnalyzer#getAvgBifurcationRatio() Strahler
	 * bifurcation ratio} of the analyzed tree.
	 *
	 * @return the average bifurcation ratio
	 * @throws IllegalArgumentException if tree contains multiple roots or loops
	 */
	public double getStrahlerBifurcationRatio() throws IllegalArgumentException {
		if (sAnalyzer == null) sAnalyzer = new StrahlerAnalyzer(tree);
		return sAnalyzer.getAvgBifurcationRatio();
	}

	/**
	 * Gets the number of branches in the analyzed tree.
	 *
	 * @return the number of branches
	 * @throws IllegalArgumentException if tree contains multiple roots or loops
	 */
	public int getNBranches() throws IllegalArgumentException {
		if (sAnalyzer == null) sAnalyzer = new StrahlerAnalyzer(tree);
		return (int) sAnalyzer.getBranchCounts().values().stream().mapToDouble(f -> f).sum();
	}

	/**
	 * Gets all the branches in the analyzed tree. A branch is defined as the Path
	 * composed of all the nodes between two branching points or between one
	 * branching point and a termination point.
	 *
	 * @return the list of branches as Path objects.
	 * @see StrahlerAnalyzer#getBranches()
	 * @throws IllegalArgumentException if tree contains multiple roots or loops
	 */
	public List<Path> getBranches() throws IllegalArgumentException {
		if (sAnalyzer == null) sAnalyzer = new StrahlerAnalyzer(tree);
		return sAnalyzer.getBranches().values().stream().flatMap(List::stream).collect(Collectors.toList());
	}

	/**
	 * Gets average {@link Path#getContraction() contraction} for all the branches
	 * of the analyzed tree.
	 * 
	 * @throws IllegalArgumentException if tree contains multiple roots or loops
	 * @return the average branch contraction
	 */
	public double getAvgContraction() throws IllegalArgumentException {
		if (sAnalyzer == null) sAnalyzer = new StrahlerAnalyzer(tree);
		double contraction = 0;
		final List<Path> branches = getBranches();
		for (final Path p : branches) {
			contraction += p.getContraction();
		}
		return contraction / branches.size();
	}

	/**
	 * Gets average length for all the branches of the analyzed tree.
	 * 
	 * @throws IllegalArgumentException if tree contains multiple roots or loops
	 * @return the average branch length
	 */
	public double getAvgBranchLength() throws IllegalArgumentException {
		if (sAnalyzer == null) sAnalyzer = new StrahlerAnalyzer(tree);
		final List<Path> branches = getBranches();
		return sumLength(getBranches()) / branches.size();
	}

	private double sumLength(final Collection<Path> paths) {
		return paths.stream().mapToDouble(p -> p.getLength()).sum();
	}


	/* IDE debug method */
	public static void main(final String[] args) throws InterruptedException {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final SNTService sntService = ij.context().getService(SNTService.class);
		sntService.initialize(sntService.demoTreeImage(), true);
		final Tree tree = sntService.demoTree();
		sntService.loadTree(tree);
		final TreeAnalyzer analyzer = new TreeAnalyzer(tree);
		for (Path p : analyzer.getPrimaryBranches()) {
			sntService.getPlugin().getPathAndFillManager().addPath(p);
		}
		sntService.getPlugin().updateAllViewers();
	}
}
