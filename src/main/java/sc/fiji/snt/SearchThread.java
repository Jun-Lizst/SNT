/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2021 Fiji developers.
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

import java.util.*;

import ij.ImagePlus;
import org.jheaps.AddressableHeap;
import org.jheaps.tree.PairingHeap;
import sc.fiji.snt.util.SparseMatrix;
import sc.fiji.snt.util.SparseMatrixStack;

/**
 * Implements a common thread that explores the image using a variety of
 * strategies, e.g., to trace tubular structures or surfaces.
 * 
 * @author Mark Longair
 * @author Tiago Ferreira
 * @author Cameron Arshadi
 */
public abstract class SearchThread extends AbstractSearch implements Runnable {

	/** Thread state: the run method is going and the thread is unpaused */
	public static final int RUNNING = 0;
	/** Thread state: run() hasn't been started yet or the thread is paused */
	public static final int PAUSED = 1;
	/** Thread state: the thread cannot be used again */
	public static final int STOPPING = 2;

	public static final int SUCCESS = 0;
	public static final int CANCELLED = 1;
	public static final int TIMED_OUT = 2;
	public static final int POINTS_EXHAUSTED = 3;
	public static final int OUT_OF_MEMORY = 4;
	public static final String[] EXIT_REASONS_STRINGS = { "SUCCESS", "CANCELLED",
		"TIMED_OUT", "POINTS_EXHAUSTED", "OUT_OF_MEMORY" };

	/* This can only be changed in a block synchronized on this object */
	private volatile int threadStatus = PAUSED;

	/* The search may only be bidirectional if definedGoal is true */
	private final boolean bidirectional;

	/*
	 * If there is no definedGoal then the search is just Dijkstra's algorithm (h =
	 * 0 in the A* search algorithm.
	 */
	private final boolean definedGoal;

	protected AddressableHeap<DefaultSearchNode, Void> open_from_start;
	private AddressableHeap<DefaultSearchNode, Void> open_from_goal;

	private long closed_from_start_count;
	private long closed_from_goal_count;

	protected SparseMatrixStack<DefaultSearchNode> nodes_as_image_from_start;
	protected SparseMatrixStack<DefaultSearchNode> nodes_as_image_from_goal;

	protected double minimum_cost_per_unit_distance;

	protected int exitReason;
	private final boolean verbose = SNTUtils.isDebugMode();
	protected int minExpectedSize;

	/* If you specify 0 for timeoutSeconds then there is no timeout. */
	public SearchThread(final ImagePlus imagePlus, final float stackMin, final float stackMax,
						final boolean bidirectional, final boolean definedGoal, final int timeoutSeconds,
						final long reportEveryMilliseconds)
	{
		super(imagePlus, stackMin, stackMax, timeoutSeconds, reportEveryMilliseconds);
		this.bidirectional = bidirectional;
		this.definedGoal = definedGoal;
		init();
	}

	protected SearchThread(final SNT snt)
	{
		super(snt);
		bidirectional = true;
		definedGoal = true;
		init();
	}

	private void init() {
		open_from_start = new PairingHeap<>();
		if (bidirectional) {
			open_from_goal = new PairingHeap<>();
		}
		nodes_as_image_from_start = new SparseMatrixStack<>(depth);
		if (bidirectional) {
			nodes_as_image_from_goal = new SparseMatrixStack<>(depth);
		}
		progressListeners = new ArrayList<>();
	}



	@Override
	protected void reportPointsInSearch() {
		for (final SearchProgressCallback progress : progressListeners)
			progress.pointsInSearch(this, open_from_start.size() + (bidirectional
				? open_from_goal.size() : 0), closed_from_start_count + (bidirectional
					? closed_from_goal_count : 0));
	}

	public long pointsConsideredInSearch() {
		return open_from_start.size() + (bidirectional ? open_from_goal.size()
			: 0) + closed_from_start_count + (bidirectional ? closed_from_goal_count
				: 0);
	}

	/*
	 * This is a factory method for creating specialized search nodes, subclasses of
	 * SearchNode:
	 */

	public DefaultSearchNode createNewNode(final int x, final int y, final int z,
										   final double g, final double h, final DefaultSearchNode predecessor,
										   final byte searchStatus)
	{
		return new DefaultSearchNode(x,y,z, g, h, predecessor, searchStatus);
	}

	/*
	 * This is called if the goal has been found in the search. If your search has
	 * no defined goal, then this will never be called, so don't bother to override
	 * it.
	 */

	protected void foundGoal(final Path pathToGoal) {
		/*
		 * A dummy implementation that does nothing with this exciting news.
		 */
	}

