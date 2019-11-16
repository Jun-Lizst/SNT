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

package sc.fiji.snt.gui.cmds;

import net.imagej.ImageJ;

import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;

import sc.fiji.snt.SNT;
import sc.fiji.snt.SNTPrefs;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.gui.GuiUtils;
import sc.fiji.snt.plugin.CallLegacyShollPlugin;
import sc.fiji.snt.plugin.LocalThicknessCmd;
import sc.fiji.snt.plugin.PlotterCmd;
import sc.fiji.snt.plugin.ROIExporterCmd;
import sc.fiji.snt.plugin.ShollTracingsCmd;
import sc.fiji.snt.plugin.SkeletonizerCmd;
import sc.fiji.snt.plugin.StrahlerCmd;
import sc.fiji.snt.plugin.TreeMapperCmd;

/**
 * Command for (re)setting SNT Preferences.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, initializer="init", label="SNT Preferences")
public class PrefsCmd extends ContextCommand {

	@Parameter
	private UIService uiService;

	@Parameter
	private PrefService prefService;

	@Parameter
	protected SNTService sntService;

	@Parameter(label="Remember window locations", description="Whether position of dialogs should be preserved across restarts")
	private boolean persistentWinLoc;

	@Parameter(label="Use compression when saving traces", description="Wheter Gzip compression should be use when saving .traces files")
	private boolean compressTraces;

	@Parameter(label="No. parallel threads",
			description="<HTML><div WIDTH=400>The max. no. of parallel threads to be used by SNT, as specified in IJ's Edit>Options>Memory &amp; Threads...")
	private int nThreads;

	@Parameter(label="Reset All Preferences...", callback="reset")
	private Button reset;

	private SNT snt;

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		snt.getPrefs().setSaveWinLocations(persistentWinLoc);
		snt.getPrefs().setSaveCompressedTraces(compressTraces);
		snt.getPrefs().setThreads(nThreads);
	}

	private void init() {
		try {
			snt = sntService.getPlugin();
			persistentWinLoc = snt.getPrefs().isSaveWinLocations();
			compressTraces = snt.getPrefs().isSaveCompressedTraces();
			nThreads = snt.getPrefs().getThreads();
		} catch (final NullPointerException npe) {
			cancel("SNT is not running.");
		}
	}

	@SuppressWarnings("unused")
	private void reset() {
		final Result result = uiService.showDialog(
			"Reset preferences to defaults? (A restart may be required)",
			MessageType.QUESTION_MESSAGE);
		if (Result.YES_OPTION == result || Result.OK_OPTION == result) {
			clearAll();
			init(); // update prompt;
			uiService.showDialog("Preferences Reset. You should now restart"
					+ " SNT for changes to take effect.", "Restart Required");
		}
	}

	/** Clears all of SNT preferences. */
	protected void clearAll() {
		// gui.cmds
		prefService.clear(ChooseDatasetCmd.class);
		prefService.clear(ColorMapReconstructionCmd.class);
		prefService.clear(CompareFilesCmd.class);
		prefService.clear(ComputeSecondaryImg.class);
		prefService.clear(ComputeTubenessImg.class);
		prefService.clear(CustomizeObjCmd.class);
		prefService.clear(CustomizeTreeCmd.class);
		prefService.clear(DistributionCmd.class);
		prefService.clear(JSONImporterCmd.class);
		prefService.clear(LoadObjCmd.class);
		prefService.clear(LoadReconstructionCmd.class);
		prefService.clear(MLImporterCmd.class);
		prefService.clear(MultiSWCImporterCmd.class);
		prefService.clear(OpenDatasetCmd.class);
		prefService.clear(PathFitterCmd.class);
		prefService.clear(ReconstructionViewerCmd.class);
		prefService.clear(RecViewerPrefsCmd.class);
		prefService.clear(RemoteSWCImporterCmd.class);
		prefService.clear(ShowCorrespondencesCmd.class);
		prefService.clear(SNTLoaderCmd.class);
		prefService.clear(SWCTypeFilterCmd.class);
		prefService.clear(SWCTypeOptionsCmd.class);
		prefService.clear(TranslateReconstructionsCmd.class);

		// tracing.plugin
		prefService.clear(CallLegacyShollPlugin.class);
		prefService.clear(LocalThicknessCmd.class);
		prefService.clear(PlotterCmd.class);
		prefService.clear(ROIExporterCmd.class);
		prefService.clear(ShollTracingsCmd.class);
		prefService.clear(SkeletonizerCmd.class);
		prefService.clear(StrahlerCmd.class);
		prefService.clear(TreeMapperCmd.class);

		// Legacy (IJ1-based) preferences
		SNTPrefs.clearAll();
	}


	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(PrefsCmd.class, true);
	}

}
