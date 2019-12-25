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

package sc.fiji.snt.plugin;

import java.awt.Color;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import net.imagej.ImageJ;
import net.imagej.lut.LUTService;
import org.scijava.table.DefaultGenericTable;
import net.imglib2.display.ColorTable;

import org.scijava.Cancelable;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Interactive;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.thread.ThreadService;
import org.scijava.widget.Button;
import org.scijava.widget.NumberWidget;

import ij.ImagePlus;
import ij.gui.Overlay;
import sc.fiji.snt.SNT;
import sc.fiji.snt.analysis.TreeAnalyzer;
import sc.fiji.snt.analysis.TreeColorMapper;
import sholl.Logger;
import sholl.Profile;
import sholl.ProfileEntry;
import sholl.ShollUtils;
import sholl.UPoint;
import sholl.gui.Helper;
import sholl.gui.ShollOverlay;
import sholl.gui.ShollPlot;
import sholl.gui.ShollTable;
import sholl.math.LinearProfileStats;
import sholl.math.NormalizedProfileStats;
import sholl.math.ShollStats;
import sholl.plugin.Prefs;
import sc.fiji.snt.Path;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.sholl.TreeParser;
import sc.fiji.snt.gui.GuiUtils;
import sc.fiji.snt.util.PointInCanvas;
import sc.fiji.snt.util.PointInImage;

