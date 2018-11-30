/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2018 Fiji developers.
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

package tracing.gui.cmds;

import java.io.File;

import net.imagej.ImageJ;
import net.imagej.table.DefaultGenericTable;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;

import tracing.Tree;
import tracing.analysis.TreeStatistics;
import tracing.gui.GuiUtils;
import tracing.plot.TreePlot3D;

/**
 * Command for opening two SWC/traces files in a dedicated Reconstruction
 * Viewer.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false,
	label = "Compare Two Reconstructions")
public class CompareFilesCmd extends ContextCommand {

	@Parameter(label = "File 1", required = true)
	private File file1;

	@Parameter(label = "Color")
	private ColorRGB color1 = Colors.GREEN;

	@Parameter(label = "File 2", required = true)
	private File file2;

	@Parameter(label = "Color")
	private ColorRGB color2 = Colors.MAGENTA;

	@Parameter(label = "File Comparison", type = ItemIO.OUTPUT)
	private DefaultGenericTable report;

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			final Tree tree1 = new Tree(file1.getAbsolutePath());
			final Tree tree2 = new Tree(file2.getAbsolutePath());
			final TreePlot3D plot3d = new TreePlot3D();
			tree1.setColor(color1);
			// plot3d.setDefaultColor(color1);
			plot3d.add(tree1);
			tree2.setColor(color2);
			// plot3d.setDefaultColor(color2);
			plot3d.add(tree2);
			plot3d.show().setTitle(file1.getName() + " vs " + file2.getName());
			report = makeReport(tree1, tree2);

		}
		catch (final IllegalArgumentException ex) {
			cancel("<HTML>An error occured: " + ex.getMessage());
		}
	}

	private DefaultGenericTable makeReport(final Tree tree1, final Tree tree2) {
		final DefaultGenericTable table = new DefaultGenericTable();
		for (final Tree tree : new Tree[] { tree1, tree2 }) {
			final TreeStatistics tStats = new TreeStatistics(tree);
			tStats.setTable(table);
			tStats.summarize(tree.getLabel(), false);
		}
		return table;
	}

	/* IDE debug method **/
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(CompareFilesCmd.class, true);
	}

}
