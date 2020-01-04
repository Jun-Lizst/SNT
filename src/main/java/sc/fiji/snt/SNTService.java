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

package sc.fiji.snt;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import net.imagej.ImageJService;
import org.scijava.table.DefaultGenericTable;
import org.scijava.util.FileUtils;
import org.scijava.Priority;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

import ij.IJ;
import ij.ImagePlus;
import ij.io.Opener;
import ij.plugin.ZProjector;
import ij.process.ColorProcessor;
import sc.fiji.snt.analysis.PathProfiler;
import sc.fiji.snt.analysis.TreeAnalyzer;
import sc.fiji.snt.viewer.Viewer3D;
import sc.fiji.snt.analysis.TreeStatistics;
import sc.fiji.snt.event.SNTEvent;
import sc.fiji.snt.hyperpanes.MultiDThreePanes;

/**
 * Service for accessing and scripting the active instance of
 * {@link SNT}.
 *
 * @author Tiago Ferreira
 * @author Kyle Harrington
 *
 */
@Plugin(type = Service.class, priority = Priority.NORMAL)
public class SNTService extends AbstractService implements ImageJService {

	@Parameter
	private ScriptService scriptService;

	@Parameter
	private LogService logService;

	private static SNT plugin;


	private void accessActiveInstance(final boolean createInstanceIfNull) {
		plugin = SNTUtils.getPluginInstance();
		if (createInstanceIfNull && plugin == null) {
			plugin = new SNT(getContext(), new PathAndFillManager());
		} else if (plugin == null) {
			throw new UnsupportedOperationException("SNT is not running");
		}
	}

	// @Override
	// public void initialize() {
	// scriptService.addAlias(this.getClass());
	// }

	/**
	 * Gets whether SNT is running.
	 *
	 * @return true if this {@code SNTService} is active, tied to the active
	 *         instance of SNT
	 */
	public boolean isActive() {
		return SNTUtils.getPluginInstance() != null;
	}

	/**
	 * Assigns pixel intensities at each Path node, storing them as Path values.
	 * Assigned intensities are those of the channel and time point currently being
	 * traced. Assumes SNT has been initialized with a valid image.
	 *
	 * @param selectedPathsOnly If true, only selected paths will be assigned
	 *                          values, otherwise voxel intensities will be assigned
	 *                          to all paths
	 * @throws UnsupportedOperationException if SNT is not running
	 * @throws IllegalArgumentException      If valid imaged data is not available
	 * @see PathProfiler
	 * @see Path#setNodeValues(double[])
	 */
	public void assignValues(final boolean selectedPathsOnly) throws UnsupportedOperationException, IllegalArgumentException{
		accessActiveInstance(false);
		if (!plugin.accessToValidImageData()) {
			throw new IllegalArgumentException("Valid image data is not available");
		}
		final PathProfiler profiler = new PathProfiler(getTree(selectedPathsOnly),
			plugin.getLoadedDataAsImp());
		profiler.assignValues();
	}

	/**
	 * Initializes SNT. Since no image is specified, tracing functions are disabled.
	 *
	 * @param startUI Whether SNT's UI should also be initialized;
	 * @return the SNT instance.
	 */
	public SNT initialize(final boolean startUI) {
		accessActiveInstance(true);
		if (startUI && plugin.getUI() == null) {
			plugin.initialize(true, 1, 1);
			plugin.startUI();
		}
		return plugin;
	}

	/**
	 * Initializes SNT.
	 *
	 * @param imagePath the image to be traced. If "demo" (case insensitive), SNT is
	 *                  initialized using the {@link #demoTreeImage}. If empty or
	 *                  null and SNT's UI is available an "Open" dialog prompt is
	 *                  displayed.
	 * @param startUI   Whether SNT's UI should also be initialized;
	 * @return the SNT instance.
	 */
	public SNT initialize(final String imagePath, final boolean startUI) {
		if ("demo".equalsIgnoreCase(imagePath)) {
			return initialize(demoTreeImage(), startUI);
		}
		if (imagePath == null || imagePath.isEmpty() && (!startUI || getUI() == null)) {
			throw new IllegalArgumentException("Invalid imagePath " + imagePath);
		}
		return initialize(IJ.openImage(imagePath), startUI);
	}