	protected boolean atGoal(final int x, final int y, final int z,
		final boolean fromStart)
	{
		return false;
	}

	/*
	 * If you need to force the distance between two points to always be greater
	 * than some value (e.g. to make your A star heuristic valid or something, then
	 * you should override this method and return that value.
	 */
	protected double minimumCostPerUnitDistance() {
		return 0.0;
	}


	public void addProgressListener(final SearchProgressCallback callback) {
		progressListeners.add(callback);
	}

	public int getThreadStatus() {
		return threadStatus;
	}

	/**
	 * This method can be overridden if one needs to find out when a point was
	 * first discovered.
	 *
	 * @param n the search node
	 */
	protected void addingNode(final DefaultSearchNode n) {}

	public void reportThreadStatus() {
		for (final SearchProgressCallback progress : progressListeners)
			progress.threadStatus(this, threadStatus);
	}

	public void reportFinished(final boolean success) {
		for (final SearchProgressCallback progress : progressListeners)
			progress.finished(this, success);
	}

	@Override
	public void printStatus() {
		System.out.println("... Start nodes: open=" + open_from_start.size() +
			" closed=" + closed_from_start_count);
		if (bidirectional) {
			System.out.println("...  Goal nodes: open=" + open_from_goal.size() +
				" closed=" + closed_from_goal_count);
		}
		else System.out.println(" ... unidirectional search");
	}

