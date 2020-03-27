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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.scijava.Context;
import org.scijava.util.ColorRGB;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import sc.fiji.snt.analysis.TreeAnalyzer;
import sc.fiji.snt.analysis.graph.DirectedWeightedGraph;
import sc.fiji.snt.hyperpanes.MultiDThreePanes;
import sc.fiji.snt.util.BoundingBox;
import sc.fiji.snt.util.PointInCanvas;
import sc.fiji.snt.util.PointInImage;
import sc.fiji.snt.util.SWCPoint;
import sc.fiji.snt.viewer.Viewer2D;
import sc.fiji.snt.viewer.Viewer3D;

/**
 * Utility class to access a Collection of Paths (typically a complete
 * reconstruction) . A Tree is the preferred way to group, access and manipulate
 * {@link Path}s that share something in common, specially when scripting SNT.
 * Note that a "Tree" here is literally a collection of {@link Path}s. Very few
 * restrictions are imposed on its topology, although it is generally assumed
 * that the Collection of paths describes a single-rooted structure with no
 * loops.
 *
 * @author Tiago Ferreira
 */
public class Tree {

	public static final int X_AXIS = 1;
	public static final int Y_AXIS = 2;
	public static final int Z_AXIS = 4;

	private ArrayList<Path> tree;
	private String label;
	private TreeBoundingBox box;
	private PathAndFillManager pafm;
	private double value;

	/**
	 * Instantiates a new empty Tree.
	 */
	public Tree() {
		tree = new ArrayList<>();
	}

	/**
	 * Instantiates a new Tree from a set of paths.
	 *
	 * @param paths the Collection of paths forming this tree. Null not allowed.
	 *          Note that when a Path has been fitted and
	 *          {@link Path#getUseFitted()} is true, its fitted 'flavor' is used.
	 */
	public Tree(final Collection<Path> paths) {
		if (paths == null) throw new IllegalArgumentException(
			"Cannot instantiate a new tree from a null collection");
		tree = new ArrayList<>(paths.size());
		for (final Path p : paths) {
			if (p == null) continue;
			Path pathToAdd;
			// If fitted flavor of path exists use it instead
			if (p.getUseFitted() && p.getFitted() != null) {
				pathToAdd = p.getFitted();
			}
			else {
				pathToAdd = p;
			}
			tree.add(pathToAdd);
		}
	}

	/**
	 * Instantiates a Tree from a collection of reconstruction nodes.
	 *
	 * @param nodes the collection of reconstruction nodes. Nodes will be sorted by
	 *              id and any duplicate entries pruned.
	 * @param label the identifying label for this Tree.
	 */
	public Tree(final Collection<SWCPoint> nodes, final String label) {
		pafm = PathAndFillManager.createFromNodes(nodes);
		tree = (pafm == null) ? new ArrayList<>() : pafm.getPaths();
		setLabel(label);
	}

	/**
	 * Instantiates a new tree from a SWC, TRACES or JSON file.
	 *
	 * @param filename the absolute file path of the imported file
	 * @throws IllegalArgumentException if file path is not valid
	 */
	public Tree(final String filename) throws IllegalArgumentException {
		final File f = new File(filename);
		if (!f.exists()) throw new IllegalArgumentException(
			"File does not exist: " + filename);
		pafm = PathAndFillManager.createFromFile(filename);
		if (pafm == null) throw new IllegalArgumentException(
			"No paths extracted from " + filename + " Invalid file?");
		tree = pafm.getPaths();
		setLabel(SNTUtils.stripExtension(f.getName()));
	}

	/**
	 * Instantiates a new tree from a filtered SWC, TRACES or JSON file.
	 *
	 * @param filename the absolute file path of the imported file
	 * @param swcTypes only paths matching the specified SWC type(s) (e.g.,
	 *          {@link Path#SWC_AXON}, {@link Path#SWC_DENDRITE}, etc.) will be
	 *          imported
	 */
	public Tree(final String filename, final int... swcTypes) {
		this(filename);
		tree = subTree(swcTypes).list();
	}

	/**
	 * Adds a new Path to this Tree.
	 *
	 * @param p the Path to be added
	 * @return true, if Path successful added
	 */
	public boolean add(final Path p) {
		return tree.add(p);
	}

	/**
	 * Appends all paths of a specified {@link Tree} to this one.
	 *
	 * @param tree the Tree to be merged
	 * @return true if this Tree changed as a result of the merge
	 */
	public boolean merge(final Tree tree) {
		setLabel(((label == null) ? "" : label) + " " + tree.getLabel());
		return this.tree.addAll(tree.list());
	}

	// /**
	// * Returns a copy of this Tree.
	// *
	// * @return a copy of this Tree.
	// */
	// public Tree duplicate() {
	// return new Tree(new HashSet<Path>(tree));
	// }

	/**
	 * Replaces all Paths in this Tree.
	 *
	 * @param paths the replacing Paths
	 */
	public void replaceAll(final List<Path> paths) {
		tree = new ArrayList<>(paths);
	}

	/**
	 * Returns the Path at the specified position.
	 *
	 * @param index index of the element to return
	 * @return the element at the specified position
	 */
	public Path get(final int index) {
		return tree.get(index);
	}

	/**
	 * Returns the index of the specified Path in this Tree.
	 *
	 * @param path the Path to be searched for
	 * @return the path index, or -1 if it was not found
	 */
	public int indexOf(final Path path) {
		return tree.indexOf(path);
	}