	/**
	 * Initializes SNT.
	 *
	 * @param imp the image to be traced (null not allowed)
	 * @param startUI Whether SNT's UI should also be initialized;
	 * @return the SNT instance.
	 */
	public SNT initialize(final ImagePlus imp, final boolean startUI) {
		if (plugin == null) {
			plugin = new SNT(getContext(), imp);
			plugin.initialize(true, 1, 1);
		} else {
			plugin.initialize(imp);
		}
		if (startUI && plugin.getUI() == null) plugin.startUI();
		return plugin;
	}

	/**
	 * Returns a reference to the active {@link SNT} plugin.
	 *
	 * @return the {@link SNT} instance
	 */
	public SNT getPlugin() {
		accessActiveInstance(true);
		return plugin;
	}

	/**
	 * Loads the specified tracings file.
	 *
	 * @param filePath either a "SWC", "TRACES" or "JSON" file. Null not allowed.
	 * @throws UnsupportedOperationException if SNT is not running
	 */
	public void loadTracings(final String filePath) {
		accessActiveInstance(false);
		plugin.loadTracings(new File(filePath));
	}

	/**
	 * Loads the specified tree.
	 *
	 * @param tree the {@link Tree} to be loaded (null not allowed)
	 * @throws UnsupportedOperationException if SNT is not running
	 */
	public void loadTree(final Tree tree) {
		accessActiveInstance(false);
		plugin.getPathAndFillManager().addTree(tree);
	}

	/**
	 * Saves all the existing paths to a file.
	 *
	 * @param filePath the saving output file path. If {@code filePath} ends in
	 *                 ".swc" (case insensitive), an SWC file is created, otherwise
	 *                 a "traces" file is created. If empty and a GUI exists, a save
	 *                 prompt is displayed.
	 * @return true, if paths exist and file was successfully written.
	 * @throws UnsupportedOperationException if SNT is not running
	 */
	public boolean save(final String filePath) {
		accessActiveInstance(false);
		if (getPathAndFillManager().size() == 0)
			return false;
		File saveFile;
		if (filePath == null || filePath.trim().isEmpty() && getUI() != null) {
			saveFile = getUI().saveFile("Save As Traces...", null, ".traces");
		} else {
			saveFile = new File(filePath);
		}
		if (saveFile == null)
			return false;
		final boolean asSWC = "swc".equalsIgnoreCase(FileUtils.getExtension(saveFile));
		if (getUI() != null) {
			if (asSWC) {
				return getUI().saveAllPathsToSwc(saveFile.getAbsolutePath());
			}
			getUI().saveToXML(saveFile);
		} else {
			if (asSWC) {
				return getPathAndFillManager().exportAllPathsAsSWC(SNTUtils.stripExtension(saveFile.getAbsolutePath()));
			}
			try {
				getPathAndFillManager().writeXML(saveFile.getAbsolutePath(),
						plugin.getPrefs().isSaveCompressedTraces());
			} catch (final IOException ioe) {
				ioe.printStackTrace();
				return false;
			}
		}
		return true;
	}

	/**
	 * Gets the paths currently selected in the Path Manager list.
	 *
	 * @return the paths currently selected, or null if no selection exists
	 * @throws UnsupportedOperationException if SNT is not running
	 * @see #getTree(boolean)
	 */
	public Collection<Path> getSelectedPaths() {
		accessActiveInstance(false);
		return plugin.getSelectedPaths();
	}