	@Override
	public void run() {

		try {

			if (verbose) {
				System.out.println("New SearchThread running!");
				printStatus();
			}

			synchronized (this) {
				threadStatus = RUNNING;
				reportThreadStatus();
			}

			long lastReportMilliseconds;
			final long started_at = lastReportMilliseconds = System.currentTimeMillis();

			int loops_at_last_report = 0;
			int loops = 0;

			/*
			 * We maintain the list of nodes in the search in a couple of different data
			 * structures here, which is bad for memory usage but good for the speed of the
			 * search.
			 *
			 * As well as keeping the nodes in priority lists, we keep them in a set of
			 * arrays that are indexed in the same way as voxels in the image.
			 */

			while ((open_from_start.size() > 0) || (bidirectional && (open_from_goal
				.size() > 0)))
			{

				if (Thread.currentThread().isInterrupted()) {
					synchronized (this) {
						threadStatus = STOPPING;
					}
					reportThreadStatus();
					setExitReason(CANCELLED);
					reportFinished(false);
					return;
				}

				// We only check every thousandth loop for
				// whether we should report the progress, etc.

				if (0 == (loops % 10000)) {

					final long currentMilliseconds = System.currentTimeMillis();

					final long millisecondsSinceStart = currentMilliseconds - started_at;

					if ((timeoutSeconds > 0) && (millisecondsSinceStart > (1000L * timeoutSeconds)))
					{
						if (verbose) System.out.println("Timed out...");
						setExitReason(TIMED_OUT);
						reportFinished(false);
						return;
					}

					final long since_last_report = currentMilliseconds -
							lastReportMilliseconds;

					if ((reportEveryMilliseconds > 0) &&
						(since_last_report > reportEveryMilliseconds))
					{
						final int loops_since_last_report = loops - loops_at_last_report;

						if (verbose) {
							System.out.println("" + (since_last_report / (double)loops_since_last_report) + "ms/loop");
							printStatus();
						}

						reportPointsInSearch();

						loops_at_last_report = loops;

						lastReportMilliseconds = currentMilliseconds;
					}
				}

				boolean fromStart = true;
				if (bidirectional) fromStart = open_from_goal.size() > open_from_start
					.size();

				final AddressableHeap<DefaultSearchNode, Void> open_queue = fromStart ? open_from_start
					: open_from_goal;

				final SparseMatrixStack<DefaultSearchNode> nodes_as_image_this_search = fromStart
					? nodes_as_image_from_start : nodes_as_image_from_goal;
				final SparseMatrixStack<DefaultSearchNode> nodes_as_image_other_search = fromStart
					? nodes_as_image_from_goal : nodes_as_image_from_start;

				DefaultSearchNode p;

				if (open_queue.size() == 0) continue;

				p = open_queue.deleteMin().getKey();
				if (p == null) continue;

				// Has the route from the start found the goal?
				if (definedGoal && atGoal(p.x, p.y, p.z, fromStart)) {
					if (verbose) System.out.println("Found the goal!");
					if (fromStart) foundGoal(p.asPath(x_spacing, y_spacing, z_spacing,
						spacing_units));
					else foundGoal(p.asPathReversed(x_spacing, y_spacing, z_spacing,
						spacing_units));
					setExitReason(SUCCESS);
					reportFinished(true);
					return;
				}

				if (fromStart) {
					p.searchStatus = CLOSED_FROM_START;
					closed_from_start_count++;
				} else {
					p.searchStatus = CLOSED_FROM_GOAL;
					closed_from_goal_count++;
				}
				nodes_as_image_this_search.getSlice(p.z).setValueWithoutChecks(p.x, p.y, p);

				// Now look at the neighbours of p. We're going to consider
				// the 26 neighbours in 3D.

				for (int zdiff = -1; zdiff <= 1; zdiff++) {

					final int new_z = p.z + zdiff;
					if (new_z < 0 || new_z >= depth) continue;

					if (nodes_as_image_this_search.getSlice(new_z) == null) {
						nodes_as_image_this_search.newSlice(new_z);
					}

					for (int xdiff = -1; xdiff <= 1; xdiff++)
						for (int ydiff = -1; ydiff <= 1; ydiff++) {

							if ((xdiff == 0) && (ydiff == 0) && (zdiff == 0)) continue;

							final int new_x = p.x + xdiff;
							final int new_y = p.y + ydiff;

							if (new_x < 0 || new_x >= width) continue;

							if (new_y < 0 || new_y >= height) continue;

							final double xdiffsq = (xdiff * x_spacing) * (xdiff * x_spacing);
							final double ydiffsq = (ydiff * y_spacing) * (ydiff * y_spacing);
							final double zdiffsq = (zdiff * z_spacing) * (zdiff * z_spacing);

							final double h_for_new_point = estimateCostToGoal(new_x, new_y,
								new_z, fromStart);

							double cost_moving_to_new_point = costMovingTo(new_x, new_y,
								new_z);
							if (cost_moving_to_new_point < minimum_cost_per_unit_distance) {
								cost_moving_to_new_point = minimum_cost_per_unit_distance;
							}

							final double g_for_new_point = (p.g + Math.sqrt(xdiffsq +
								ydiffsq + zdiffsq) * cost_moving_to_new_point);

							if (!definedGoal && g_for_new_point > drawingThreshold) {
								// Only fill up to the threshold
								continue;
							}

							final double f_for_new_point = h_for_new_point + g_for_new_point;

							DefaultSearchNode newNode = createNewNode(new_x, new_y, new_z, g_for_new_point,
									h_for_new_point, p, FREE);

							// Is this newNode really new?
							DefaultSearchNode alreadyThereInThisSearch =
								nodes_as_image_this_search.getSlice(new_z).getValue(newNode.x, newNode.y);

							if (alreadyThereInThisSearch == null) {
								newNode.searchStatus = fromStart ? OPEN_FROM_START
										: OPEN_FROM_GOAL;
								newNode.heapHandle = open_queue.insert(newNode);
								addingNode(newNode);
								nodes_as_image_this_search.getSlice(new_z).setValue(newNode.x, newNode.y, newNode);
							}
							else {

								// The other alternative is that this node is
								// already in one
								// of the lists working from the start but has a
								// better way
								// of getting to that point.

								if (alreadyThereInThisSearch.f > f_for_new_point) {

									if (alreadyThereInThisSearch.searchStatus == (fromStart
										? OPEN_FROM_START : OPEN_FROM_GOAL))
									{
										alreadyThereInThisSearch.setFrom(newNode);
										alreadyThereInThisSearch.searchStatus = fromStart
											? OPEN_FROM_START : OPEN_FROM_GOAL;
										alreadyThereInThisSearch.heapHandle.decreaseKey(alreadyThereInThisSearch);
									}
									else if (alreadyThereInThisSearch.searchStatus == (fromStart
										? CLOSED_FROM_START : CLOSED_FROM_GOAL))
									{

										//closed_queue.remove(alreadyThereInThisSearch);
										alreadyThereInThisSearch.setFrom(newNode);
										alreadyThereInThisSearch.searchStatus = fromStart
											? OPEN_FROM_START : OPEN_FROM_GOAL;
										alreadyThereInThisSearch.heapHandle = open_queue.insert(alreadyThereInThisSearch);
									}
								}
							}

							if (bidirectional && nodes_as_image_other_search.getSlice(new_z) != null) {

								final DefaultSearchNode alreadyThereInOtherSearch =
									nodes_as_image_other_search.getSlice(new_z).getValue(newNode.x, newNode.y);
								if (alreadyThereInOtherSearch != null) {

									Path result;

									// If either of the next two if conditions
									// are true
									// then we've finished.

									if (alreadyThereInOtherSearch.searchStatus == CLOSED_FROM_START ||
											alreadyThereInOtherSearch.searchStatus == CLOSED_FROM_GOAL)
									{

										if (fromStart) {
											result = p.asPath(x_spacing, y_spacing, z_spacing,
												spacing_units);
											final Path fromGoalReversed = alreadyThereInOtherSearch
												.asPathReversed(x_spacing, y_spacing, z_spacing,
													spacing_units);
											result.add(fromGoalReversed);
										}
										else {
											result = alreadyThereInOtherSearch.asPath(x_spacing,
												y_spacing, z_spacing, spacing_units);
											result.add(p.asPathReversed(x_spacing, y_spacing,
												z_spacing, spacing_units));
										}
										if (verbose) System.out.println("Searches met!");
										foundGoal(result);
										setExitReason(SUCCESS);
										reportFinished(true);
										return;
									}
								}
							}
						}
				}
				++loops;
			}

			/*
			 * If we get to here then we haven't found a route to the point. (With the
			 * current impmlementation this shouldn't happen, so print a warning - probably
			 * the programmer hasn't populated the open list to start with.) However, in
			 * this case let's return the best path so far anyway...
			 */

			if (definedGoal) {
				if (verbose) System.out.println("FAILED to find a route.  Shouldn't happen...");
				setExitReason(POINTS_EXHAUSTED);
				reportFinished(false);
			}
			else {
				if (verbose) System.out.println("Fill complete for thread " + Thread.currentThread());
				setExitReason(SUCCESS);
				reportFinished(true);
			}

		}
		catch (final OutOfMemoryError oome) {
			SNTUtils.error("Out Of Memory Error", oome);
			setExitReason(OUT_OF_MEMORY);
			reportFinished(false);
		}
		catch (final Throwable t) {
			SNTUtils.error("Exception in search thread " + Thread.currentThread(), t);
		}
	}