	/**
	 * Removes a path from this tree.
	 *
	 * @param p the Path to be removed
	 * @return true if this tree contained p
	 */
	public boolean remove(final Path p) {
		return tree.remove(p);
	}

	/**
	 * Gets all the paths from this tree.
	 *
	 * @return the paths forming this tree
	 */
	public ArrayList<Path> list() {
		return tree;
	}

	/**
	 * Checks if this Tree is empty.
	 *
	 * @return true if this tree contains no Paths, false otherwise
	 */
	public boolean isEmpty() {
		return tree.isEmpty();
	}

	/**
	 * Checks if the nodes of this Tree have been assigned {@link BrainAnnotation}s
	 * (neuropil labels).
	 *
	 * @return true if at least one node in the Tree has a valid annotation, false
	 *         otherwise
	 */
	public boolean isAnnotated() {
		for (final Path p : tree) {
			for (int i = 0; i < p.size(); ++i) {
				if (p.getNodeAnnotation(i) != null)
					return true;
			}
		}
		return false;
	}

	/**
	 * Downsamples the tree.
	 *
	 * @param maximumAllowedDeviation the maximum allowed distance between 'shaft'
	 *          path nodes. Note that 1) upsampling is not supported, and 2) the
	 *          position of nodes at branch points and tips remains unaltered
	 *          during downsampling
	 * @see PathDownsampler
	 */
	public void downSample(final double maximumAllowedDeviation) {
		tree.parallelStream().forEach(p -> {
			p.downsample(maximumAllowedDeviation);
		});
	}

	/**
	 * Extracts the subset of paths matching the specified criteria (script friendly
	 * method)
	 *
	 * @param swcTypes SWC type(s) a string with at least 2 characters describing
	 *                 the SWC type allowed in the subtree (e.g., 'soma', 'axn', or
	 *                 'dendrite')
	 * @return the subset of paths matching the filtering criteria, or an empty Tree
	 *         if no hits were retrieved
	 */
	public Tree subTree(final String... swcTypes) {
		final Set<Integer> types = new HashSet<>();
		for (int i = 0; i < swcTypes.length; i++) {
			switch (swcTypes[i].toLowerCase().substring(0, 2)) {
			case "ax":
				types.add(Path.SWC_AXON);
				break;
			case "so":
				types.add(Path.SWC_SOMA);
				break;
			case "ap":
				types.add(Path.SWC_APICAL_DENDRITE);
				break;
			case "ba":
			case "(b":
				types.add(Path.SWC_DENDRITE);
				break;
			case "de":
				types.add(Path.SWC_APICAL_DENDRITE);
				types.add(Path.SWC_DENDRITE);
			default:
				types.add(Path.SWC_UNDEFINED);
				break;
			}
		}
		final Tree subTree = subTree(types.stream().mapToInt(Integer::intValue).toArray());
		if (getLabel() != null) 
			subTree.setLabel(getLabel() + " " + Arrays.toString(swcTypes));
		return subTree;
	}