	/**
	 * Gets the paths currently listed in the Path Manager
	 *
	 * @return all the listed paths, or null if the Path Manager is empty
	 * @throws UnsupportedOperationException if SNT is not running
	 * @see #getTree(boolean)
	 */
	public List<Path> getPaths() {
		accessActiveInstance(false);
		return plugin.getPathAndFillManager().getPathsFiltered();
	}

	/**
	 * Gets the collection of paths listed in the Path Manager as a {@link Tree}
	 * object.
	 *
	 * @param selectedPathsOnly If true, only selected paths are retrieved
	 * @return the Tree holding the Path collection
	 * @throws UnsupportedOperationException if SNT is not running
	 */
	public Tree getTree(final boolean selectedPathsOnly) {
		final Tree tree = new Tree((selectedPathsOnly) ? getSelectedPaths()
			: getPaths());
		tree.setLabel((selectedPathsOnly) ? "Selected Paths" : "All Paths");
		return tree;
	}

	/**
	 * Returns a {@link TreeAnalyzer} instance constructed from current Paths.
	 *
	 * @param selectedPathsOnly If true only selected paths will be considered
	 * @return the TreeAnalyzer instance
	 * @throws UnsupportedOperationException if SNT is not running
	 */
	public TreeAnalyzer getAnalyzer(final boolean selectedPathsOnly) {
		accessActiveInstance(false);
		final TreeAnalyzer tAnalyzer = new TreeAnalyzer(getTree(selectedPathsOnly));
		tAnalyzer.setContext(getContext());
		tAnalyzer.setTable(getTable(), PathManagerUI.TABLE_TITLE);
		return tAnalyzer;
	}

	/**
	 * Returns a {@link TreeStatistics} instance constructed from current Paths.
	 *
	 * @param selectedPathsOnly If true only selected paths will be considered
	 * @return the TreeStatistics instance
	 * @throws UnsupportedOperationException if SNT is not running
	 */
	public TreeStatistics getStatistics(final boolean selectedPathsOnly) {
		final TreeStatistics tStats = new TreeStatistics(getTree(
			selectedPathsOnly));
		tStats.setContext(getContext());
		return tStats;
	}

	/**
	 * Returns the {@link PathAndFillManager} associated with the current SNT
	 * instance.
	 *
	 * @return the PathAndFillManager instance
	 * @throws UnsupportedOperationException if no SNT instance exists.
	 */
	public PathAndFillManager getPathAndFillManager() {
		accessActiveInstance(false);
		return plugin.getPathAndFillManager();
	}

	/**
	 * Script-friendly method for updating (refreshing) all viewers currently in use
	 * by SNT. Does nothing if no SNT instance exists.
	 */
	public void updateViewers() {
		accessActiveInstance(false);
		if (plugin != null) plugin.updateAllViewers();
	}

	/**
	 * Returns a reference to SNT's UI.
	 *
	 * @return the {@link SNTUI} window, or null if SNT is not running, or is
	 *         running without GUI
	 */
	public SNTUI getUI() {
		plugin = SNTUtils.getPluginInstance();
		return (plugin==null) ? null : plugin.getUI();
	}

	/**
	 * Returns a reference to SNT's Reconstruction Viewer.
	 *
	 * @return SNT's {@link Viewer3D} instance.
	 * @throws UnsupportedOperationException if SNT is not running
	 */
	public Viewer3D getRecViewer() {
		accessActiveInstance(false);
		if (getUI() != null) {
			return getUI().getReconstructionViewer(true);
		}
		final Viewer3D viewer = getInstanceViewer();
		if (viewer == null) {
			class SNTViewer3D extends Viewer3D {
				private SNTViewer3D() {
					super(plugin);
				}
				@Override
				public void dispose() {
					super.dispose();
					if (getUI() != null) getUI().setReconstructionViewer(null);
				}
			}
			return new SNTViewer3D();
		} else {
			return viewer;
		}
	}