/**
 * Implements both the "Analyze:Sholl:Sholl Analysis (From Tracings)..." and
 * SNT's "Start Sholl Analysis..." commands
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, menu = { @Menu(label = "Analyze"), @Menu(
	label = "Sholl", weight = 0.01d), @Menu(
		label = "Sholl Analysis (From Tracings)...") }, initializer = "init")
public class ShollTracingsCmd extends DynamicCommand implements Interactive,
	Cancelable
{

	@Parameter
	private CommandService cmdService;
	@Parameter
	private DisplayService displayService;
	@Parameter
	private PrefService prefService;
	@Parameter
	private StatusService statusService;
	@Parameter
	private LUTService lutService;
	@Parameter
	private ThreadService threadService;

	/* constants */
	private static final String HEADER_HTML =
		"<html><body><div style='font-weight:bold;'>";

	/* Parameters */
	@Parameter(required = false, label = "File:",
		style = "extensions:eswc/swc/traces", callback = "fileChanged")
	private File file;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
		label = HEADER_HTML + "Sampling:")
	private String HEADER1;

	@Parameter(label = "Path filtering", required = false, choices = { "None",
		"Selected paths", "Paths tagged as 'Axon'", "Paths tagged as 'Dendrite'",
		"Paths tagged as 'Custom'", "Paths tagged as 'Undefined'" })
	private String filterChoice;

	@Parameter(label = "Center", required = false, choices = { "Soma",
		"Root node(s): Primary axon(s)",
		"Root node(s): Primary (basal) dendrites(s)",
		"Root node(s): Primary apical dendrites(s)",
		"Root node(s): All primary paths" }, callback = "centerChoiceChanged")
	private String centerChoice;

	@Parameter(label = "Radius step size", required = false, min = "0",
		callback = "stepSizeChanged")
	private double stepSize;

	@Parameter(label = "Preview", persist = false, callback = "overlayShells")
	private boolean previewShells;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
		label = HEADER_HTML + "<br>Metrics:")
	private String HEADER2;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
		label = "<html><i>Polynomial Fit:")
	private String HEADER2A;

	@Parameter(label = "Degree", callback = "polynomialChoiceChanged",
		required = false, choices = { "'Best fitting' degree",
			"None. Skip curve fitting", "Use degree specified below:" })
	private String polynomialChoice;

	@Parameter(label = "<html>&nbsp;", callback = "polynomialDegreeChanged",
		style = NumberWidget.SCROLL_BAR_STYLE)
	private int polynomialDegree;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
		label = "<html><i>Sholl Decay:")
	private String HEADER2B;

	@Parameter(label = "Method", choices = { "Automatically choose", "Semi-Log",
		"Log-log" })
	private String normalizationMethodDescription;

	@Parameter(label = "Normalizer", choices = { "Default", "Area/Volume",
		"Perimeter/Surface area", "Annulus/Spherical shell" },
		callback = "normalizerDescriptionChanged")
	private String normalizerDescription;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
		label = HEADER_HTML + "<br>Output:")
	private String HEADER3;

	@Parameter(label = "Plots", choices = { "Linear plot", "Normalized plot",
		"Linear & normalized plots", "None. Show no plots" })
	private String plotOutputDescription;

	@Parameter(label = "Tables", choices = { "Detailed table", "Summary table",
		"Detailed & Summary tables", "None. Show no tables" })
	private String tableOutputDescription;

	@Parameter(required = false, label = "Annotations", choices = { "None",
		"Color coded paths", "3D viewer labels image" })
	private String annotationsDescription;

	@Parameter(required = false, label = "Annotations LUT",
		callback = "lutChoiceChanged")
	private String lutChoice;

	@Parameter(required = false, label = "<html>&nbsp;")
	private ColorTable lutTable;

	@Parameter(label = "<html><b>Run Analysis", callback = "runAnalysis")
	private Button analyzeButton;

	@Parameter(label = " Options, Preferences and Resources... ",
		callback = "runOptions")
	private Button optionsButton;

	/* Parameters for SNT interaction */
	@Parameter(required = false, visibility = ItemVisibility.INVISIBLE)
	private SNT snt;

	@Parameter(required = false, visibility = ItemVisibility.INVISIBLE)
	private Tree tree;

	@Parameter(required = false, visibility = ItemVisibility.INVISIBLE)
	private PointInImage center;

	/* Instance variables */
	private Helper helper;
	private Logger logger;
	private AnalysisRunner analysisRunner;
	private Map<String, URL> luts;
	private Future<?> analysisFuture;
	private PreviewOverlay previewOverlay;
	private Profile profile;
	private DefaultGenericTable commonSummaryTable;
	private Display<?> detailedTableDisplay;

	/* Interactive runs: References to previous outputs */
	private ShollPlot lPlot;
	private ShollPlot nPlot;

	/* Preferences */
	private int minDegree;
	private int maxDegree;

	@Override
	public void run() {
		// Do nothing. Actually analysis is performed by runAnalysis();
	}

	/*
	 * Triggered every time user interacts with prompt (NB: buttons in the prompt
	 * are excluded from this
	 */
	@Override
	public void preview() {
		// do nothing. It is all implemented by callbacks
	}

	@Override
	public void cancel() {
		if (previewOverlay != null) previewOverlay.removeShellOverlay();
	}

	@SuppressWarnings("unused")
	private void runAnalysis() throws InterruptedException {
		if (analysisFuture != null && !analysisFuture.isDone()) {
			threadService.queue(() -> {
				final boolean killExisting = new GuiUtils((snt != null) ? snt.getUI()
					: null).getConfirmation("An analysis is already running. Abort it?",
						"Ongoing Analysis");
				if (killExisting) {
					analysisFuture.cancel(true);
					analysisRunner.showStatus("Aborted");
				}
			});
			return;
		}
		if (snt != null) {
			logger.info("Retrieving filtered paths... ");
			final Tree filteredTree = getFilteredTree();
			tree.setBoundingBox(snt.getPathAndFillManager().getBoundingBox(false));
			if (filteredTree == null || filteredTree.isEmpty()) {
				cancelAndFreezeUI(
					"Tracings do not seem to contain Paths matching the filtering criteria.",
					"Invalid Filter");
				return;
			}
			logger.info("Considering " + filteredTree.size() + " out of " + tree
				.size());
			analysisRunner = new AnalysisRunner(filteredTree, center);
		}
		else {
			if (tree == null) {
				cancelAndFreezeUI("File does not seem to be valid", "Invalid File");
				return;
			}
			analysisRunner = new AnalysisRunner(tree, centerChoice);
			if (analysisRunner.parser.getCenter() == null) {
				cancelAndFreezeUI(
					"File does not seem to contain any path matching the center criteria.",
					"Invalid Center");
				return;
			}
		}
		if (!validOutput()) {
			cancelAndFreezeUI("Analysis can only proceed if at least one type\n" +
				"of output (plot, table, annotation) is chosen.", "Invalid Output");
			return;
		}
		logger.info("Analysis started...");
		analysisFuture = threadService.run(analysisRunner);
	}

	private NormalizedProfileStats getNormalizedProfileStats(
		final Profile profile)
	{
		String normString = normalizerDescription.toLowerCase();
		if (normString.startsWith("default")) {
			normString = "Area/Volume";
		}
		if (tree.is3D()) {
			normString = normString.substring(normString.indexOf("/") + 1);
		}
		else {
			normString = normString.substring(0, normString.indexOf("/"));
		}
		final int normFlag = NormalizedProfileStats.getNormalizerFlag(normString);
		final int methodFlag = NormalizedProfileStats.getMethodFlag(
			normalizationMethodDescription);
		return new NormalizedProfileStats(profile, normFlag, methodFlag);
	}

	/* initializer method running before displaying prompt */
	protected void init() {
		helper = new Helper(context());
		logger = new Logger(context());
		final boolean calledFromSNT = snt != null;
		final boolean calledFromStandAloneRecViewer = snt == null && tree != null;

		// Adjust Path filtering choices
		final MutableModuleItem<String> mlitm =
			(MutableModuleItem<String>) getInfo().getInput("filterChoice",
				String.class);
		if (calledFromSNT) {
			previewOverlay = new PreviewOverlay(snt.getImagePlus(), center
				.getUnscaledPoint());
			logger.setDebug(SNTUtils.isDebugMode());
			setLUTs();
			resolveInput("file");
			resolveInput("centerChoice");
			final ArrayList<String> filteredchoices = new ArrayList<>(mlitm
				.getChoices());
			if (!filteredchoices.contains("Selected paths")) filteredchoices.add(1,
				"Selected paths");
			mlitm.setChoices(filteredchoices);
		}
		else {
			resolveInput("previewShells");
			resolveInput("annotationsDescription");
			resolveInput("lutChoice");
			resolveInput("lutTable");
			resolveInput("snt");
			resolveInput("center");
			resolveInput("centerUnscaled");
			if (calledFromStandAloneRecViewer) {
				resolveInput("file");
			}
			else {
				resolveInput("tree");
				final ArrayList<String> filteredchoices = new ArrayList<>();
				for (final String choice : mlitm.getChoices()) {
					if (!choice.equals("Selected paths")) filteredchoices.add(choice);
				}
				mlitm.setChoices(filteredchoices);
			}
		}
		readPreferences();
		getInfo().setLabel("Sholl Analysis " + ShollUtils.version());
		adjustFittingOptions();
	}

	private void setLUTs() {
		// see net.imagej.lut.LUTSelector
		luts = lutService.findLUTs();
		final ArrayList<String> choices = new ArrayList<>();
		for (final Map.Entry<String, URL> entry : luts.entrySet()) {
			choices.add(entry.getKey());
		}
		Collections.sort(choices);
		choices.add(0, "None");
		final MutableModuleItem<String> input = getInfo().getMutableInput(
			"lutChoice", String.class);
		input.setChoices(choices);
		input.setValue(this, lutChoice);
		lutChoiceChanged();
	}

	protected void lutChoiceChanged() {
		try {
			lutTable = lutService.loadLUT(luts.get(lutChoice));
		}
		catch (final Exception ignored) {
			// presumably "No Lut" was chosen
			lutTable = ShollUtils.constantLUT(Color.GREEN);
		}
		overlayShells();
	}

	private void readPreferences() {
		logger.debug("Reading preferences");
		minDegree = prefService.getInt(Prefs.class, "minDegree",
			Prefs.DEF_MIN_DEGREE);
		maxDegree = prefService.getInt(Prefs.class, "maxDegree",
			Prefs.DEF_MAX_DEGREE);
	}

	private void adjustFittingOptions() {
		final MutableModuleItem<Integer> polynomialDegreeInput = getInfo()
			.getMutableInput("polynomialDegree", Integer.class);
		polynomialDegreeInput.setMinimumValue(minDegree);
		polynomialDegreeInput.setMaximumValue(maxDegree);
	}

	private void cancelAndFreezeUI(final String msg, final String title) {
		cancel(title);
		helper.errorPrompt(msg, title);
	}

	private double adjustedStepSize() {
		return Math.max(stepSize, 0);
	}

	private boolean validOutput() {
		boolean noOutput = plotOutputDescription.contains("None");
//		noOutput = noOutput && tableOutputDescription.contains("None");
		if (snt != null) noOutput = noOutput && annotationsDescription.contains(
			"None");
		return !noOutput;
	}

	private Tree getFilteredTree() {
		final String choice = filterChoice.toLowerCase();
		if (choice.contains("none")) {
			return tree;
		}
		if (filterChoice.contains("selected")) {
			return new Tree(snt.getPathAndFillManager().getSelectedPaths());
		}
		boolean containsType = false;
		final Set<Integer> existingTypes = tree.getSWCTypes();
		final List<Integer> filteredTypes = new ArrayList<>();
		if (choice.contains("none")) {
			filteredTypes.addAll(Path.getSWCtypes());
			containsType = true;
		}
		else if (choice.contains("axon")) {
			filteredTypes.add(Path.SWC_AXON);
			containsType = existingTypes.contains(Path.SWC_AXON);
		}
		else if (choice.contains("dendrite")) {
			filteredTypes.add(Path.SWC_APICAL_DENDRITE);
			filteredTypes.add(Path.SWC_DENDRITE);
			containsType = existingTypes.contains(Path.SWC_APICAL_DENDRITE) ||
				existingTypes.contains(Path.SWC_DENDRITE);
		}
		else if (choice.contains("custom")) {
			filteredTypes.add(Path.SWC_CUSTOM);
			existingTypes.contains(Path.SWC_CUSTOM);
		}
		else if (choice.contains("undefined")) {
			filteredTypes.add(Path.SWC_UNDEFINED);
			existingTypes.contains(Path.SWC_UNDEFINED);
		}
		if (containsType) {
			return tree.subTree(filteredTypes.stream().mapToInt(Integer::intValue)
				.toArray());
		}
		return null;
	}

	/* callbacks */

	@SuppressWarnings("unused")
	private void fileChanged() {
		try {
			tree = new Tree(file.getAbsolutePath());
		}
		catch (final IllegalArgumentException | NullPointerException ex) {
			tree = null;
		}
	}

	@SuppressWarnings("unused")
	/* Callback for stepSize */
	private void stepSizeChanged() {
		stepSize = Math.max(0, stepSize);
		overlayShells();
		normalizerDescriptionChanged();
	}

	/* Callback for stepSize && previewShells */
	private void overlayShells() {
		threadService.run(previewOverlay);
	}

	@SuppressWarnings("unused")
	/* Callback for polynomialChoice */
	private void polynomialChoiceChanged() {
		if (!polynomialChoice.contains("specified")) {
			polynomialDegree = 0;
		}
		else if (polynomialDegree == 0) {
			polynomialDegree = (minDegree + maxDegree) / 2;
		}
	}

	/* Callback for stepSize && normalizerDescription */
	private void normalizerDescriptionChanged() {
		if (stepSize == 0 && (normalizerDescription.contains("Annulus") ||
			normalizerDescription.contains("shell")))
		{
			cancelAndFreezeUI(normalizerDescription +
				" normalization requires radius step size to be ≥ 0", null);
			normalizerDescription = "Default";
		}
	}

	@SuppressWarnings("unused")
	/* Callback for polynomialDegree */
	private void polynomialDegreeChanged() {
		if (polynomialDegree == 0) polynomialChoice = "'Best fitting' degree";
		else polynomialChoice = "Use degree specified below:";
	}

	@SuppressWarnings("unused")
	/* Callback for optionsButton */
	private void runOptions() {
		threadService.run(() -> {
			final Map<String, Object> input = new HashMap<>();
			input.put("ignoreBitmapOptions", true);
			cmdService.run(Prefs.class, true, input);
		});
	}

	private class AnalysisRunner implements Runnable {

		private final Tree tree;
		private final TreeParser parser;
		private LinearProfileStats lStats;
		private NormalizedProfileStats nStats;

		public AnalysisRunner(final Tree tree, final String centerChoice) {
			this.tree = tree;
			parser = new TreeParser(tree);
			setCenterFromChoice(centerChoice);
			parser.setStepSize(adjustedStepSize());
		}

		public AnalysisRunner(final Tree tree, final PointInImage center) {
			this.tree = tree;
			parser = new TreeParser(tree);
			parser.setCenter(center);
			parser.setStepSize(adjustedStepSize());
		}

		private void setCenterFromChoice(final String centerChoice) {
			final String choice = centerChoice.toLowerCase();
			try {
				if (choice.contains("all")) {
					parser.setCenter(TreeParser.PRIMARY_NODES_ANY);
				}
				else if (choice.contains("soma")) {
					parser.setCenter(TreeParser.PRIMARY_NODES_SOMA);
				}
				else if (choice.contains("axon")) {
					parser.setCenter(TreeParser.PRIMARY_NODES_AXON);
				}
				else if (choice.contains("apical")) {
					parser.setCenter(TreeParser.PRIMARY_NODES_APICAL_DENDRITE);
				}
				else if (choice.contains("dendrite")) {
					parser.setCenter(TreeParser.PRIMARY_NODES_DENDRITE);
				}
			}
			catch (IllegalArgumentException | NullPointerException ignored) {
				// do nothing. We'll check for this later
			}
		}

		@Override
		public void run() {
			// while (!Thread.currentThread().isInterrupted()) {
			runAnalysis();
			// }
		}

		public void runAnalysis() {
			showStatus("Obtaining profile...");
			parser.setStepSize(adjustedStepSize());
			parser.parse();
			profile = parser.getProfile();

			if (!parser.successful()) {
				cancelAndFreezeUI("No valid profile retrieved.", "Invalid Profile");
				showStatus("Sholl: No profile retrieved.");
				return;
			}

			// Linear profile stats
			lStats = new LinearProfileStats(profile);
			lStats.setLogger(logger);
			lStats.setPrimaryBranches(new TreeAnalyzer(tree).getPrimaryBranches()
				.size());

			if (polynomialChoice.contains("Best")) {
				showStatus("Computing 'Best Fit' Polynomial...");
				final int deg = lStats.findBestFit(minDegree, maxDegree, prefService);
				if (deg == -1) {
					helper.error(
						"Polynomial regression failed. You may need to adjust (or reset) the options for 'best fit' polynomial",
						null);
				}
			}
			else if (polynomialChoice.contains("degree") && polynomialDegree > 1) {
				showStatus("Fitting polynomial...");
				try {
					lStats.fitPolynomial(polynomialDegree);
				}
				catch (final Exception ignored) {
					helper.error("Polynomial regression failed. Unsuitable degree?",
						null);
				}
			}

			/// Normalized profile stats
			nStats = getNormalizedProfileStats(profile);
			logger.debug("Sholl decay: " + nStats.getShollDecay());

			// Set Plots
			showStatus("Preparing outputs...");
			if (plotOutputDescription.toLowerCase().contains("linear")) {
				lPlot = showOrRebuildPlot(lPlot, lStats);
			}
			if (plotOutputDescription.toLowerCase().contains("normalized")) {
				nPlot = showOrRebuildPlot(nPlot, nStats);
			}

			// Set tables
			if (tableOutputDescription.contains("Detailed")) {
				final ShollTable dTable = new ShollTable(lStats, nStats);
				dTable.listProfileEntries();
				if (detailedTableDisplay != null) {
					detailedTableDisplay.close();
				}
				detailedTableDisplay = displayService.createDisplay("Sholl-Profiles",
					dTable);
			}
			if (tableOutputDescription.contains("Summary")) {
				final ShollTable sTable = new ShollTable(lStats, nStats);
				if (commonSummaryTable == null) commonSummaryTable =
					new DefaultGenericTable();
				String header = "";
				if (snt == null) {
					header = file.getName();
				}
				else {
					header = "Analysis " + (commonSummaryTable.getRowCount() + 1);
					sTable.setContext(snt.getContext());
				}
				if (!filterChoice.contains("None")) header += "(" + filterChoice + ")";
				sTable.summarize(commonSummaryTable, header);
				updateAndDisplayCommonSummaryTable();
			}

			if (snt != null) {
				if (annotationsDescription.contains("labels image")) {
					showStatus("Creating labels image...");
					parser.getLabelsImage(snt.getImagePlus(), null).show();
				}
				if (annotationsDescription.contains("paths")) {
					showStatus("Color coding nodes...");
					final TreeColorMapper treeColorizer = new TreeColorMapper(snt
						.getContext());
					treeColorizer.map(tree, lStats, lutTable);
				}
				annotationsDescription = "None";
			}
			showStatus("Sholl Analysis concluded...");
		}

		private ShollPlot showOrRebuildPlot(ShollPlot plot,
			final ShollStats stats)
		{
			if (plot != null && plot.isVisible() && !plot.isFrozen()) {
				plot.rebuild(stats);
				showStatus("Plot updated...");
			}
			else {
				plot = new ShollPlot(stats);
				plot.getTitle();
				plot.show();
			}
			return plot;
		}

		private void updateAndDisplayCommonSummaryTable() {
			final String DISPLAY_NAME = "Sholl Metrics";
			final Display<?> display = displayService.getDisplay(DISPLAY_NAME);
			if (display != null && display.isDisplaying(commonSummaryTable)) {
				display.update();
			}
			else {
				displayService.createDisplay(DISPLAY_NAME, commonSummaryTable);
			}
		}

		private void showStatus(final String msg) {
			statusService.showStatus(msg);
		}
	}

	private class PreviewOverlay implements Runnable {

		private final ImagePlus imp;
		private final PointInCanvas centerUnscaled;
		private Overlay overlaySnapshot;
		private double endRadiusUnscaled;

		public PreviewOverlay(final ImagePlus imp,
			final PointInCanvas centerUnscaled)
		{
			this.imp = imp;
			this.centerUnscaled = centerUnscaled;
			overlaySnapshot = imp.getOverlay();
			if (overlaySnapshot == null) overlaySnapshot = new Overlay();
			// Shells will be drawn in pixel coordinates because the
			// image calibration is not aware of a Path's canvasOffset
			final PointInCanvas nw = new PointInCanvas(0, 0, 0);
			final PointInCanvas sw = new PointInCanvas(0, imp.getHeight(), 0);
			final PointInCanvas ne = new PointInCanvas(imp.getWidth(), 0, 0);
			final PointInCanvas se = new PointInCanvas(imp.getWidth(), imp
				.getHeight(), 0);
			endRadiusUnscaled = Math.max(centerUnscaled.distanceSquaredTo(nw),
				centerUnscaled.distanceSquaredTo(sw));
			endRadiusUnscaled = Math.max(endRadiusUnscaled, center.distanceSquaredTo(
				ne));
			endRadiusUnscaled = Math.sqrt(Math.max(endRadiusUnscaled, centerUnscaled
				.distanceSquaredTo(se)));
		}

		@Override
		public void run() {
			if (!previewShells) {
				removeShellOverlay();
				return;
			}
			if (imp == null) {
				helper.error("Image is not available. Cannot preview overlays.",
					"Image Not Available");
				previewShells = false;
			}
			if (centerUnscaled == null) {
				helper.error("Center position unknown. Cannot preview overlays.",
					"Center Not Available");
				previewShells = false;
			}
			try {
				final double unscaledStepSize = adjustedStepSize() / snt
					.getMinimumSeparation();
				final ArrayList<Double> radii = ShollUtils.getRadii(0, Math.max(
					unscaledStepSize, 1), endRadiusUnscaled);
				final Profile profile = new Profile();
				for (final double r : radii) {
					profile.add(new ProfileEntry(r, 0));
				}
				profile.setCenter(new UPoint(centerUnscaled.x, centerUnscaled.y,
					centerUnscaled.z));
				final ShollOverlay so = new ShollOverlay(profile);
				so.setShellsLUT(lutTable, ShollOverlay.RADIUS);
				so.addCenter();
				so.assignProperty("temp");
				imp.setOverlay(so.getOverlay());
			}
			catch (final IllegalArgumentException ignored) {
				return; // invalid parameters: do nothing
			}
		}

		public void removeShellOverlay() {
			if (imp == null || overlaySnapshot == null) {
				return;
			}
			ShollOverlay.remove(overlaySnapshot, "temp");
			imp.setOverlay(overlaySnapshot);
		}
	}

	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Map<String, Object> input = new HashMap<>();
		final CommandService cmdService = ij.command();
		cmdService.run(ShollTracingsCmd.class, true, input);
	}
}