	/*
	 * This is the heuristic value for the A* search. There's no defined goal in
	 * this default superclass implementation, so always return 0 so we end up with
	 * Dijkstra's algorithm.
	 */
	double estimateCostToGoal(final int current_x, final int current_y,
		final int current_z, final boolean fromStart)
	{
		return 0;
	}

	/* This method is used to set the reason for the thread finishing */
	void setExitReason(final int exitReason) {
		this.exitReason = exitReason;
	}

	/*
	 * Use this to find out why the thread exited if you're not adding listeners to
	 * do that.
	 */
	int getExitReason() {
		return exitReason;
	}

	@Override
	protected DefaultSearchNode anyNodeUnderThreshold(final int x, final int y, final int z,
													  final double threshold)
	{
		final SparseMatrix<DefaultSearchNode> startSlice = nodes_as_image_from_start.getSlice(z);
		SparseMatrix<DefaultSearchNode> goalSlice = null;
		if (nodes_as_image_from_goal != null) goalSlice = nodes_as_image_from_goal.getSlice(z);
		DefaultSearchNode n = null;
		if (startSlice != null) {
			n = startSlice.getValue(x, y);
			if (n != null && threshold >= 0 && n.g > threshold) n = null;
			if (n == null && goalSlice != null) {
				n = goalSlice.getValue(x, y);
				if (threshold >= 0 && n.g > threshold) n = null;
			}
		}
		return n;
	}

	// Add a node, ignoring requests to add duplicate nodes:

	public void addNode(final DefaultSearchNode n, final boolean fromStart) {

		final SparseMatrixStack<DefaultSearchNode> nodes_as_image = fromStart ? nodes_as_image_from_start
			: nodes_as_image_from_goal;

		SparseMatrix<DefaultSearchNode> slice = nodes_as_image.getSlice(n.z);
		if (slice == null) {
			slice = nodes_as_image.newSlice(n.z);
		}

		if (slice.getValue(n.x, n.y) != null) {
			// Then there's already a node there:
			return;
		}

		if (n.searchStatus == OPEN_FROM_START) {

			n.heapHandle = open_from_start.insert(n);
			slice.setValue(n.x, n.y, n);
		}
		else if (n.searchStatus == OPEN_FROM_GOAL) {
			assert bidirectional && definedGoal;

			n.heapHandle = open_from_goal.insert(n);
			slice.setValue(n.x, n.y, n);

		}
		else if (n.searchStatus == CLOSED_FROM_START) {

			slice.setValue(n.x, n.y, n);
		}
		else if (n.searchStatus == CLOSED_FROM_GOAL) {
			assert bidirectional && definedGoal;

			slice.setValue(n.x, n.y, n);

		}

	}

	public void setMinExpectedSizeOfResult(final int size) {
		this.minExpectedSize = size;
	}
}