	/**
	 * Extracts the subset of paths matching the specified criteria.
	 *
	 * @param swcTypes SWC type(s) (e.g., {@link Path#SWC_AXON},
	 *          {@link Path#SWC_DENDRITE}, etc.) allowed in the subtree
	 * @return the subset of paths matching the filtering criteria, or an empty
	 *         Tree if no hits were retrieved
	 */
	public Tree subTree(final int... swcTypes) {
		final Tree subtree = new Tree();
		Iterator<Path> it = tree.iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final boolean filteredType = Arrays.stream(swcTypes).anyMatch(t -> t == p
				.getSWCType());
			if (filteredType) subtree.add(p);
		}
		it = subtree.tree.iterator();
		while (it.hasNext()) {
			final Path p = it.next();
			final Iterator<Path> joinsIt = p.somehowJoins.iterator();
			while (joinsIt.hasNext()) {
				final Path joins = joinsIt.next();
				final boolean filteredType = Arrays.stream(swcTypes).anyMatch(t -> t == joins.getSWCType());
				if (!filteredType)
					joinsIt.remove();
			}
		}
		if (getLabel() != null) subtree.setLabel(getLabel() + " (filtered)");
		return subtree;
	}

	/**
	 * Assigns an SWC type label to all the Paths in this Tree.
	 *
	 * @param type the SWC type (e.g., {@link Path#SWC_AXON},
	 *          {@link Path#SWC_DENDRITE}, etc.)
	 */
	public void setType(final int type) {
		tree.forEach(p -> p.setSWCType(type));
	}

	/**
	 * Assigns an SWC type label to all the Paths in this Tree.
	 *
	 * @param type the SWC type (e.g., "soma", "axon", "(basal) dendrite", "apical
	 *          dendrite", etc.)
	 */
	public void setSWCType(final String type) {
		String inputType = (type == null) ? Path.SWC_UNDEFINED_LABEL : type.trim()
			.toLowerCase();
		switch (inputType) {
			case "apical dendrite":
			case "(apical) dendrite":
				inputType = Path.SWC_APICAL_DENDRITE_LABEL;
				break;
			case "basal dendrite":
			case "(basal) dendrite":
			case "dendrite":
			case "dend":
				inputType = Path.SWC_DENDRITE_LABEL;
				break;
			case "":
			case "none":
			case "unknown":
			case "undefined":
			case "undef":
				inputType = Path.SWC_UNDEFINED_LABEL;
				break;
			case "custom":
				inputType = Path.SWC_CUSTOM_LABEL;
				break;
			default:
				break; // keep input
		}
		final int labelIdx = Path.getSWCtypeNames().indexOf(type);
		if (labelIdx == -1) throw new IllegalArgumentException(
			"Unrecognized SWC-type label:" + type);
		final int intType = Path.getSWCtypes().get(labelIdx);
		tree.forEach(p -> p.setSWCType(intType));
	}

	/**
	 * Extracts the SWC-type flags present in this Tree.
	 *
	 * @return the set of SWC type(s) (e.g., {@link Path#SWC_AXON},
	 *         {@link Path#SWC_DENDRITE}, etc.) present in the tree
	 */
	public Set<Integer> getSWCTypes() {
		final HashSet<Integer> types = new HashSet<>();
		final Iterator<Path> it = tree.iterator();
		while (it.hasNext()) {
			types.add(it.next().getSWCType());
		}
		return types;
	}

	public Viewer3D show3D() {
		final Viewer3D viewer = new Viewer3D();
		viewer.addTree(this);
		if (getLabel() != null) {
			final java.awt.Frame frame = viewer.show();
			frame.setTitle(frame.getTitle().replace("Reconstruction Viewer", getLabel()));
		}
		return viewer;
	}

	public Viewer2D show2D() {
		final Viewer2D viewer = new Viewer2D(new Context());
		viewer.add(this);
		if (getLabel() != null) {
			viewer.setTitle(getLabel());
		}
		viewer.show();
		return viewer;
	}

	public void show() {
		if (is3D())
			show3D();
		else 
			show2D();
	}

	/**
	 * Gets the list of all nodes tagged as {@link Path#SWC_SOMA}.
	 *
	 * @return the soma nodes or null if no Paths are tagged as soma.
	 * @see #getRoot()
	 */
	public List<PointInImage> getSomaNodes() {
		final List<PointInImage> points = new ArrayList<>();
		for (final Path p : tree) {
			if (p.isPrimary() && p.getSWCType() == Path.SWC_SOMA) {
				points.add(p.getNode(0));
			}
		}
		return (points.isEmpty()) ? null : points;
	}

	/**
	 * Gets the first node of the main primary path of this tree
	 *
	 * @return the root node, or null if the main primary path is undefined for this
	 *         tree.
	 */
	public PointInImage getRoot() {
		if (isEmpty()) return null;
		Path rootPath = get(0);
		if (!rootPath.isPrimary()) { // how would this ever happen?
			rootPath = null;
			for (final Path p : list()) {
				if (p.isPrimary()) {
					rootPath = p;
					break;
				}
			}
		}
		return (rootPath == null) ? null : rootPath.getNodeWithoutChecks(0);
	}

	/**
	 * Specifies the offset to be used when rendering this Tree in a
	 * {@link TracerCanvas}. Path coordinates remain unaltered.
	 *
	 * @param xOffset the x offset (in pixels)
	 * @param yOffset the y offset (in pixels)
	 * @param zOffset the z offset (in pixels)
	 */
	public void applyCanvasOffset(final double xOffset, final double yOffset,
		final double zOffset)
	{
		final PointInCanvas offset = new PointInCanvas(xOffset, yOffset, zOffset);
		tree.forEach(p -> p.setCanvasOffset(offset));
	}

	/**
	 * Translates the tree by the specified offset.
	 *
	 * @param xOffset the x offset
	 * @param yOffset the y offset
	 * @param zOffset the z offset
	 */
	public void translate(final double xOffset, final double yOffset,
		final double zOffset)
	{
		tree.forEach(p -> {
			for (int node = 0; node < p.size(); node++) {
				p.precise_x_positions[node] += xOffset;
				p.precise_y_positions[node] += yOffset;
				p.precise_z_positions[node] += zOffset;
			}
			if (p.startJoinsPoint != null) {
				final PointInImage sPim = p.startJoinsPoint;
				final Path sPath = p.startJoins;
				sPim.x += xOffset;
				sPim.y += yOffset;
				sPim.z += zOffset;
				p.unsetStartJoin();
				p.setStartJoin(sPath, sPim);
			}
			if (p.endJoinsPoint != null) {
				final PointInImage ePim = p.endJoinsPoint;
				final Path ePath = p.endJoins;
				ePim.x += xOffset;
				ePim.y += yOffset;
				ePim.z += zOffset;
				p.unsetEndJoin();
				p.setEndJoin(ePath, ePim);
			}
		});
		if (box != null) {
			box.origin().x += xOffset;
			box.origin().y += yOffset;
			box.origin().z += zOffset;
			box.originOpposite().x += xOffset;
			box.originOpposite().y += yOffset;
			box.originOpposite().z += zOffset;
		}
	}

	/**
	 * Scales the tree by the specified factors.
	 *
	 * @param xScale the scaling factor for x coordinates
	 * @param yScale the scaling factor for y coordinates
	 * @param zScale the scaling factor for z coordinates
	 */
	public void scale(final double xScale, final double yScale,
		final double zScale)
	{
		tree.forEach(p -> {
			for (int node = 0; node < p.size(); node++) {
				p.precise_x_positions[node] *= xScale;
				p.precise_y_positions[node] *= yScale;
				p.precise_z_positions[node] *= zScale;
			}
			if (p.startJoinsPoint != null) {
				final PointInImage sPim = p.startJoinsPoint;
				final Path sPath = p.startJoins;
				sPim.scale(xScale, yScale, zScale);
				p.unsetStartJoin();
				p.setStartJoin(sPath, sPim);
			}
			if (p.endJoinsPoint != null) {
				final PointInImage ePim = p.endJoinsPoint;
				final Path ePath = p.endJoins;
				ePim.scale(xScale, yScale, zScale);
				p.unsetEndJoin();
				p.setEndJoin(ePath, ePim);
			}
		});
		if (box != null) {
			box.origin().x *= xScale;
			box.origin().y *= yScale;
			box.origin().z *= zScale;
			box.originOpposite().x *= xScale;
			box.originOpposite().y *= yScale;
			box.originOpposite().z *= zScale;
		}
	}

	/**
	 * Scales the tree by the specified factors.
	 *
	 * @param xScale the scaling factor for x coordinates
	 * @param yScale the scaling factor for y coordinates
	 * @param zScale the scaling factor for z coordinates
	 * @param radiusScale the scaling factor for node radii.
	 */
	public void scale(final double xScale, final double yScale,
		final double zScale, final double radiusScale)
	{
		scale(xScale, yScale, zScale);
		tree.forEach(p -> {
			if (p.hasRadii()) {
				for (int node = 0; node < p.size(); node++) {
					p.radii[node] *= radiusScale;
				}
			}
		});
	}

	/**
	 * Rotates the tree.
	 *
	 * @param axis the rotation axis. Either {@link #X_AXIS}, {@link #Y_AXIS}, or
	 *          {@link #Z_AXIS}
	 * @param angle the rotation angle in degrees
	 */
	public void rotate(final int axis, final double angle) {
		// See http://www.petercollingridge.appspot.com/3D-tutorial
		if (Double.isNaN(angle)) throw new IllegalArgumentException(
			"Angle not valid");
		final double radAngle = Math.toRadians(angle);
		final double sin = Math.sin(radAngle);
		final double cos = Math.cos(radAngle);
		switch (axis) {
			case Z_AXIS:
				tree.forEach(p -> {
					for (int node = 0; node < p.size(); node++) {
						final PointInImage pim = p.getNodeWithoutChecks(node);
						final double x = pim.x * cos - pim.y * sin;
						final double y = pim.y * cos + pim.x * sin;
						p.moveNode(node, new PointInImage(x, y, pim.z));
					}
					if (p.startJoinsPoint != null) {
						final PointInImage sPim = p.startJoinsPoint;
						final Path sPath = p.startJoins;
						sPim.x = sPim.x * cos - sPim.y * sin;
						sPim.y = sPim.y * cos + sPim.x * sin;
						p.unsetStartJoin();
						p.setStartJoin(sPath, sPim);
					}
					if (p.endJoinsPoint != null) {
						final PointInImage ePim = p.endJoinsPoint;
						final Path ePath = p.endJoins;
						ePim.x = ePim.x * cos - ePim.y * sin;
						ePim.y = ePim.y * cos + ePim.x * sin;
						p.unsetEndJoin();
						p.setEndJoin(ePath, ePim);
					}
				});
				break;
			case Y_AXIS:
				tree.forEach(p -> {
					for (int node = 0; node < p.size(); node++) {
						final PointInImage pim = p.getNodeWithoutChecks(node);
						final double x = pim.x * cos - pim.z * sin;
						final double z = pim.z * cos + pim.x * sin;
						p.moveNode(node, new PointInImage(x, pim.y, z));
					}
					if (p.startJoinsPoint != null) {
						final PointInImage sPim = p.startJoinsPoint;
						final Path sPath = p.startJoins;
						sPim.x = sPim.x * cos - sPim.z * sin;
						sPim.y = sPim.z * cos + sPim.x * sin;
						p.unsetStartJoin();
						p.setStartJoin(sPath, sPim);
					}
					if (p.endJoinsPoint != null) {
						final PointInImage ePim = p.endJoinsPoint;
						final Path ePath = p.endJoins;
						ePim.x = ePim.x * cos - ePim.z * sin;
						ePim.y = ePim.z * cos + ePim.x * sin;
						p.unsetEndJoin();
						p.setEndJoin(ePath, ePim);
					}
				});
				break;
			case X_AXIS:
				tree.forEach(p -> {
					for (int node = 0; node < p.size(); node++) {
						final PointInImage pim = p.getNodeWithoutChecks(node);
						final double y = pim.y * cos - pim.z * sin;
						final double z = pim.z * cos + pim.y * sin;
						p.moveNode(node, new PointInImage(pim.x, y, z));
					}
					if (p.startJoinsPoint != null) {
						final PointInImage sPim = p.startJoinsPoint;
						final Path sPath = p.startJoins;
						sPim.x = sPim.y * cos - sPim.z * sin;
						sPim.y = sPim.z * cos + sPim.y * sin;
						p.unsetStartJoin();
						p.setStartJoin(sPath, sPim);
					}
					if (p.endJoinsPoint != null) {
						final PointInImage ePim = p.endJoinsPoint;
						final Path ePath = p.endJoins;
						ePim.x = ePim.y * cos - ePim.z * sin;
						ePim.y = ePim.z * cos + ePim.y * sin;
						p.unsetEndJoin();
						p.setEndJoin(ePath, ePim);
					}
				});
				break;
			default:
				throw new IllegalArgumentException("Unrecognized rotation axis" + axis);
		}
		if (box != null) box.setComputationNeeded(true);
	}

	/**
	 * Gets all the nodes (path points) forming this tree.
	 *
	 * @return the points
	 */
	public List<PointInImage> getNodes() {
		final List<PointInImage> list = new ArrayList<>();
		for (final Path p : tree) {
			for (int i = 0; i < p.size(); ++i) {
				list.add(p.getNodeWithoutChecks(i));
			}
		}
		return list;
	}

	/**
	 * Assesses whether this Tree has depth.
	 *
	 * @return true, if is 3D
	 */
	public boolean is3D() {
		final double zRef = getRoot().getZ();
		for (final Path p : tree) {
			for (int i = 0; i < p.size(); ++i) {
				if (p.getNodeWithoutChecks(i).getZ() != zRef) return true;
			}
		}
		return false;
	}

	/**
	 * Associates a bounding box to this tree.
	 *
	 * @param box the BoundingBox, typically referring to the image associated
	 *          with this tree
	 */
	public void setBoundingBox(final BoundingBox box) {
		this.box = (box == null) ? null : new TreeBoundingBox(box);
	}

	/**
	 * Gets the bounding box associated with this tree.
	 *
	 * @return the BoundingBox. It will be computed if no boundaries have been set.
	 */
	public BoundingBox getBoundingBox() {
		return getBoundingBox(box == null);
	}

	/**
	 * Gets the bounding box associated with this tree.
	 *
	 * @param computeIfUnset if {@code true} no BoundingBox has been explicitly
	 *          set, and, a BoundingBox will be compute from all the nodes of this
	 *          Tree
	 * @return the BoundingBox
	 */
	public BoundingBox getBoundingBox(final boolean computeIfUnset) {
		final boolean compute = box == null || computeIfUnset || box.isComputationNeeded();
		if (box == null) 
			box = new TreeBoundingBox();
		if (compute)
			box.compute(getNodes().iterator());
		return box;
	}

	/**
	 * Gets an empty image capable of holding the skeletonized version of this tree.
	 *
	 * @param multiDThreePaneView the pane flag indicating the SNT view for this
	 *                            image e.g., {@link MultiDThreePanes#XY_PLANE}
	 * @param bitDepth            8, 16 or 32 (float)
	 * 
	 * @return the empty {@link ImagePlus} container
	 */
	public ImagePlus getImpContainer(final int multiDThreePaneView, final int bitDepth) {
		if (tree.isEmpty()) throw new IllegalArgumentException(
			"tree contains no paths");
		final BoundingBox bBox = getBoundingBox(true);
		final Path referencePath = tree.iterator().next();
		bBox.origin().onPath = referencePath;
		bBox.originOpposite().onPath = referencePath;
		final PointInCanvas bound1 = bBox.origin().getUnscaledPoint(multiDThreePaneView);
		final PointInCanvas bound2 = bBox.originOpposite().getUnscaledPoint(multiDThreePaneView);

		// Padding is required to accommodate "rounding errors"
		// in PathAndFillManager.setPathPointsInVolume()
		final int xyPadding = 6; // 4 extra pixels on each margin
		final int zPadding = 2; // 1 slice above / below last point
		final int w = (int) Math.round(bound2.x - bound1.x) + xyPadding;
		final int h = (int) Math.round(bound2.y - bound1.y) + xyPadding;
		int d = (int) Math.round(bound2.z - bound1.z);
		if (d < 1) d = 1;
		if (d > 1) d += zPadding;
		final ImagePlus imp = IJ.createImage(null, w, h, d, bitDepth);
		return imp;
	}

	/**
	 * Skeletonizes (rasterizes) this tree on the specified image using Bresenham’s
	 * Algorithm (3D).
	 *
	 * @param destinationImp the destination image (16-bit). It is assumed that the
	 *                       image dimensions are suitable, and that the spatial
	 *                       calibration of the image is compatible with that of
	 *                       this tree. Out of bound locations will be silently
	 *                       ignored.
	 * @param value          the pixel intensity of the skeleton
	 * @throws IllegalArgumentException If image is not 16-bit grayscale (unsigned)
	 */
	public void skeletonize(final ImagePlus destinationImp, final int value) throws IllegalArgumentException {
		if (destinationImp.getType() != ImagePlus.GRAY16) {
			throw new IllegalArgumentException("Only 16-bit images supported");
		}
		final int width = destinationImp.getWidth();
		final int height = destinationImp.getHeight();
		final int depth = destinationImp.getNSlices();
		final int channel = destinationImp.getC();
		final int frame = destinationImp.getT();
		final ImageStack s = destinationImp.getStack();
		final short[][] slices_data = new short[depth][];
		for (int z = 0; z < depth; ++z) {
			slices_data[z] = (short[]) s.getPixels(destinationImp.getStackIndex(channel, z + 1, frame));
		}
		new PathAndFillManager().setPathPointsInVolume(list(), slices_data, value, width, height, depth);
	}

	/**
	 * Retrieves the rasterized skeleton of this tree at 1:1 scaling.
	 *
	 * @return the skeletonized 8-bit binary image: (skeleton: 255, background: 0).
	 * @see #skeletonize(ImagePlus, int)
	 */
	public ImagePlus getSkeleton() {

		// Find what is the offset of the tree relative to (0,0,0).
		// We'll set padding margins similarly to getImpContainer()
		SNTUtils.log("Skeletonizing "+ getLabel());
		box = (TreeBoundingBox) getBoundingBox(true);
		final double width = box.width();
		final double height = box.height();
		final double depth = box.depth();
		final boolean threeD = depth > 0;
		final int xyMargin = 3;
		final int zMargin = (threeD) ? 1 : 0;
		final double xOffset = box.origin().getX() - xyMargin;
		final double yOffset = box.origin().getY() - xyMargin;
		final double zOffset = box.origin().getZ() - zMargin;
		SNTUtils.log("  Tree boundaries: " + width + "x" + height + "x" + depth);

		// Apply the translation offset to each Path as canvas offset and
		// map the path scaling to 1. We'll keep track of existing values
		// so that we can restore them after skeletonization
		final ArrayList<PointInCanvas> pics = new ArrayList<>(size());
		final ArrayList<Calibration> spacings = new ArrayList<>(size());
		for (final Path p : list()) {
			pics.add(p.getCanvasOffset());
			p.setCanvasOffset(new PointInCanvas(-xOffset, -yOffset, -zOffset));
			spacings.add(p.getCalibration());
			p.setSpacing(new Calibration()); // 1,1,1 pixel spacing
		}

		// Define image dimensions
		final int w = (int) Math.round(width) + (2 * xyMargin);
		final int h = (int) Math.round(height) + (2 * xyMargin);
		int d = (int) Math.round(depth);
		if (d < 1) d = 1;
		if (d > 1) d += (2 * zMargin);
		SNTUtils.log("  Allocating " + w + "x" + h + "x" + d + " pixels (16-bit)");
		final ImagePlus imp = IJ.createImage("Skel " + getLabel(), w, h, d, 16);

		// Skeletonize
		skeletonize(imp, 65535);
		SNTUtils.convertTo8bit(imp);
		if (getBoundingBox().isScaled())
			imp.setCalibration(getBoundingBox().getCalibration());

		// Restore initial state
		SNTUtils.log("  Skeletonization complete");
		for (int i = 0; i < size(); i++) {
			get(i).setCanvasOffset(pics.get(i));
			get(i).setSpacing(spacings.get(i));
		}

		return imp;
	}

	/**
	 * Retrieves a 2D projection of the rasterized skeleton of this tree at 1:1
	 * scaling.
	 *
	 * @return the skeletonized 8-bit binary image: (skeleton: 255, background: 0).
	 * @see #getSkeleton()
	 */
	public ImagePlus getSkeleton2D() {
		final ImagePlus imp = getSkeleton();
		return (imp.getNDimensions() > 2) ? SNTUtils.getMIP(imp) : imp;
	}

	/**
	 * Retrieves the number of paths in this tree.
	 *
	 * @return Returns the number of paths in this tree.
	 */
	public int size() {
		return tree.size();
	}

	/**
	 * Assigns a color to all the paths in this tree. Note that assigning a
	 * non-null color will remove node colors from Paths.
	 *
	 * @param color the color to be applied.
	 * @see Path#hasNodeColors()
	 */
	public void setColor(final ColorRGB color) {
		tree.forEach(p -> {
			p.setColor(color);
			if (color != null) p.setNodeColors(null);
		});
	}

	/**
	 * Assigns a color to all the paths in this tree. Note that assigning a non-null
	 * color will remove node colors from Paths.
	 * 
	 * @param color the color to be applied, either a 1) HTML color codes starting
	 *              with hash ({@code #}), a color preset ("red", "blue", etc.), or
	 *              integer triples of the form {@code r,g,b} and range
	 *              {@code [0, 255]}
	 */
	public void setColor(final String color) {
		setColor(new ColorRGB(color));
	}

	/**
	 * Assigns a fixed radius to all the nodes in this tree.
	 *
	 * @param r the radius to be assigned. Setting it to 0 or Double.NaN removes
	 *          the radius attribute from the Tree
	 */
	public void setRadii(final double r) {
		tree.forEach(p -> p.setRadius(r));
	}

	/**
	 * Sets an identifying label for this Tree.
	 *
	 * @param label the identifying string
	 */
	public void setLabel(final String label) {
		this.label = label;
	}

	/**
	 * Returns the identifying label of this tree. When importing files, the label
	 * typically defaults to the imported filename,
	 *
	 * @return the Tree label (or null) if none has been set.
	 */
	public String getLabel() {
		return label;
	}

	public Collection<SWCPoint> getNodesAsSWCPoints() throws IllegalArgumentException {
		initPathAndFillManager();
		try {
			return pafm.getSWCFor(tree);
		} catch (final SWCExportException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
	}

	/**
	 * Assembles a DirectedGraph from this Tree.
	 *
	 * @return the Tree's graph with edge weights corresponding to inter-node
	 *         distances
	 * @throws IllegalArgumentException if tree contains multiple roots or loops
	 */
	public DirectedWeightedGraph getGraph() throws IllegalArgumentException {
		return new DirectedWeightedGraph(this);
	}

	/**
	 * Assembles a DirectedGraph from this Tree.
	 *
	 * @param simplify if true, graph will be simplified so that Tree is only
	 *                 represented by root, branch-points and tips.
	 * @return the Tree's graph with edge weights corresponding to branch lengths
	 * @throws IllegalArgumentException if tree contains multiple roots or loops
	 */
	public DirectedWeightedGraph getGraph(final boolean simplify) throws IllegalArgumentException {
		return (simplify) ? getGraph().getSimplifiedGraph() : getGraph();
	}

	/**
	 * Script-friendly method for creating a Tree from a reconstruction file.
	 *
	 * @param filePath the absolute path to the file (.Traces, (e)SWC or JSON) to be
	 *                 imported
	 * @return the Tree instance, or null if file could not be imported
	 */
	public static Tree fromFile(final String filePath) {
		try {
			return new Tree(filePath);
		} catch (final IllegalArgumentException ex) {
			return null;
		}
	}

	/**
	 * Retrieves a list of {@link Tree}s from reconstruction files stored in a
	 * common directory.
	 *
	 * @param dir the directory containg the reconstruction files (.(e)swc, .traces,
	 *            .json extension)
	 * @return the list of imported {@link Tree}s. An empty list is retrieved if
	 *         {@code dir} is not a valid, readable directory.
	 */
	public static List<Tree> listFromDir(final String dir) {
		return listFromDir(dir, "");
	}

	/**
	 * Retrieves a list of {@link Tree}s from reconstruction files stored in a
	 * common directory matching the specified criteria.
	 *
	 * @param dir     the directory containing the reconstruction files (.(e)swc,
	 *                .traces, .json extension)
	 * @param pattern the filename substring (case sensitive) to be matched. Only
	 *                filenames containing {@code pattern} will be imported from the
	 *                directory. {@code null} allowed.
	 * @return the list of imported {@link Tree}s. An empty list is retrieved if
	 *         {@code dir} is not a valid, readable directory.
	 */
	public static List<Tree> listFromDir(final String dir, final String pattern) {
		return listFromDir(dir, pattern, (String[])null);
	}

	/**
	 * Retrieves a list of {@link Tree}s from reconstruction files stored in a
	 * common directory matching the specified criteria.
	 *
	 * @param dir     the directory containing the reconstruction files (.(e)swc,
	 *                .traces, .json extension)
	 * @param pattern the filename substring (case sensitive) to be matched. Only
	 *                filenames containing {@code pattern} will be imported from the
	 *                directory. {@code null} allowed.
	 * @param swcTypes SWC type(s) a string with at least 2 characters describing
	 *                 the SWC type allowed in the subtree (e.g., 'soma', 'axn', or
	 *                 'dendrite'). Ignored when {@code null}
	 * @return the list of imported {@link Tree}s. An empty list is retrieved if
	 *         {@code dir} is not a valid, readable directory.
	 */
	public static List<Tree> listFromDir(final String dir, final String pattern, final String... swcTypes) {
		final String validatedPattern = (pattern == null) ? "" : pattern;
		final FileFilter filter = (file) -> {
			final String name = file.getName();
			if (!name.contains(validatedPattern))
				return false;
			final String lName = name.toLowerCase();
			return file.canRead() && (lName.endsWith("swc") || lName.endsWith(".traces") || lName.endsWith(".json"));
		};
		final List<Tree> trees = new ArrayList<>();
		if (dir == null) return trees;
		final File dirFile = new File(dir);
		if (!dirFile.isDirectory() || !dirFile.exists() || !dirFile.canRead()) {
			return trees;
		}
		final File treeFiles[] = dirFile.listFiles(filter);
		if (treeFiles == null || treeFiles.length == 0) {
			return trees;
		}
		for (final File treeFile : treeFiles) {
			final Tree tree = Tree.fromFile(treeFile.getAbsolutePath());
			if (tree != null) {
				if (swcTypes == null)
					trees.add(tree);
				else
					trees.add(tree.subTree(swcTypes));
			}
		}
		return trees;
	}

	/**
	 * Returns the SWC Type flags used by SNT.
	 * 
	 * @return the map mapping swct type flags (e.g., {@link Path#SWC_AXON},
	 *         {@link Path#SWC_DENDRITE}, etc.) and their respective labels
	 */
	public static Map<Integer, String> getSWCTypeMap() {
		final HashMap<Integer, String> map = new HashMap<>();
		map.put(Path.SWC_UNDEFINED, Path.SWC_UNDEFINED_LABEL);
		map.put(Path.SWC_SOMA, Path.SWC_SOMA_LABEL);
		map.put(Path.SWC_AXON, Path.SWC_AXON_LABEL);
		map.put(Path.SWC_DENDRITE, Path.SWC_DENDRITE_LABEL);
		map.put(Path.SWC_APICAL_DENDRITE, Path.SWC_APICAL_DENDRITE_LABEL);
		map.put(Path.SWC_CUSTOM, Path.SWC_CUSTOM_LABEL);
		return map;
	}

	/**
	 * Saves this Tree to an SWC file.
	 *
	 * @param filePath the absolute path of the output file. {@code .swc} is
	 *                 automatically appended if {@code filePath} does not include
	 *                 an extension. If a label has been assigned, {@code filePath}
	 *                 can also be a directory. If this Tree contains multiple
	 *                 roots, each rooted structure will be saved on a series of
	 *                 files with 3-digit identifiers appended to the specified file
	 *                 path (e.g., -000.swc, -001.swc, etc).
	 * @return true, if file successfully saved.
	 * @see #setLabel(String)
	 */
	public boolean saveAsSWC(final String filePath) {
		if (list() == null || list().isEmpty() || filePath == null || filePath.isEmpty())
			return false;
		initPathAndFillManager();
		File file = new File(filePath);
		if (file.isDirectory() && getLabel() != null) {
			final String fName = (getLabel().toLowerCase().endsWith(".swc")) ? getLabel() : getLabel() + ".swc";
			file = new File(file.getAbsolutePath(), fName);
		} else if (!filePath.toLowerCase().endsWith(".swc")) {
			file = new File(filePath + ".swc");
		}
		return pafm.savetoFileOrFileSeries(file);
	}

	/**
	 * Saves this Tree to a .TRACES (XML, compressed) file.
	 *
	 * @param filePath the absolute path of the output file. {@code .traces} is
	 *                 automatically appended if {@code filePath} does not include
	 *                 an extension. If a label has been assigned, {@code filePath}
	 *                 can also be a directory.
	 * @return true, if file successfully saved.
	 * @see #setLabel(String)
	 */
	public boolean save(final String filePath) {
		if (list() == null || list().isEmpty() || filePath == null || filePath.isEmpty())
			return false;
		initPathAndFillManager();
		File file = new File(filePath);
		if (file.isDirectory() && getLabel() != null) {
			final String fName = (getLabel().toLowerCase().endsWith(".traces")) ? getLabel() : getLabel() + ".traces";
			file = new File(file.getAbsolutePath(), fName);
		} else if (!filePath.toLowerCase().endsWith(".traces")) {
			file = new File(filePath + ".traces");
		}
		try {
			pafm.writeXML(file.getAbsolutePath(), true);
		} catch (final IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Assigns a numeric property to this Tree.
	 * 
	 * @param value the value to be assigned to this Tree.
	 * @see #getAssignedValue()
	 */
	public void assignValue(final double value) {
		this.value = value;
	}

	/**
	 * Retrieves an approximate estimate of Tree's volume by approximating the
	 * volume of each path, and summing to total. THe volume of each path is
	 * computed assuming the volume of each of inter-node segment to be that of a
	 * truncated cone (Frustum).
	 * 
	 * @return the approximate volume or 9 if this Tree's paths have no radius
	 *         information
	 * @see Path#getApproximatedVolume()
	 */
	public double getApproximatedVolume() {
		double volume = 0;
		for (final Path path : tree) {
			volume += path.getApproximatedVolume();
		}
		return volume;
	}

	/**
	 * Retrieves the numeric property assigned to this Tree.
	 * 
	 * @return the assigned value.
	 * @see #assignValue(double)
	 */
	public double getAssignedValue() {
		return value;
	}

	private void initPathAndFillManager() {
		if (pafm == null) pafm = new PathAndFillManager();
		if (pafm.size() == 0) for (final Path p : list()) pafm.addPath(p);
	}

	@Override
	public Tree clone() {
		final Tree clone = new Tree();
		clone.setLabel(getLabel());
		clone.setBoundingBox(box);
		for (final Path path : list()) clone.add(path.clone());
		return clone;
	}

	private class TreeBoundingBox extends BoundingBox {

		private boolean dimensionsNeedToBeComputed;

		TreeBoundingBox(final BoundingBox box) {
			this.origin = box.origin();
			this.originOpposite = box.originOpposite();
			this.xSpacing = box.xSpacing;
			this.ySpacing = box.ySpacing;
			this.zSpacing = box.zSpacing;
			this.info = box.info;
			this.spacingUnit = box.getUnit();
		}

		TreeBoundingBox() {
			super();
		}

		void setComputationNeeded(final boolean bool) {
			dimensionsNeedToBeComputed = bool;
			if (bool) reset();
		}

		boolean isComputationNeeded() {
			return dimensionsNeedToBeComputed;
		}
	}

	/* IDE debug method */
	public static void main(final String[] args) {

		final Tree tree = new Tree("/home/tferr/code/zi/AA0174.json");
		TreeAnalyzer analyzer = new TreeAnalyzer(tree);
		final int treeBPs = analyzer.getBranchPoints().size();
		final int treeTips = analyzer.getTips().size();
		final int treeBranches = analyzer.getBranches().size();

		final DirectedWeightedGraph graph = tree.getGraph();
		System.out.println("Graph : Tree Before Filtering");
		System.out.println(graph.vertexSet().size() + " : " + tree.getNodes().size());
		System.out.println(graph.vertexSet().size() + " : " + tree.getNodesAsSWCPoints().size());

		final Tree aTree = tree.subTree("axon");
		final DirectedWeightedGraph aGraph = aTree.getGraph();
		System.out.println("SubGraph : SubTree After Filtering");
		System.out.println(tree.getLabel() + " : " + aTree.getLabel());
		System.out.println(aGraph.vertexSet().size() + " : " + aTree.getNodes().size());
		System.out.println(aGraph.vertexSet().size() + " : " + aTree.getNodesAsSWCPoints().size());

		// Did the filtering affect original tree?
		analyzer = new TreeAnalyzer(tree);
		System.out.println("Tree Before Filtering : Tree After Filtering");
		System.out.println(treeBPs + " : " + analyzer.getBranchPoints().size());
		System.out.println(treeTips + " : " + analyzer.getTips().size());
		System.out.println(treeBranches + " : " + analyzer.getBranches().size());

	}
}