	private Viewer3D getInstanceViewer() {
		final HashMap<Integer, Viewer3D> viewerMap = SNTUtils.getViewers();
		if (viewerMap == null || viewerMap.isEmpty()) {
			return null;
		}
		for (final Viewer3D viewer : viewerMap.values()) {
			if (viewer.isSNTInstance()) {
				return viewer;
			}
		}
		return null;
	}

	/**
	 * Returns a reference to an opened Reconstruction Viewer (standalone instance).	 *
	 *
	 * @param id the unique numeric ID of the Reconstruction Viewer to be retrieved
	 *           (as used by the "Script This Viewer" command, and typically
	 *           displayed in the Viewer's window title)
	 * @return The standalone {@link Viewer3D} instance, or null if id was not
	 *         recognized
	 */
	public Viewer3D getRecViewer(final int id) {
		final HashMap<Integer, Viewer3D> viewerMap = SNTUtils.getViewers();
		return (viewerMap == null || viewerMap.isEmpty()) ? null : viewerMap.get(id);
	}

	/**
	 * Instantiates a new standalone Reconstruction Viewer.
	 *
	 * @return The standalone {@link Viewer3D} instance
	 */
	public Viewer3D newRecViewer(final boolean guiControls) {
		return (guiControls) ? new Viewer3D(getContext()) : new Viewer3D();
	}

	public SciViewSNT getOrCreateSciViewSNT() {
		if (SNTUtils.getPluginInstance() == null) {
			return new SciViewSNT(getContext());
		}
		return getSciViewSNT();
	}

	public SciViewSNT getSciViewSNT() {
		accessActiveInstance(false);
		if (getUI() != null && getUI().sciViewSNT != null)
			return getUI().sciViewSNT;
		return new SciViewSNT(plugin);
	}

	/**
	 * Returns a reference to SNT's main table of measurements.
	 *
	 * @return SNT measurements table
	 * @throws UnsupportedOperationException if SNT is not running
	 */
	public DefaultGenericTable getTable() {
		accessActiveInstance(false);
		return (getUI() == null) ? null : getUI().getPathManager().getTable();
	}

	/**
	 * Returns a toy reconstruction (fractal tree).
	 *
	 * @return a reference to the loaded tree, or null if data could no be retrieved
	 * @see #demoTreeImage()
	 */
	public Tree demoTree() {
		return getResourceTree("tests/TreeV.swc");
	}

	private Tree getResourceTree(final String resourcePath) {
		final ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		final InputStream is = classloader.getResourceAsStream(resourcePath);
		final PathAndFillManager pafm = new PathAndFillManager();
		pafm.setHeadless(true);
		Tree tree;
		try {
			final int idx1stPath = pafm.size();
			final BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
			if (pafm.importSWC(br, "TreeV", false, 0, 0, 0, 1, 1, 1, false)) {
				tree = new Tree();
				for (int i = idx1stPath; i < pafm.size(); i++) {
					final Path p = pafm.getPath(i);
					tree.add(p);
				}
				tree.setLabel("TreeV");
			} else {
				return null;
			}
			br.close();
		} catch (final IOException e) {
			tree = null;
			SNTUtils.error("UnsupportedEncodingException", e);
		}
		return tree;
	}

	/**
	 * Returns the image associated with the demo (fractal) tree.
	 *
	 * @return a reference to the image tree, or null if data could no be retrieved
	 * @see #demoTree()
	 */
	public ImagePlus demoTreeImage() {
		final ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		final InputStream is = classloader.getResourceAsStream("tests/TreeV.tif");
		final boolean redirecting = IJ.redirectingErrorMessages();
		IJ.redirectErrorMessages(true);
		final ImagePlus imp = new Opener().openTiff(is, "TreeV.tif");
		IJ.redirectErrorMessages(redirecting);
		return imp;
	}

