package sc.fiji.snt.viewer.geditor;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

import javax.swing.*;

import com.mxgraph.analysis.StructuralException;
import com.mxgraph.analysis.mxGraphProperties.GraphType;
import com.mxgraph.analysis.mxAnalysisGraph;
import com.mxgraph.analysis.mxGraphProperties;
import com.mxgraph.analysis.mxGraphStructure;
import com.mxgraph.analysis.mxTraversal;
import com.mxgraph.costfunction.mxCostFunction;
import com.mxgraph.model.mxICell;
import com.mxgraph.model.mxIGraphModel;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.util.mxGraphActions;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxPoint;
import com.mxgraph.util.mxResources;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxGraphView;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.analysis.graph.SNTGraph;
import sc.fiji.snt.gui.GuiUtils;
import sc.fiji.snt.plugin.GraphAdapterMapperCmd;
import sc.fiji.snt.viewer.SNTGraphAdapter;
import sc.fiji.snt.viewer.SNTGraphComponent;

public class EditorMenuBar extends JMenuBar
{
	/**
	 * 
	 */
	@Parameter
	Context context;
	private static final long serialVersionUID = 4060203894740766714L;
	protected final BasicGraphEditor editor;
	protected final mxGraphComponent graphComponent;
	protected final mxGraph graph;
	protected mxAnalysisGraph aGraph;

	protected JMenu menu = null;
	protected JMenu submenu = null;

	public enum AnalyzeType
	{
		PROPERTIES, COLOR_CODING, EDGE_SCALING, COMPLEMENTARY, REGULARITY, COMPONENTS, MAKE_CONNECTED, MAKE_SIMPLE,
		ONE_SPANNING_TREE, GET_CUT_VERTEXES, GET_CUT_EDGES, GET_SOURCES, GET_SINKS,
		PLANARITY, GET_BICONNECTED, SPANNING_TREE, FLOYD_ROY_WARSHALL
	}

	public EditorMenuBar(final BasicGraphEditor editor, final Context context)
	{
		context.inject(this);
		this.editor = editor;
		this.graphComponent = editor.getGraphComponent();
		this.graph = graphComponent.getGraph();
		this.aGraph = new mxAnalysisGraph();

		// Creates the file menu
		menu = add(new JMenu(mxResources.get("file")));

//		menu.add(editor.bind(mxResources.get("new"), new EditorActions.NewAction(), "/mx_shape_images/new.gif"));
		menu.add(editor.bind(mxResources.get("openFile"), new EditorActions.OpenAction(), "/mx_shape_images/open.gif"));
//		menu.add(editor.bind(mxResources.get("importStencil"), new EditorActions.ImportAction(), "/mx_shape_images/open.gif"));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("save"), new EditorActions.SaveAction(false), "/mx_shape_images/save.gif"));
		menu.add(editor.bind(mxResources.get("saveAs"), new EditorActions.SaveAction(true), "/mx_shape_images/saveas.gif"));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("pageSetup"), new EditorActions.PageSetupAction(), "/mx_shape_images/pagesetup.gif"));
		menu.add(editor.bind(mxResources.get("print"), new EditorActions.PrintAction(), "/mx_shape_images/print.gif"));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("exit"), new EditorActions.ExitAction()));

		// Creates the edit menu
		menu = add(new JMenu(mxResources.get("edit")));

		menu.add(editor.bind(mxResources.get("undo"), new EditorActions.HistoryAction(true), "/mx_shape_images/undo.gif"));
		menu.add(editor.bind(mxResources.get("redo"), new EditorActions.HistoryAction(false), "/mx_shape_images/redo.gif"));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("cut"), TransferHandler.getCutAction(), "/mx_shape_images/cut.gif"));
		menu.add(editor.bind(mxResources.get("copy"), TransferHandler.getCopyAction(), "/mx_shape_images/copy.gif"));
		menu.add(editor.bind(mxResources.get("paste"), TransferHandler.getPasteAction(), "/mx_shape_images/paste.gif"));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("delete"), mxGraphActions.getDeleteAction(), "/mx_shape_images/delete.gif"));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("selectAll"), mxGraphActions.getSelectAllAction()));
		menu.add(editor.bind(mxResources.get("selectNone"), mxGraphActions.getSelectNoneAction()));

