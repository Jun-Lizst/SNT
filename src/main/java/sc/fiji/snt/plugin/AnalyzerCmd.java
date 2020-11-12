/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2020 Fiji developers.
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

package sc.fiji.snt.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultGenericTable;
import org.scijava.widget.FileWidget;

import net.imagej.ImageJ;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.MultiTreeStatistics;
import sc.fiji.snt.analysis.TreeAnalyzer;
import sc.fiji.snt.gui.GuiUtils;
import sc.fiji.snt.gui.cmds.CommonDynamicCmd;

/**
 * Command for measuring Tree(s).
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label="Measure...", initializer = "init")
public class AnalyzerCmd extends CommonDynamicCmd {

	private static final String TABLE_TITLE = "SNT Measurements";

	// I: Input options
	@Parameter(label = "<HTML><b>Input Files:", persist = false, required = false, visibility = ItemVisibility.MESSAGE)
	private String SPACER1;

	@Parameter(required = false, style = FileWidget.DIRECTORY_STYLE, label = "Directory:",
			description = "<HTML><div WIDTH=500>Path to directory containing files to be measured. "
					+ "NB: A single file can also be specified but the \"Browser\" prompt "
					+ "may not allow single files to be selected. In that case, you can "
					+ "manually specify its path in the text field.")
	private File file;

	@Parameter(label = "File name contains", required = false, description = "<HTML><div WIDTH=500>"
			+ "Only filenames containing this pattern will be imported from the directory")
	private String pattern;

	// II. Metrics
	@Parameter(label = "<HTML>&nbsp;<br><b>Metrics:", persist = false, required = false, visibility = ItemVisibility.MESSAGE)
	private String SPACER2;

	@Parameter(label = MultiTreeStatistics.LENGTH)
	private boolean cableLength;

	@Parameter(label = MultiTreeStatistics.TERMINAL_LENGTH)
	private boolean terminalLength;

	@Parameter(label = MultiTreeStatistics.PRIMARY_LENGTH)
	private boolean primaryLength;

	@Parameter(label = MultiTreeStatistics.INNER_LENGTH)
	private boolean innerLength;

	@Parameter(label = MultiTreeStatistics.AVG_BRANCH_LENGTH)
	private boolean avgBranchLength;

	@Parameter(label = MultiTreeStatistics.AVG_CONTRACTION)
	private boolean avgContraction;

	@Parameter(label = MultiTreeStatistics.AVG_FRAGMENTATION)
	private boolean avgFragmentation;

	@Parameter(label = MultiTreeStatistics.AVG_REMOTE_ANGLE)
	private boolean avgRemoteAngle;
	
	@Parameter(label = MultiTreeStatistics.AVG_PARTITION_ASYMMETRY)
	private boolean avgPartitionAsymmetry;
	
	@Parameter(label = MultiTreeStatistics.AVG_FRACTAL_DIMENSION)
	private boolean avgFractalDimension;

	@Parameter(label = MultiTreeStatistics.N_BRANCH_POINTS)
	private boolean nBPs;

	@Parameter(label = MultiTreeStatistics.N_TIPS)
	private boolean nTips;

	@Parameter(label = MultiTreeStatistics.N_BRANCHES)
	private boolean nBranches;

	@Parameter(label = MultiTreeStatistics.N_PRIMARY_BRANCHES)
	private boolean nPrimaryBranches;

	@Parameter(label = MultiTreeStatistics.N_INNER_BRANCHES)
	private boolean nInnerBranches;

	@Parameter(label = MultiTreeStatistics.N_TERMINAL_BRANCHES)
	private boolean nTerminalBranches;

	@Parameter(label = MultiTreeStatistics.STRAHLER_NUMBER)
	private boolean sNumber;

	@Parameter(label = MultiTreeStatistics.STRAHLER_RATIO)
	private boolean sRatio;

	@Parameter(label = MultiTreeStatistics.WIDTH)
	private boolean width;

	@Parameter(label = MultiTreeStatistics.HEIGHT)
	private boolean height;

	@Parameter(label = MultiTreeStatistics.DEPTH)
	private boolean depth;

	@Parameter(label = MultiTreeStatistics.MEAN_RADIUS)
	private boolean meanRadius;

	// III. Options
	@Parameter(label = "<HTML>&nbsp;<br><b>Options:", persist = false, required = false, visibility = ItemVisibility.MESSAGE)
	private String SPACER3;

	@Parameter(label = "Action", choices = {"Choose", "Select All", "Select None"}, callback="actionChoiceSelected")
	private String actionChoice;

	@Parameter(label = "Distinguish compartments", description="<HTML><div WIDTH=500>Whether measurements "
			+ "should be grouped by cellular compartment (e.g., \"axon\", \"dendrites\", etc.)")
	private boolean splitByType;

	@Parameter(label = "<HTML>&nbsp;<br", persist = false, required = false, visibility = ItemVisibility.MESSAGE)
	private String fittingSpacer;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
			label = "NB: Some metrics may not be", 
			description = "<HTML><div WIDTH=500>"
					+ "Some branch-based metrics may not be available if e.g., you fitted paths while "
					+ "retaining original path coordinates. In such cases, it is recommended to fit "
					+ "paths using the 'Replace existing nodes' option")
	private String fittingMsg = "retrievable!";

	@Parameter(required = false)
	private Tree tree;

	@Parameter(required = false)
	private Collection<Tree> trees;

	@Parameter(required = false)
	private DefaultGenericTable table;

	@Parameter(required = false, visibility = ItemVisibility.INVISIBLE, persist = false)
	private boolean calledFromPathManagerUI;

	@SuppressWarnings("unused")
	private void init() {
		super.init(false);
		if (tree != null || trees != null) {
			resolveInput("SPACER1");
			getInfo().getMutableInput("SPACER1", String.class).setLabel("");
			resolveInput("file");
			getInfo().getMutableInput("file", File.class).setLabel("");
			resolveInput("pattern");
			getInfo().getMutableInput("pattern", String.class).setLabel("");
		}
		if (tree == null && trees == null) {
			// caller specified nothing: user will be prompted for file/dir
			resolveInput("tree");
			resolveInput("trees");
		} else if (tree != null) {
			// caller specified a single tree
			resolveInput("trees");
		} else if (trees != null) {
			// caller specified a collection of trees
			resolveInput("tree");
		}
		if (table == null) {
			table = (sntService.isActive()) ? sntService.getTable() : new DefaultGenericTable();
			if (table == null) table = new DefaultGenericTable();
		}
		resolveInput("table");
		if (!calledFromPathManagerUI) {
			resolveInput("fittingSpacer");
			resolveInput("fittingMsg");
		}
		resolveInput("calledFromPathManagerUI");
	}

	@SuppressWarnings("unused")
	private void actionChoiceSelected() {
		if (actionChoice.contains("All")) {
			setAllCheckboxesEnabled(true);
		} else if (actionChoice.contains("None")) {
			setAllCheckboxesEnabled(false);
		}
	}

	private void setAllCheckboxesEnabled(final boolean enable) {
		avgBranchLength = enable;
		avgContraction = enable;
		avgFragmentation = enable;
		avgRemoteAngle = enable;
		avgPartitionAsymmetry = enable;
		avgFractalDimension = enable;
		cableLength = enable;
		depth = enable;
		height = enable;
		meanRadius = enable;
		nBPs = enable;
		nBranches = enable;
		nPrimaryBranches = enable;
		nInnerBranches = enable;
		nTerminalBranches = enable;
		nTips = enable;
		primaryLength = enable;
		innerLength = enable;
		sNumber = enable;
		sRatio = enable;
		terminalLength = enable;
		width = enable;
		actionChoice = "Choose";
	}

	@Override
	public void run() {

		final List<String> metrics = new ArrayList<>();
		if (avgBranchLength) metrics.add(MultiTreeStatistics.AVG_BRANCH_LENGTH);
		if (avgContraction) metrics.add(MultiTreeStatistics.AVG_CONTRACTION);
		if (avgFragmentation) metrics.add(MultiTreeStatistics.AVG_FRAGMENTATION);
		if(avgRemoteAngle) metrics.add(MultiTreeStatistics.AVG_REMOTE_ANGLE);
		if(avgPartitionAsymmetry) metrics.add(MultiTreeStatistics.AVG_PARTITION_ASYMMETRY);
		if(avgFractalDimension) metrics.add(MultiTreeStatistics.AVG_FRACTAL_DIMENSION);
		if(cableLength) metrics.add(MultiTreeStatistics.LENGTH);
		if(terminalLength) metrics.add(MultiTreeStatistics.TERMINAL_LENGTH);
		if(primaryLength) metrics.add(MultiTreeStatistics.PRIMARY_LENGTH);
		if(innerLength) metrics.add(MultiTreeStatistics.INNER_LENGTH);
		if(nBPs) metrics.add(MultiTreeStatistics.N_BRANCH_POINTS);
		if(nTips) metrics.add(MultiTreeStatistics.N_TIPS);
		if(nBranches) metrics.add(MultiTreeStatistics.N_BRANCHES);
		if(nPrimaryBranches) metrics.add(MultiTreeStatistics.N_PRIMARY_BRANCHES);
		if(nInnerBranches) metrics.add(MultiTreeStatistics.N_INNER_BRANCHES);
		if(nTerminalBranches) metrics.add(MultiTreeStatistics.N_TERMINAL_BRANCHES);
		if(sNumber) metrics.add(MultiTreeStatistics.STRAHLER_NUMBER);
		if(sRatio) metrics.add(MultiTreeStatistics.STRAHLER_RATIO);
		if(width) metrics.add(MultiTreeStatistics.WIDTH);
		if(height) metrics.add(MultiTreeStatistics.HEIGHT);
		if(depth) metrics.add(MultiTreeStatistics.DEPTH);
		if(meanRadius) metrics.add(MultiTreeStatistics.MEAN_RADIUS);
	
		if (metrics.isEmpty()) {
			error("No metrics chosen.");
			return;
		}

		if (tree != null) {
			trees = Collections.singletonList(tree);
		}

		if (trees != null) {
			measure(trees, metrics);
			return;
		}

		if (file == null || !file.exists() || !file.canRead()) {
			error("Specified path is not valid.");
			return;
		}

		if (file.isDirectory()) {
			trees = Tree.listFromDir(file.getAbsolutePath(), pattern);
		} else if (validFile(file)) {
			trees = new ArrayList<>();
			final Tree tree = Tree.fromFile(file.getAbsolutePath());
			if (tree != null) trees.add(tree);
		}
		if (trees.isEmpty()) {
			error("No reconstruction file(s) could be retrieved from the specified path.");
			return;
		}
		measure(trees, metrics);
	}

	private boolean validFile(final File file) {
		return (pattern == null || pattern.isEmpty()) ? true : file.getName().contains(pattern);
	}

	private void measure(final Collection<Tree> trees, final List<String> metrics) {
		try {
			final int n = trees.size();
			final Iterator<Tree> it = trees.iterator();
			int index = 0;
			while (it.hasNext()) {
				final Tree tree = it.next();
				statusService.showStatus(index++, n, tree.getLabel());
				final TreeAnalyzer analyzer = new TreeAnalyzer(tree);
				analyzer.setContext(getContext());
				analyzer.setTable(table, TABLE_TITLE);
				analyzer.measure(metrics, splitByType); // will display table
			}
		} catch (final IllegalArgumentException | ArithmeticException | IllegalStateException ex) {
			error("An error occured while computing metric(s). See Console for details.");
			ex.printStackTrace();
		} finally {
			resetUI();
		}
	}


	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final SNTService sntService = ij.context().getService(SNTService.class);
		final Tree tree = sntService.demoTrees().get(0);
		final Map<String, Object> input = new HashMap<>();
		input.put("tree", tree);
		ij.command().run(AnalyzerCmd.class, true, (Map<String, Object>)null);
	}
}