	/**
	 * Returns a collection of four demo reconstructions (dendrites from pyramidal
	 * cells from the MouseLight database). NB: Data is cached locally. No internet
	 * connection required.
	 *
	 * @return the list of {@link Tree}s, corresponding to the dendritic arbors of
	 *         cells "AA0001", "AA0002", "AA0003", "AA0004" (MouseLight database
	 *         IDs).
	 */
	public List<Tree> demoTrees() {
		final String[] cells = {"AA0001", "AA0002", "AA0003", "AA0004"};
		final List<Tree> trees = new ArrayList<>();
		for (final String cell : cells) {
			final Tree tree = getResourceTree("ml/demo-trees/" + cell + ".swc");
			if (tree != null) tree.setLabel(cell);
			trees.add(tree);
		}
		return trees;
	}

	/**
	 * Retrieves a WYSIWYG 'snapshot' of a tracing canvas.
	 *
	 * @param view A case-insensitive string specifying the canvas to be captured.
	 *          Either "xy" (or "main"), "xz", "zy" or "3d" (for legacy's 3D
	 *          Viewer).
	 * @param project whether the snapshot of 3D image stacks should include its
	 *          projection (MIP), or just the current plane
	 * @return the snapshot capture of the canvas as an RGB image
	 * @throws UnsupportedOperationException if SNT is not running
	 * @throws IllegalArgumentException if view is not a recognized option
	 */
	@SuppressWarnings("deprecation")
	public ImagePlus captureView(final String view, final boolean project) {
		accessActiveInstance(false);
		if (view == null || view.trim().isEmpty())
			throw new IllegalArgumentException("Invalid view");

		if (view.toLowerCase().contains("3d")) {
			if (plugin.get3DUniverse() == null) throw new IllegalArgumentException(
				"view is not available");
			return plugin.get3DUniverse().takeSnapshot();
		}

		final int viewPlane = getView(view);
		final ImagePlus imp = plugin.getImagePlus(viewPlane);
		if (imp == null) throw new IllegalArgumentException(
			"view is not available");

		final ImagePlus proj = ZProjector.run(imp, "max", (project) ? 1 : imp
			.getZ(), (project) ? imp.getNSlices() : imp.getZ()).flatten();
		// NB: overlay will be flatten but not active ROI
		final TracerCanvas canvas = new TracerCanvas(proj, plugin, viewPlane, plugin
			.getPathAndFillManager());
		final BufferedImage bi = new BufferedImage(proj.getWidth(), proj
			.getHeight(), BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = canvas.getGraphics2D(bi.getGraphics());
		g.drawImage(proj.getImage(), 0, 0, null);
		for (final Path p : getPaths()) {
			p.drawPathAsPoints(g, canvas, plugin);
		}
		// this is taken from ImagePlus.flatten()
		final ImagePlus result = new ImagePlus(view + " view snapshot",
			new ColorProcessor(bi));
		result.copyScale(proj);
		result.setProperty("Info", proj.getProperty("Info"));
		return result;
	}

	private int getView(final String view) {
		switch (view.toLowerCase()) {
			case "xy":
			case "main":
				return MultiDThreePanes.XY_PLANE;
			case "xz":
				return MultiDThreePanes.XZ_PLANE;
			case "zy":
				return MultiDThreePanes.ZY_PLANE;
			default:
				throw new IllegalArgumentException("Unrecognized view");
		}
	}

	/**
	 * Quits SNT. Does nothing if SNT is currently not running.
	 */
	@Override
	public void dispose() {
		if (plugin == null) return;
		if (getUI() == null) {
			SNTUtils.log("Disposing resources..");
			plugin.cancelSearch(true);
			plugin.notifyListeners(new SNTEvent(SNTEvent.QUIT));
			plugin.prefs.savePluginPrefs(true);
			if (getInstanceViewer() != null) getRecViewer().dispose();
			plugin.closeAndResetAllPanes();
			if (plugin.getImagePlus() != null) plugin.getImagePlus().close();
			SNTUtils.setPlugin(null);
			plugin = null;
		} else {
			getUI().exitRequested();
		}
	}

}