//		menu.addSeparator();
//		menu.add(editor.bind(mxResources.get("warning"), new EditorActions.WarningAction()));
//		menu.add(editor.bind(mxResources.get("edit"), mxGraphActions.getEditAction()));

		// Creates the view menu
		menu = add(new JMenu(mxResources.get("view")));

		JMenuItem item = menu.add(new EditorActions.TogglePropertyItem(graphComponent, mxResources.get("pageLayout"),
				"PageVisible", false, e -> {
					if (graphComponent.isPageVisible() && graphComponent.isCenterPage()) {
						graphComponent.zoomAndCenter();
					} else {
						graphComponent.getGraphControl().updatePreferredSize();
					}
				}));
		item.addActionListener(e -> {
			if (e.getSource() instanceof EditorActions.TogglePropertyItem) {
				final mxGraphComponent graphComponent = editor.getGraphComponent();
				EditorActions.TogglePropertyItem toggleItem = (EditorActions.TogglePropertyItem) e.getSource();
				if (toggleItem.isSelected()) {
					// Scrolls the view to the center
					SwingUtilities.invokeLater(() -> {
						graphComponent.scrollToCenter(true);
						graphComponent.scrollToCenter(false);
					});
				} else {
					// Resets the translation of the view
					mxPoint tr = graphComponent.getGraph().getView().getTranslate();
					if (tr.getX() != 0 || tr.getY() != 0) {
						graphComponent.getGraph().getView().setTranslate(new mxPoint());
					}
				}
			}
		});

		menu.addSeparator();
		menu.add(new EditorActions.ToggleBottomPaneItem(editor, mxResources.get("outline")));
		menu.add(new EditorActions.ToggleRulersItem(editor, mxResources.get("rulers")));
		menu.add(new EditorActions.ToggleGridItem(editor, mxResources.get("grid"), graphComponent.isGridVisible()));

		submenu = (JMenu) menu.add(new JMenu("Grid Options"));
		submenu.add(editor.bind(mxResources.get("gridSize"), new EditorActions.PromptPropertyAction(graph, "Grid Size", "GridSize")));
		submenu.add(editor.bind(mxResources.get("gridColor"), new EditorActions.GridColorAction()));
		submenu.addSeparator();
		submenu.add(editor.bind(mxResources.get("dashed"), new EditorActions.GridStyleAction(mxGraphComponent.GRID_STYLE_DASHED)));
		submenu.add(editor.bind(mxResources.get("dot"), new EditorActions.GridStyleAction(mxGraphComponent.GRID_STYLE_DOT)));
		submenu.add(editor.bind(mxResources.get("line"), new EditorActions.GridStyleAction(mxGraphComponent.GRID_STYLE_LINE)));
		submenu.add(editor.bind(mxResources.get("cross"), new EditorActions.GridStyleAction(mxGraphComponent.GRID_STYLE_CROSS)));
		menu.addSeparator();

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("zoom")));
		submenu.add(editor.bind(mxResources.get("zoomIn"), mxGraphActions.getZoomInAction()));
		submenu.add(editor.bind(mxResources.get("zoomOut"), mxGraphActions.getZoomOutAction()));
		submenu.addSeparator(); 
		submenu.add(editor.bind(mxResources.get("actualSize"), mxGraphActions.getZoomActualAction()));
		submenu.add(editor.bind(mxResources.get("page"), new EditorActions.ZoomPolicyAction(mxGraphComponent.ZOOM_POLICY_PAGE)));
		submenu.add(editor.bind(mxResources.get("width"), new EditorActions.ZoomPolicyAction(mxGraphComponent.ZOOM_POLICY_WIDTH)));
		submenu.addSeparator(); 

		final JMenu zoomLevelsMenu = (JMenu) submenu.add(new JMenu("Presets"));
		zoomLevelsMenu.add(editor.bind("400%", new EditorActions.ScaleAction(4)));
		zoomLevelsMenu.add(editor.bind("200%", new EditorActions.ScaleAction(2)));
		zoomLevelsMenu.add(editor.bind("150%", new EditorActions.ScaleAction(1.5)));
		zoomLevelsMenu.add(editor.bind("100%", new EditorActions.ScaleAction(1)));
		zoomLevelsMenu.add(editor.bind("75%", new EditorActions.ScaleAction(0.75)));
		zoomLevelsMenu.add(editor.bind("50%", new EditorActions.ScaleAction(0.5)));
		zoomLevelsMenu.addSeparator();
		zoomLevelsMenu.add(editor.bind(mxResources.get("custom"), new EditorActions.ScaleAction(0)));

		final JMenu zoomOptionsMenu = (JMenu) submenu.add(new JMenu("Options"));
		zoomOptionsMenu.add(new EditorActions.TogglePropertyItem(graphComponent, mxResources.get("centerZoom"),
				"CenterZoom", true));
		zoomOptionsMenu.add(new EditorActions.TogglePropertyItem(graphComponent, mxResources.get("centerPage"),
				"CenterPage", true, e -> {
					if (graphComponent.isPageVisible() && graphComponent.isCenterPage()) {
						graphComponent.zoomAndCenter();
					}
				}));
		zoomOptionsMenu.add(new EditorActions.TogglePropertyItem(graphComponent, mxResources.get("zoomToSelection"),
				"KeepSelectionVisibleOnZoom", true));


		// Creates the format menu
		menu = add(new JMenu(mxResources.get("format")));

		populateFormatMenu(menu, editor);

		// Creates the shape menu
		menu = add(new JMenu(mxResources.get("shape")));

		populateShapeMenu(menu, editor);

		// Creates the diagram menu
		menu = add(new JMenu(mxResources.get("diagram")));

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("background")));

		submenu.add(editor.bind(mxResources.get("backgroundColor"), new EditorActions.BackgroundAction()));
		submenu.add(editor.bind(mxResources.get("backgroundImage"), new EditorActions.BackgroundImageAction()));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("pageBackground"), new EditorActions.PageBackgroundAction()));


		submenu = (JMenu) menu.add(new JMenu(mxResources.get("layout")));

		submenu.add(editor.graphLayout("verticalHierarchical", true));
		submenu.add(editor.graphLayout("horizontalHierarchical", true));

		submenu.addSeparator();

		submenu.add(editor.graphLayout("verticalPartition", false));
		submenu.add(editor.graphLayout("horizontalPartition", false));

		submenu.addSeparator();

		submenu.add(editor.graphLayout("verticalStack", false));
		submenu.add(editor.graphLayout("horizontalStack", false));

		submenu.addSeparator();

		submenu.add(editor.graphLayout("verticalTree", true));
		submenu.add(editor.graphLayout("horizontalTree", true));

		submenu.addSeparator();

		submenu.add(editor.graphLayout("placeEdgeLabels", false));
		submenu.add(editor.graphLayout("parallelEdges", false));

		submenu.addSeparator();

		submenu.add(editor.graphLayout("organicLayout", true));
		submenu.add(editor.graphLayout("circleLayout", true));

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("selection")));

		submenu.add(editor.bind(mxResources.get("selectPath"), new EditorActions.SelectShortestPathAction(false)));
		submenu.add(editor.bind(mxResources.get("selectDirectedPath"), new EditorActions.SelectShortestPathAction(true)));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("selectTree"), new EditorActions.SelectSpanningTreeAction(false)));
		submenu.add(editor.bind(mxResources.get("selectDirectedTree"), new EditorActions.SelectSpanningTreeAction(true)));

		menu.addSeparator();

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("stylesheet")));

		submenu.add(editor.bind(mxResources.get("basicStyle"),
				new EditorActions.StylesheetAction("/basic-style.xml")));
		submenu.add(editor.bind(mxResources.get("defaultStyle"), new EditorActions.StylesheetAction(
				"/default-style.xml")));

		createDeveloperMenu();

		// Creates the options menu
		menu = add(new JMenu(mxResources.get("options")));

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("display")));
//		submenu.add(new EditorActions.TogglePropertyItem(graphComponent, mxResources.get("buffering"), "TripleBuffered", true));

		submenu.add(new EditorActions.TogglePropertyItem(graphComponent, mxResources.get("preferPageSize"), "PreferPageSize", true, new ActionListener()
		{
			/**
			 * 
			 */
			public void actionPerformed(ActionEvent e)
			{
				graphComponent.zoomAndCenter();
			}
		}));

		// TODO: This feature is not yet implemented
		//submenu.add(new EditorActions.TogglePropertyItem(graphComponent, mxResources
		//		.get("pageBreaks"), "PageBreaksVisible", true));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("tolerance"), new EditorActions.PromptPropertyAction(graphComponent, "Tolerance")));

		submenu.add(editor.bind(mxResources.get("dirty"), new EditorActions.ToggleDirtyAction()));

		menu.addSeparator();

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("dragAndDrop")));

		submenu.add(new EditorActions.TogglePropertyItem(graphComponent, mxResources.get("dragEnabled"), "DragEnabled"));
		submenu.add(new EditorActions.TogglePropertyItem(graph, mxResources.get("dropEnabled"), "DropEnabled"));

		submenu.addSeparator();

		submenu.add(new EditorActions.TogglePropertyItem(graphComponent.getGraphHandler(), mxResources.get("imagePreview"), "ImagePreview"));

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("labels")));

		submenu.add(new EditorActions.TogglePropertyItem(graph, mxResources.get("htmlLabels"), "HtmlLabels", true));
		submenu.add(new EditorActions.TogglePropertyItem(graph, mxResources.get("showLabels"), "LabelsVisible", true));

		submenu.addSeparator();

		submenu.add(new EditorActions.TogglePropertyItem(graph, mxResources.get("moveEdgeLabels"), "EdgeLabelsMovable"));
		submenu.add(new EditorActions.TogglePropertyItem(graph, mxResources.get("moveVertexLabels"), "VertexLabelsMovable"));

		submenu.addSeparator();

		submenu.add(new EditorActions.TogglePropertyItem(graphComponent, mxResources.get("handleReturn"), "EnterStopsCellEditing"));

		menu.addSeparator();

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("connections")));

		submenu.add(new EditorActions.TogglePropertyItem(graphComponent, mxResources.get("connectable"), "Connectable"));
		submenu.add(new EditorActions.TogglePropertyItem(graph, mxResources.get("connectableEdges"), "ConnectableEdges"));

		submenu.addSeparator();

		submenu.add(new EditorActions.ToggleCreateTargetItem(editor, mxResources.get("createTarget")));
		submenu.add(new EditorActions.TogglePropertyItem(graph, mxResources.get("disconnectOnMove"), "DisconnectOnMove"));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("connectMode"), new EditorActions.ToggleConnectModeAction()));

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("validation")));

		submenu.add(new EditorActions.TogglePropertyItem(graph, mxResources.get("allowDanglingEdges"), "AllowDanglingEdges"));
		submenu.add(new EditorActions.TogglePropertyItem(graph, mxResources.get("cloneInvalidEdges"), "CloneInvalidEdges"));

		submenu.addSeparator();

		submenu.add(new EditorActions.TogglePropertyItem(graph, mxResources.get("allowLoops"), "AllowLoops"));
		submenu.add(new EditorActions.TogglePropertyItem(graph, mxResources.get("multigraph"), "Multigraph"));

		// Creates the Look and Feel menu
		menu.addSeparator();
		submenu = (JMenu) menu.add(new JMenu("Look & Feel"));
		UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
		for (int i = 0; i < lafs.length; i++) {
			final String clazz = lafs[i].getClassName();
			submenu.add(new AbstractAction(lafs[i].getName()) {
				private static final long serialVersionUID = 7588919504149148501L;

				public void actionPerformed(ActionEvent e) {
					editor.setLookAndFeel(clazz);
				}
			});
		}

		// Creates the help menu
		menu = add(new JMenu(mxResources.get("help")));

		item = menu.add(new JMenuItem(mxResources.get("aboutGraphEditor")));
		item.addActionListener(new ActionListener()
		{
			/*
			 * (non-Javadoc)
			 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
			 */
			public void actionPerformed(ActionEvent e)
			{
				editor.about();
			}
		});
	}

	public void createDeveloperMenu() {
		// Creates a developer menu
//		menu = add(new JMenu("Generate"));
//		menu.add(editor.bind("Null Graph", new InsertGraph(GraphType.NULL, aGraph)));
//		menu.add(editor.bind("Complete Graph", new InsertGraph(GraphType.COMPLETE, aGraph)));
//		menu.add(editor.bind("Grid", new InsertGraph(GraphType.GRID, aGraph)));
//		menu.add(editor.bind("Bipartite", new InsertGraph(GraphType.BIPARTITE, aGraph)));
//		menu.add(editor.bind("Complete Bipartite", new InsertGraph(GraphType.COMPLETE_BIPARTITE, aGraph)));
//		menu.add(editor.bind("Knight's Graph", new InsertGraph(GraphType.KNIGHT, aGraph)));
//		menu.add(editor.bind("King's Graph", new InsertGraph(GraphType.KING, aGraph)));
//		menu.add(editor.bind("Petersen", new InsertGraph(GraphType.PETERSEN, aGraph)));
//		menu.add(editor.bind("Path", new InsertGraph(GraphType.PATH, aGraph)));
//		menu.add(editor.bind("Star", new InsertGraph(GraphType.STAR, aGraph)));
//		menu.add(editor.bind("Wheel", new InsertGraph(GraphType.WHEEL, aGraph)));
//		menu.add(editor.bind("Friendship Windmill", new InsertGraph(GraphType.FRIENDSHIP_WINDMILL, aGraph)));
//		menu.add(editor.bind("Full Windmill", new InsertGraph(GraphType.FULL_WINDMILL, aGraph)));
//		menu.add(editor.bind("Knight's Tour", new InsertGraph(GraphType.KNIGHT_TOUR, aGraph)));
//		menu.addSeparator();
//		menu.add(editor.bind("Simple Random", new InsertGraph(GraphType.SIMPLE_RANDOM, aGraph)));
//		menu.add(editor.bind("Simple Random Tree", new InsertGraph(GraphType.SIMPLE_RANDOM_TREE, aGraph)));
//		menu.addSeparator();
//		menu.add(editor.bind("Reset Style", new InsertGraph(GraphType.RESET_STYLE, aGraph)));

		menu = add(new JMenu("Analyze"));
		menu.add(editor.bind("Properties", new AnalyzeGraph(AnalyzeType.PROPERTIES, aGraph, context)));
		menu.add(editor.bind("Color coding...", new AnalyzeGraph(AnalyzeType.COLOR_CODING, aGraph, context)));
		menu.add(editor.bind("Edge Scaling...", new AnalyzeGraph(AnalyzeType.EDGE_SCALING, aGraph, context)));
		menu.add(editor.bind("BFS Directed", new InsertGraph(GraphType.BFS_DIR, aGraph)));
		menu.add(editor.bind("BFS Undirected", new InsertGraph(GraphType.BFS_UNDIR, aGraph)));
		menu.add(editor.bind("DFS Directed", new InsertGraph(GraphType.DFS_DIR, aGraph)));
		menu.add(editor.bind("DFS Undirected", new InsertGraph(GraphType.DFS_UNDIR, aGraph)));
		menu.add(editor.bind("Complementary", new AnalyzeGraph(AnalyzeType.COMPLEMENTARY, aGraph, context)));
		menu.add(editor.bind("Regularity", new AnalyzeGraph(AnalyzeType.REGULARITY, aGraph, context)));
		menu.add(editor.bind("Dijkstra", new InsertGraph(GraphType.DIJKSTRA, aGraph)));
		menu.add(editor.bind("Bellman-Ford", new InsertGraph(GraphType.BELLMAN_FORD, aGraph)));
		menu.add(editor.bind("Floyd-Roy-Warshall", new AnalyzeGraph(AnalyzeType.FLOYD_ROY_WARSHALL, aGraph, context)));
		menu.add(editor.bind("Get Components", new AnalyzeGraph(AnalyzeType.COMPONENTS, aGraph, context)));
//		menu.add(editor.bind("Make Connected", new AnalyzeGraph(AnalyzeType.MAKE_CONNECTED, aGraph)));
//		menu.add(editor.bind("Make Simple", new AnalyzeGraph(AnalyzeType.MAKE_SIMPLE, aGraph)));
		menu.add(editor.bind("One Spanning Tree", new AnalyzeGraph(AnalyzeType.ONE_SPANNING_TREE, aGraph, context)));
//		menu.add(editor.bind("Make tree directed", new InsertGraph(GraphType.MAKE_TREE_DIRECTED, aGraph)));
//		menu.add(editor.bind("Is directed", new AnalyzeGraph(AnalyzeType.IS_DIRECTED, aGraph)));
//		menu.add(editor.bind("Indegree", new InsertGraph(GraphType.INDEGREE, aGraph)));
//		menu.add(editor.bind("Outdegree", new InsertGraph(GraphType.OUTDEGREE, aGraph)));
//		menu.add(editor.bind("Is cut vertex", new InsertGraph(GraphType.IS_CUT_VERTEX, aGraph)));
//		menu.add(editor.bind("Get cut vertices", new AnalyzeGraph(AnalyzeType.GET_CUT_VERTEXES, aGraph)));
//		menu.add(editor.bind("Get cut edges", new AnalyzeGraph(AnalyzeType.GET_CUT_EDGES, aGraph)));
		menu.add(editor.bind("Get sources", new AnalyzeGraph(AnalyzeType.GET_SOURCES, aGraph, context)));
		menu.add(editor.bind("Get sinks", new AnalyzeGraph(AnalyzeType.GET_SINKS, aGraph, context)));
//		menu.add(editor.bind("Is biconnected", new AnalyzeGraph(AnalyzeType.IS_BICONNECTED, aGraph)));
	}

	/**
	 * Adds menu items to the given shape menu. This is factored out because
	 * the shape menu appears in the menubar and also in the popupmenu.
	 */
	public static void populateShapeMenu(JMenu menu, BasicGraphEditor editor)
	{
		menu.add(editor.bind("Choose vertex shape...", new EditorActions.ChangeVertexShapeAction()));
		//menu.add(editor.bind(mxResources.get("home"), mxGraphActions.getHomeAction(), "/mx_shape_images/house.gif"));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("exitGroup"), mxGraphActions.getExitGroupAction(), "/mx_shape_images/up.gif"));
		menu.add(editor.bind(mxResources.get("enterGroup"), mxGraphActions.getEnterGroupAction(),
				"/mx_shape_images/down.gif"));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("group"), mxGraphActions.getGroupAction(), "/mx_shape_images/group.gif"));
		menu.add(editor.bind(mxResources.get("ungroup"), mxGraphActions.getUngroupAction(),
				"/mx_shape_images/ungroup.gif"));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("removeFromGroup"), mxGraphActions.getRemoveFromParentAction()));

		menu.add(editor.bind(mxResources.get("updateGroupBounds"), mxGraphActions.getUpdateGroupBoundsAction()));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("collapse"), mxGraphActions.getCollapseAction(),
				"/mx_shape_images/collapse.gif"));
		menu.add(editor.bind(mxResources.get("expand"), mxGraphActions.getExpandAction(), "/mx_shape_images/expand.gif"));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("toBack"), mxGraphActions.getToBackAction(), "/mx_shape_images/toback.gif"));
		menu.add(editor.bind(mxResources.get("toFront"), mxGraphActions.getToFrontAction(),
				"/mx_shape_images/tofront.gif"));

		menu.addSeparator();

		JMenu submenu = (JMenu) menu.add(new JMenu(mxResources.get("align")));

		submenu.add(editor.bind(mxResources.get("left"), new EditorActions.AlignCellsAction(mxConstants.ALIGN_LEFT),
				"/mx_shape_images/alignleft.gif"));
		submenu.add(editor.bind(mxResources.get("center"), new EditorActions.AlignCellsAction(mxConstants.ALIGN_CENTER),
				"/mx_shape_images/aligncenter.gif"));
		submenu.add(editor.bind(mxResources.get("right"), new EditorActions.AlignCellsAction(mxConstants.ALIGN_RIGHT),
				"/mx_shape_images/alignright.gif"));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("top"), new EditorActions.AlignCellsAction(mxConstants.ALIGN_TOP),
				"/mx_shape_images/aligntop.gif"));
		submenu.add(editor.bind(mxResources.get("middle"), new EditorActions.AlignCellsAction(mxConstants.ALIGN_MIDDLE),
				"/mx_shape_images/alignmiddle.gif"));
		submenu.add(editor.bind(mxResources.get("bottom"), new EditorActions.AlignCellsAction(mxConstants.ALIGN_BOTTOM),
				"/mx_shape_images/alignbottom.gif"));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("autosize"), new EditorActions.AutosizeAction()));

	}

	/**
	 * Adds menu items to the given format menu. This is factored out because
	 * the format menu appears in the menubar and also in the popupmenu.
	 */
	public static void populateFormatMenu(JMenu menu, BasicGraphEditor editor)
	{
		JMenu submenu = (JMenu) menu.add(new JMenu(mxResources.get("background")));

		submenu.add(editor.bind(mxResources.get("fillcolor"), new EditorActions.ColorAction("Fillcolor", mxConstants.STYLE_FILLCOLOR),
				"/mx_shape_images/fillcolor.gif"));
		submenu.add(editor.bind(mxResources.get("gradient"), new EditorActions.ColorAction("Gradient", mxConstants.STYLE_GRADIENTCOLOR)));

		submenu.addSeparator();

		//submenu.add(editor.bind(mxResources.get("image"), new EditorActions.PromptValueAction(mxConstants.STYLE_IMAGE, "Image")));
		submenu.add(editor.bind(mxResources.get("shadow"), new EditorActions.ToggleAction(mxConstants.STYLE_SHADOW)));

		submenu.add(editor.bind(mxResources.get("opacity"), new EditorActions.PromptValueAction(mxConstants.STYLE_OPACITY, "Opacity (0-100)")));

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("label")));

		submenu.add(editor.bind(mxResources.get("fontcolor"), new EditorActions.ColorAction("Fontcolor", mxConstants.STYLE_FONTCOLOR),
				"/mx_shape_images/fontcolor.gif"));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("labelFill"), new EditorActions.ColorAction("Label Fill", mxConstants.STYLE_LABEL_BACKGROUNDCOLOR)));
		submenu.add(editor.bind(mxResources.get("labelBorder"), new EditorActions.ColorAction("Label Border", mxConstants.STYLE_LABEL_BORDERCOLOR)));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("rotateLabel"), new EditorActions.ToggleAction(mxConstants.STYLE_HORIZONTAL, true)));

		submenu.add(editor.bind(mxResources.get("textOpacity"), new EditorActions.PromptValueAction(mxConstants.STYLE_TEXT_OPACITY, "Opacity (0-100)")));

		submenu.addSeparator();

		JMenu subsubmenu = (JMenu) submenu.add(new JMenu(mxResources.get("position")));

		subsubmenu.add(editor.bind(mxResources.get("top"), new EditorActions.SetLabelPositionAction(mxConstants.ALIGN_TOP, mxConstants.ALIGN_BOTTOM)));
		subsubmenu.add(editor.bind(mxResources.get("middle"),
				new EditorActions.SetLabelPositionAction(mxConstants.ALIGN_MIDDLE, mxConstants.ALIGN_MIDDLE)));
		subsubmenu.add(editor.bind(mxResources.get("bottom"), new EditorActions.SetLabelPositionAction(mxConstants.ALIGN_BOTTOM, mxConstants.ALIGN_TOP)));

		subsubmenu.addSeparator();

		subsubmenu.add(editor.bind(mxResources.get("left"), new EditorActions.SetLabelPositionAction(mxConstants.ALIGN_LEFT, mxConstants.ALIGN_RIGHT)));
		subsubmenu.add(editor.bind(mxResources.get("center"),
				new EditorActions.SetLabelPositionAction(mxConstants.ALIGN_CENTER, mxConstants.ALIGN_CENTER)));
		subsubmenu.add(editor.bind(mxResources.get("right"), new EditorActions.SetLabelPositionAction(mxConstants.ALIGN_RIGHT, mxConstants.ALIGN_LEFT)));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("wordWrap"), new EditorActions.KeyValueAction(mxConstants.STYLE_WHITE_SPACE, "wrap")));
		submenu.add(editor.bind(mxResources.get("noWordWrap"), new EditorActions.KeyValueAction(mxConstants.STYLE_WHITE_SPACE, null)));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("hide"), new EditorActions.ToggleAction(mxConstants.STYLE_NOLABEL)));

		menu.addSeparator();

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("line")));

		submenu.add(editor.bind(mxResources.get("linecolor"), new EditorActions.ColorAction("Linecolor", mxConstants.STYLE_STROKECOLOR),
				"/mx_shape_images/linecolor.gif"));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("orthogonal"), new EditorActions.ToggleAction(mxConstants.STYLE_ORTHOGONAL)));
		submenu.add(editor.bind(mxResources.get("dashed"), new EditorActions.ToggleAction(mxConstants.STYLE_DASHED)));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("linewidth"), new EditorActions.PromptValueAction(mxConstants.STYLE_STROKEWIDTH, "Linewidth")));

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("connector")));

		submenu.add(editor.bind(mxResources.get("straight"), new EditorActions.SetStyleAction("straight"),
				"/mx_shape_images/straight.gif"));

		submenu.add(editor.bind(mxResources.get("horizontal"), new EditorActions.SetStyleAction(""), "/mx_shape_images/connect.gif"));
		submenu.add(editor.bind(mxResources.get("vertical"), new EditorActions.SetStyleAction("vertical"),
				"/mx_shape_images/vertical.gif"));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("entityRelation"), new EditorActions.SetStyleAction("edgeStyle=mxEdgeStyle.EntityRelation"),
				"/mx_shape_images/entity.gif"));
		submenu.add(editor.bind(mxResources.get("arrow"), new EditorActions.SetStyleAction("arrow"), "/mx_shape_images/arrow.gif"));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("plain"), new EditorActions.ToggleAction(mxConstants.STYLE_NOEDGESTYLE)));

		menu.addSeparator();

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("linestart")));

		submenu.add(editor.bind(mxResources.get("open"), new EditorActions.KeyValueAction(mxConstants.STYLE_STARTARROW, mxConstants.ARROW_OPEN),
				"/mx_shape_images/open_start.gif"));
		submenu.add(editor.bind(mxResources.get("classic"), new EditorActions.KeyValueAction(mxConstants.STYLE_STARTARROW, mxConstants.ARROW_CLASSIC),
				"/mx_shape_images/classic_start.gif"));
		submenu.add(editor.bind(mxResources.get("block"), new EditorActions.KeyValueAction(mxConstants.STYLE_STARTARROW, mxConstants.ARROW_BLOCK),
				"/mx_shape_images/block_start.gif"));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("diamond"), new EditorActions.KeyValueAction(mxConstants.STYLE_STARTARROW, mxConstants.ARROW_DIAMOND),
				"/mx_shape_images/diamond_start.gif"));
		submenu.add(editor.bind(mxResources.get("oval"), new EditorActions.KeyValueAction(mxConstants.STYLE_STARTARROW, mxConstants.ARROW_OVAL),
				"/mx_shape_images/oval_start.gif"));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("none"), new EditorActions.KeyValueAction(mxConstants.STYLE_STARTARROW, mxConstants.NONE)));
		submenu.add(editor.bind(mxResources.get("size"), new EditorActions.PromptValueAction(mxConstants.STYLE_STARTSIZE, "Linestart Size")));

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("lineend")));

		submenu.add(editor.bind(mxResources.get("open"), new EditorActions.KeyValueAction(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_OPEN),
				"/mx_shape_images/open_end.gif"));
		submenu.add(editor.bind(mxResources.get("classic"), new EditorActions.KeyValueAction(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC),
				"/mx_shape_images/classic_end.gif"));
		submenu.add(editor.bind(mxResources.get("block"), new EditorActions.KeyValueAction(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_BLOCK),
				"/mx_shape_images/block_end.gif"));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("diamond"), new EditorActions.KeyValueAction(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_DIAMOND),
				"/mx_shape_images/diamond_end.gif"));
		submenu.add(editor.bind(mxResources.get("oval"), new EditorActions.KeyValueAction(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_OVAL),
				"/mx_shape_images/oval_end.gif"));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("none"), new EditorActions.KeyValueAction(mxConstants.STYLE_ENDARROW, mxConstants.NONE)));
		submenu.add(editor.bind(mxResources.get("size"), new EditorActions.PromptValueAction(mxConstants.STYLE_ENDSIZE, "Lineend Size")));

		menu.addSeparator();

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("alignment")));

		submenu.add(editor.bind(mxResources.get("left"), new EditorActions.KeyValueAction(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_LEFT),
				"/mx_shape_images/left.gif"));
		submenu.add(editor.bind(mxResources.get("center"), new EditorActions.KeyValueAction(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_CENTER),
				"/mx_shape_images/center.gif"));
		submenu.add(editor.bind(mxResources.get("right"), new EditorActions.KeyValueAction(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_RIGHT),
				"/mx_shape_images/right.gif"));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("top"), new EditorActions.KeyValueAction(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_TOP),
				"/mx_shape_images/top.gif"));
		submenu.add(editor.bind(mxResources.get("middle"), new EditorActions.KeyValueAction(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_MIDDLE),
				"/mx_shape_images/middle.gif"));
		submenu.add(editor.bind(mxResources.get("bottom"), new EditorActions.KeyValueAction(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_BOTTOM),
				"/mx_shape_images/bottom.gif"));

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("spacing")));

		submenu.add(editor.bind(mxResources.get("top"), new EditorActions.PromptValueAction(mxConstants.STYLE_SPACING_TOP, "Top Spacing")));
		submenu.add(editor.bind(mxResources.get("right"), new EditorActions.PromptValueAction(mxConstants.STYLE_SPACING_RIGHT, "Right Spacing")));
		submenu.add(editor.bind(mxResources.get("bottom"), new EditorActions.PromptValueAction(mxConstants.STYLE_SPACING_BOTTOM, "Bottom Spacing")));
		submenu.add(editor.bind(mxResources.get("left"), new EditorActions.PromptValueAction(mxConstants.STYLE_SPACING_LEFT, "Left Spacing")));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("global"), new EditorActions.PromptValueAction(mxConstants.STYLE_SPACING, "Spacing")));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("sourceSpacing"), new EditorActions.PromptValueAction(mxConstants.STYLE_SOURCE_PERIMETER_SPACING,
				mxResources.get("sourceSpacing"))));
		submenu.add(editor.bind(mxResources.get("targetSpacing"), new EditorActions.PromptValueAction(mxConstants.STYLE_TARGET_PERIMETER_SPACING,
				mxResources.get("targetSpacing"))));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("perimeter"), new EditorActions.PromptValueAction(mxConstants.STYLE_PERIMETER_SPACING,
				"Perimeter Spacing")));

		submenu = (JMenu) menu.add(new JMenu(mxResources.get("direction")));

		submenu.add(editor.bind(mxResources.get("north"), new EditorActions.KeyValueAction(mxConstants.STYLE_DIRECTION, mxConstants.DIRECTION_NORTH)));
		submenu.add(editor.bind(mxResources.get("east"), new EditorActions.KeyValueAction(mxConstants.STYLE_DIRECTION, mxConstants.DIRECTION_EAST)));
		submenu.add(editor.bind(mxResources.get("south"), new EditorActions.KeyValueAction(mxConstants.STYLE_DIRECTION, mxConstants.DIRECTION_SOUTH)));
		submenu.add(editor.bind(mxResources.get("west"), new EditorActions.KeyValueAction(mxConstants.STYLE_DIRECTION, mxConstants.DIRECTION_WEST)));

		submenu.addSeparator();

		submenu.add(editor.bind(mxResources.get("rotation"), new EditorActions.PromptValueAction(mxConstants.STYLE_ROTATION, "Rotation (0-360)")));

		menu.addSeparator();

		menu.add(editor.bind(mxResources.get("rounded"), new EditorActions.ToggleAction(mxConstants.STYLE_ROUNDED)));

		//menu.add(editor.bind(mxResources.get("style"), new EditorActions.StyleAction()));
	}

	/**
	 *
	 */
	public static class InsertGraph extends AbstractAction
	{

		/**
		 * 
		 */
		private static final long serialVersionUID = 4010463992665008365L;

		/**
		 * 
		 */
		protected GraphType graphType;

		protected mxAnalysisGraph aGraph;

		/**
		 * @param aGraph 
		 * 
		 */
		public InsertGraph(GraphType tree, mxAnalysisGraph aGraph)
		{
			this.graphType = tree;
			this.aGraph = aGraph;
		}

		/**
		 * 
		 */
		public void actionPerformed(ActionEvent e)
		{
			if (e.getSource() instanceof mxGraphComponent)
			{
				mxGraphComponent graphComponent = (mxGraphComponent) e.getSource();
				mxGraph graph = graphComponent.getGraph();

				// dialog = new FactoryConfigDialog();
				String dialogText = "";
				if (graphType == GraphType.NULL)
					dialogText = "Configure null graph";
				else if (graphType == GraphType.COMPLETE)
					dialogText = "Configure complete graph";
				else if (graphType == GraphType.NREGULAR)
					dialogText = "Configure n-regular graph";
				else if (graphType == GraphType.GRID)
					dialogText = "Configure grid graph";
				else if (graphType == GraphType.BIPARTITE)
					dialogText = "Configure bipartite graph";
				else if (graphType == GraphType.COMPLETE_BIPARTITE)
					dialogText = "Configure complete bipartite graph";
				else if (graphType == GraphType.BFS_DIR)
					dialogText = "Configure BFS algorithm";
				else if (graphType == GraphType.BFS_UNDIR)
					dialogText = "Configure BFS algorithm";
				else if (graphType == GraphType.DFS_DIR)
					dialogText = "Configure DFS algorithm";
				else if (graphType == GraphType.DFS_UNDIR)
					dialogText = "Configure DFS algorithm";
				else if (graphType == GraphType.DIJKSTRA)
					dialogText = "Configure Dijkstra's algorithm";
				else if (graphType == GraphType.BELLMAN_FORD)
					dialogText = "Configure Bellman-Ford algorithm";
				else if (graphType == GraphType.MAKE_TREE_DIRECTED)
					dialogText = "Configure make tree directed algorithm";
				else if (graphType == GraphType.KNIGHT_TOUR)
					dialogText = "Configure knight's tour";
				else if (graphType == GraphType.GET_ADJ_MATRIX)
					dialogText = "Configure adjacency matrix";
				else if (graphType == GraphType.FROM_ADJ_MATRIX)
					dialogText = "Input adjacency matrix";
				else if (graphType == GraphType.PETERSEN)
					dialogText = "Configure Petersen graph";
				else if (graphType == GraphType.WHEEL)
					dialogText = "Configure Wheel graph";
				else if (graphType == GraphType.STAR)
					dialogText = "Configure Star graph";
				else if (graphType == GraphType.PATH)
					dialogText = "Configure Path graph";
				else if (graphType == GraphType.FRIENDSHIP_WINDMILL)
					dialogText = "Configure Friendship Windmill graph";
				else if (graphType == GraphType.INDEGREE)
					dialogText = "Configure indegree analysis";
				else if (graphType == GraphType.OUTDEGREE)
					dialogText = "Configure outdegree analysis";
				GraphConfigDialog dialog = new GraphConfigDialog(graphType, dialogText);
				dialog.configureLayout(graph, graphType, aGraph);
				dialog.setModal(true);
				Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
				Dimension frameSize = dialog.getSize();
				dialog.setLocation(screenSize.width / 2 - (frameSize.width / 2), screenSize.height / 2 - (frameSize.height / 2));
				dialog.setVisible(true);
			}
		}
	}

	/**
	 *
	 */
	public static class AnalyzeGraph extends AbstractAction
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 6926170745240507985L;

		mxAnalysisGraph aGraph;
		SNTGraphComponent component;
		SNTGraphAdapter adapter;
		Context context;

		/**
		 * 
		 */
		protected AnalyzeType analyzeType;

		/**
		 * Examples for calling analysis methods from mxGraphStructure 
		 */
		public AnalyzeGraph(AnalyzeType analyzeType, mxAnalysisGraph aGraph, Context context)
		{
			this.analyzeType = analyzeType;
			this.aGraph = aGraph;
			this.context = context;
		}

		private void printReport() {
			boolean isConnected = mxGraphStructure.isConnected(aGraph);
			boolean isSimple = mxGraphStructure.isSimple(aGraph);
			boolean isDirected = mxGraphProperties.isDirected(aGraph.getProperties(), mxGraphProperties.DEFAULT_DIRECTED);
			boolean isTree = mxGraphStructure.isTree(aGraph);

			System.out.println("Graph properties");
			System.out.println("\tConnected: " + isConnected);
			try {
				boolean isBiconnected = mxGraphStructure.isBiconnected(aGraph);
				System.out.println("\tBiconnected: " + isBiconnected);
			} catch (Exception ignored) {
				// do nothing
			}
			System.out.println("\tSimple: " + isSimple);
			System.out.println("\tDirected: " + isDirected);
			System.out.println("\tTree: " + isTree);

			boolean isCyclicDirected = mxGraphStructure.isCyclicDirected(aGraph);
			System.out.println("\tCyclic directed: " + isCyclicDirected);
			boolean isCyclicUndirected = mxGraphStructure.isCyclicUndirected(aGraph);
			System.out.println("\tCyclic undirected: " + isCyclicUndirected);
		}

		protected void doColorCoding() {
			final Map<String, Object> input = new HashMap<>();
			input.put("adapter", adapter);
			context.getService(CommandService.class).run(GraphAdapterMapperCmd.class, true, input);
		}

		protected void doEdgeScaling() {
			JTextField maxWidthField = new JTextField(SNTUtils.formatDouble(5, 2), 5);
			JRadioButton linearScaleButton = new JRadioButton("linear");
			linearScaleButton.setSelected(true);
			JRadioButton logScaleButton = new JRadioButton("log");
			ButtonGroup bg = new ButtonGroup();
			bg.add(linearScaleButton);
			bg.add(logScaleButton);
			JPanel myPanel = new JPanel();
			myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));
			myPanel.add(new JLabel("<html><b>Edge Scaling Parameters"));
			myPanel.add(new JLabel("<html>Max line width"));
			myPanel.add(maxWidthField);
			myPanel.add(new JLabel("<html><br>Scale"));
			myPanel.add(linearScaleButton);
			myPanel.add(logScaleButton);
			double newMax = 1;
			String scale = "linear";
			int result = JOptionPane.showConfirmDialog(null, myPanel,
					"Please Specify Options", JOptionPane.OK_CANCEL_OPTION);
			if (result == JOptionPane.OK_OPTION) {
				double input = GuiUtils.extractDouble(maxWidthField);
				if (Double.isNaN(input) || input <= 0) {
					GuiUtils.errorPrompt("Max width must be > 0");
					return;
				}
				newMax = input;
				for (Enumeration<AbstractButton> buttons = bg.getElements(); buttons.hasMoreElements();) {
					AbstractButton button = buttons.nextElement();
					if (button.isSelected()) {
						scale = button.getText();
					}
				}
			} else {
				return;
			}
			Object[] cells = adapter.getEdgeToCellMap().values().toArray();
			if (cells.length == 0) {
				return;
			}
			double newMin = 0.1;
			double minWeight = Double.MAX_VALUE;
			double maxWeight = -Double.MAX_VALUE;
			SNTGraph<Object, DefaultWeightedEdge> sntGraph = adapter.getSourceGraph();
			// First get the range of observed weights, negative weights are allowed.
			for (Object cell : cells) {
				mxICell mxc = (mxICell) cell;
				if (!mxc.isEdge()) continue;
				double weight = sntGraph.getEdgeWeight((DefaultWeightedEdge)adapter.getCellToEdgeMap().get(mxc));
				if (weight < minWeight) {minWeight = weight;}
				if (weight > maxWeight) {maxWeight = weight;}
			}
			if (scale.equals("linear")) {
				// Map the input interval onto a new interval [newMin, newMax]
				for (Object cell : cells) {
					mxICell mxc = (mxICell) cell;
					if (!mxc.isEdge()) continue;
					double weight = sntGraph.getEdgeWeight((DefaultWeightedEdge)adapter.getCellToEdgeMap().get(mxc));
					double scaledWeight = newMin + ((newMax - newMin) / (maxWeight - minWeight)) * (weight - minWeight);
					adapter.setCellStyles(mxConstants.STYLE_STROKEWIDTH, String.valueOf(scaledWeight), new Object[]{mxc});
				}
			}
			else if (scale.equals("log")) {
				// If the min edge weight is not 1, shift all values so that the minimum is 1.
				// This is necessary for correct log function behavior at the minimum value (i.e., log(1) == 0)
				double rightShift = 0;
				double leftShift = 0;
				if (minWeight < 1) {
					rightShift = 1 - minWeight;
				}
				else if (minWeight > 1) {
					leftShift = 1 - minWeight;
				}
				for (Object cell : cells) {
					mxICell mxc = (mxICell) cell;
					if (!mxc.isEdge()) continue;
					double weight = sntGraph.getEdgeWeight(
							(DefaultWeightedEdge)adapter.getCellToEdgeMap().get(mxc)
					) + rightShift + leftShift;
					double k = newMax / Math.log(maxWeight + rightShift + leftShift);
					double scaledWeight = k * Math.log(weight) + newMin;
					adapter.setCellStyles(mxConstants.STYLE_STROKEWIDTH, String.valueOf(scaledWeight), new Object[]{mxc});
				}
			}
		}

		protected void doComplementary() {
			adapter.getModel().beginUpdate();

			mxGraphStructure.complementaryGraph(aGraph);

			mxGraphStructure.setDefaultGraphStyle(aGraph, true);
			adapter.getModel().endUpdate();
		}

		protected void doRegularity() {
			try
			{
				int regularity = mxGraphStructure.regularity(aGraph);
				System.out.println("Graph regularity is: " + regularity);
			}
			catch (StructuralException e1)
			{
				System.out.println("The graph is irregular");
			}
		}

		protected void doComponents() {
			Object[][] components = mxGraphStructure.getGraphComponents(aGraph);
			mxIGraphModel model = aGraph.getGraph().getModel();

			for (int i = 0; i < components.length; i++)
			{
				System.out.print("Component " + i + " :");

				for (int j = 0; j < components[i].length; j++)
				{
					System.out.print(" " + model.getValue(components[i][j]));
				}

				System.out.println(".");
			}

			System.out.println("Number of components: " + components.length);
		}

		protected void doMakeConnected() {
			adapter.getModel().beginUpdate();

			if (!mxGraphStructure.isConnected(aGraph))
			{
				mxGraphStructure.makeConnected(aGraph);
				mxGraphStructure.setDefaultGraphStyle(aGraph, false);
			}

			adapter.getModel().endUpdate();
		}

		protected void doGetSources() {

			Object[] cells = aGraph.getGraph().getChildVertices(aGraph.getGraph().getDefaultParent());
			List<Object> selectionCells = new ArrayList<>();
			for (Object c : cells) {
				mxICell mxc = (mxICell) c;
				if (aGraph.getGraph().getOutgoingEdges(mxc).length > 0) {
					System.out.println("Source: " + aGraph.getGraph().getModel().getValue(mxc));
					selectionCells.add(mxc);
				}
			}
			if (!selectionCells.isEmpty()) {
				aGraph.getGraph().setSelectionCells(selectionCells);
			}
		}

		protected void doGetSinks() {
			Object[] cells = aGraph.getGraph().getChildVertices(aGraph.getGraph().getDefaultParent());
			List<Object> selectionCells = new ArrayList<>();
			for (Object c : cells) {
				mxICell mxc = (mxICell) c;
				if (aGraph.getGraph().getOutgoingEdges(mxc).length == 0) {
					System.out.println("Sink: " + aGraph.getGraph().getModel().getValue(mxc));
					selectionCells.add(mxc);
				}
			}
			if (!selectionCells.isEmpty()) {
				aGraph.getGraph().setSelectionCells(selectionCells);
			}
		}

		public void actionPerformed(ActionEvent e)
		{
			if (e.getSource() instanceof SNTGraphComponent)
			{
				component = (SNTGraphComponent) e.getSource();
				adapter = (SNTGraphAdapter) component.getGraph();
				aGraph.setGraph(adapter);

				if (analyzeType == AnalyzeType.PROPERTIES)
				{
					printReport();
				}
				else if (analyzeType == AnalyzeType.COLOR_CODING)
				{
					doColorCoding();
				}
				else if (analyzeType == AnalyzeType.EDGE_SCALING) {
					doEdgeScaling();
				}
				else if (analyzeType == AnalyzeType.COMPLEMENTARY)
				{
					doComplementary();
				}
				else if (analyzeType == AnalyzeType.REGULARITY)
				{
					doRegularity();
				}
				else if (analyzeType == AnalyzeType.COMPONENTS)
				{
					doComponents();
				}
				else if (analyzeType == AnalyzeType.MAKE_CONNECTED)
				{
					doMakeConnected();
				}
				else if (analyzeType == AnalyzeType.MAKE_SIMPLE)
				{
					mxGraphStructure.makeSimple(aGraph);
				}
				else if (analyzeType == AnalyzeType.ONE_SPANNING_TREE)
				{
					try
					{
						adapter.getModel().beginUpdate();
						aGraph.getGenerator().oneSpanningTree(aGraph, true, true);
						mxGraphStructure.setDefaultGraphStyle(aGraph, false);
						adapter.getModel().endUpdate();
					}
					catch (StructuralException e1)
					{
						System.out.println("The graph must be simple and connected");
					}
				}
				else if (analyzeType == AnalyzeType.GET_CUT_VERTEXES)
				{
					Object[] cutVertices = mxGraphStructure.getCutVertices(aGraph);

					System.out.print("Cut vertices of the graph are: [");
					mxIGraphModel model = aGraph.getGraph().getModel();

					for (int i = 0; i < cutVertices.length; i++)
					{
						System.out.print(" " + model.getValue(cutVertices[i]));
					}

					System.out.println(" ]");
				}
				else if (analyzeType == AnalyzeType.GET_CUT_EDGES)
				{
					Object[] cutEdges = mxGraphStructure.getCutEdges(aGraph);

					System.out.print("Cut edges of the graph are: [");
					mxIGraphModel model = aGraph.getGraph().getModel();

					for (int i = 0; i < cutEdges.length; i++)
					{
						System.out.print(" " + Integer.parseInt((String) model.getValue(aGraph.getTerminal(cutEdges[i], true))) + "-"
								+ Integer.parseInt((String) model.getValue(aGraph.getTerminal(cutEdges[i], false))));
					}

					System.out.println(" ]");
				}
				else if (analyzeType == AnalyzeType.GET_SOURCES)
				{
					doGetSources();
				}
				else if (analyzeType == AnalyzeType.GET_SINKS)
				{
					doGetSinks();
				}
				else if (analyzeType == AnalyzeType.FLOYD_ROY_WARSHALL)
				{
					
					ArrayList<Object[][]> FWIresult = new ArrayList<Object[][]>();
					try
					{
						//only this line is needed to get the result from Floyd-Roy-Warshall, the rest is code for displaying the result
						FWIresult = mxTraversal.floydRoyWarshall(aGraph);

						Object[][] dist = FWIresult.get(0);
						Object[][] paths = FWIresult.get(1);
						Object[] vertices = aGraph.getChildVertices(aGraph.getGraph().getDefaultParent());
						int vertexNum = vertices.length;
						System.out.println("Distances are:");

						for (int i = 0; i < vertexNum; i++)
						{
							System.out.print("[");

							for (int j = 0; j < vertexNum; j++)
							{
								System.out.print(" " + Math.round((Double) dist[i][j] * 100.0) / 100.0);
							}

							System.out.println("] ");
						}

						System.out.println("Path info:");

						mxCostFunction costFunction = aGraph.getGenerator().getCostFunction();
						mxGraphView view = aGraph.getGraph().getView();

						for (int i = 0; i < vertexNum; i++)
						{
							System.out.print("[");

							for (int j = 0; j < vertexNum; j++)
							{
								if (paths[i][j] != null)
								{
									System.out.print(" " + costFunction.getCost(view.getState(paths[i][j])));
								}
								else
								{
									System.out.print(" -");
								}
							}

							System.out.println(" ]");
						}

						try
						{
							Object[] path = mxTraversal.getWFIPath(aGraph, FWIresult, vertices[0], vertices[vertexNum - 1]);
							System.out.print("The path from " + costFunction.getCost(view.getState(vertices[0])) + " to "
									+ costFunction.getCost((view.getState(vertices[vertexNum - 1]))) + " is:");

							for (int i = 0; i < path.length; i++)
							{
								System.out.print(" " + costFunction.getCost(view.getState(path[i])));
							}

							System.out.println();
						}
						catch (StructuralException e1)
						{
							System.out.println(e1);
						}
					}
					catch (StructuralException e2)
					{
						System.out.println(e2);
					}
				}
			}
		}
	};
};