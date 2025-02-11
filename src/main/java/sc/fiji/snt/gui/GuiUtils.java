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

package sc.fiji.snt.gui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.jidesoft.plaf.LookAndFeelFactory;
import com.jidesoft.popup.JidePopup;
import com.jidesoft.utils.ProductNames;

import ij.IJ;
import ij.ImageJ;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.border.EmptyBorder;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.NumberFormatter;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.awt.AWTWindows;
import org.scijava.ui.swing.SwingDialog;
import org.scijava.ui.swing.widget.SwingColorWidget;
import org.scijava.util.ColorRGB;
import org.scijava.util.PlatformUtils;
import org.scijava.util.Types;

import sc.fiji.snt.SNTPrefs;
import sc.fiji.snt.SNTUtils;

/** Misc. utilities for SNT's GUI. */
public class GuiUtils {

	public static final String LAF_LIGHT = FlatLightLaf.NAME;
	public static final String LAF_LIGHT_INTJ = FlatIntelliJLaf.NAME;
	public static final String LAF_DARK = FlatDarkLaf.NAME;
	public static final String LAF_DARCULA = FlatDarculaLaf.NAME;
	public static final String LAF_DEFAULT  = "System default";

	/** The default sorting weight for the Plugins>Neuroanatomy> submenu */
	// define it here in case we need to change sorting priority again later on
	public static final double DEFAULT_MENU_WEIGHT = org.scijava.MenuEntry.DEFAULT_WEIGHT;

	private static SplashScreen splashScreen;
	private static LookAndFeel existingLaf;
	final private Component parent;
	private JidePopup popup;
	private boolean popupExceptionTriggered;
	private int timeOut = 2500;
	private Color background = Color.WHITE;
	private Color foreground = Color.BLACK;

	public GuiUtils(final Component parent) {
		this.parent = parent;
		if (parent != null) {
			background = parent.getBackground();
			foreground = parent.getForeground();
		}
	}

	public GuiUtils() {
		this(null);
	}

	public void error(final String msg) {
		error(msg, "SNT v" + SNTUtils.VERSION);
	}

	public void error(final String msg, final String title) {
		centeredDialog(msg, title, JOptionPane.ERROR_MESSAGE);
	}

	public JDialog floatingMsg(final String msg, final boolean autodismiss) {
		final JDialog dialog = new FloatingDialog(msg);
		if (autodismiss) GuiUtils.setAutoDismiss(dialog);
		makeVisible(dialog, false);
		return dialog;
	}

	public void tempMsg(final String msg) {
		tempMsg(msg, -1);
	}

	public void tempMsg(final String msg, final int location) {
		SwingUtilities.invokeLater(() -> {
			try {
				if (popup != null && popup.isVisible())
					popup.hidePopupImmediately();
				popup = getPopup(msg);
				if (location < 0) {
					popup.showPopup();
				} else {
					popup.showPopup(location);
				}
				popup.showPopup();
			} catch (final Error ignored) {
				if (!popupExceptionTriggered) {
					errorPrompt("<HTML><body><div style='width:500;'>Notification mechanism "
							+ "failed when notifying of:<br>\"<i>"+ msg +"</i>\".<br>"
							+ "All future notifications will be displayed in Console.");
					popupExceptionTriggered = true;
				}
				if (msg.startsWith("<")) { //HTML formatted
					// https://stackoverflow.com/a/3608319
					String cleanedMsg = msg.replaceAll("(?s)<[^>]*>(\\s*<[^>]*>)*", " ");
					cleanedMsg = cleanedMsg.replaceAll("&amp;", "&");
					System.out.println("[INFO] [SNT] " + cleanedMsg.trim());
				} else {
					System.out.println("[INFO] [SNT] " + msg);
				}
			}
		});
	}

	public static void showHTMLDialog(final String msg, final String title) {
		final HTMLDialog dialog = new GuiUtils().new HTMLDialog(msg, title, false);
		SwingUtilities.invokeLater(() -> {
			dialog.setVisible(true);
		});
	}

	public static boolean isLegacy3DViewerAvailable() {
		return Types.load("ij3d.Image3DUniverse") != null;
	}

	private JidePopup getPopup(final String msg) {
		final JLabel label = getLabel(msg);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		final JidePopup popup = new JidePopup();
		popup.getContentPane().add(label);
		label.setBackground(background);
		label.setForeground(foreground);
		popup.getContentPane().setBackground(background);
		popup.setBackground(foreground);
		if (parent != null) {
			popup.setOwner(parent);
			popup.setMaximumSize(parent.getSize());
		}
		popup.setFocusable(false);
		popup.setTransient(timeOut > 0);
		popup.setMovable(false);
		popup.setDefaultMoveOperation(JidePopup.HIDE_ON_MOVED);
		popup.setEnsureInOneScreen(true);
		popup.setTimeout(timeOut);
		return popup;

	}

	public void setTmpMsgTimeOut(final int mseconds) { // 0: no timeout, always visible
		timeOut = mseconds;
	}

	public int yesNoDialog(final String msg, final String title, final String yesButtonLabel, final String noButtonLabel) {
		return yesNoDialog(new Object[] { getLabel(msg) }, title, new String[] {yesButtonLabel, noButtonLabel});
	}

	public int yesNoDialog(final String msg, final String title) {
		return yesNoDialog(new Object[] { getLabel(msg) }, title, null);
	}

	public Result yesNoPrompt(final String message, final String title) {
		final int result = yesNoDialog(message, (title == null) ? SNTUtils.getReadableVersion() : title);
		switch (result) {
		case JOptionPane.YES_OPTION:
			return Result.YES_OPTION;
		case JOptionPane.NO_OPTION:
			return Result.NO_OPTION;
		case JOptionPane.CANCEL_OPTION:
			return Result.CANCEL_OPTION;
		default:
			return Result.CLOSED_OPTION;
		}
	}

	private int yesNoDialog(final Object[] components, final String title,
		final String[] buttonLabels)
	{
		final JOptionPane optionPane = new JOptionPane(components,
			JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION, null,
			buttonLabels);
		final JDialog d = optionPane.createDialog(parent, title);
		// Work around prompt being displayed behind splashScreen on MacOS
		final boolean splashScreenDisplaying = splashScreen != null && splashScreen.isVisible();
		if (splashScreenDisplaying) splashScreen.setVisible(false);
		makeVisible(d, true);
		d.dispose();
		if (splashScreenDisplaying) splashScreen.setVisible(true);
		final Object result = optionPane.getValue();
		if (result instanceof Integer) {
			return (Integer) result;
		}
		else if (buttonLabels != null &&
				result instanceof String)
		{
			return result.equals(buttonLabels[0]) ? JOptionPane.YES_OPTION
				: JOptionPane.NO_OPTION;
		}
		else {
			return SwingDialog.UNKNOWN_OPTION;
		}
	}

	private void makeVisible(final JDialog dialog, final boolean forceBringToFront) {
		dialog.setVisible(true);
		dialog.toFront();
		// work around a bug in openjdk and MacOS in which prompts
		// are not frontmost if the component hierarchy is > 3
		if (forceBringToFront && PlatformUtils.isMac() && !dialog.hasFocus() && parent != null
				&& parent instanceof Window) {
			try {
				((Window) parent).toBack();
				Thread.sleep(75);
				dialog.toFront();
			} catch (final InterruptedException e) {
				// ignored
			}
		}
	}

	public boolean getConfirmation(final String msg, final String title) {
		return (yesNoDialog(msg, title) == JOptionPane.YES_OPTION);
	}

	public void error(final String msg, final String title, final String helpURI) {
		final JOptionPane optionPane = new JOptionPane(getLabel(msg), JOptionPane.ERROR_MESSAGE,
				JOptionPane.YES_NO_OPTION, null, new String[] { "Online Help", "OK" });
		final JDialog d = optionPane.createDialog(parent, title);
		makeVisible(d, true);
		d.dispose();
		if ("Online Help".equals(optionPane.getValue()))
			openURL(helpURI);
	}

	public boolean getConfirmation(final String msg, final String title, final String yesLabel, final String noLabel) {
		return (yesNoDialog(msg, title, yesLabel, noLabel) == JOptionPane.YES_OPTION);
	}

	public String getChoice(final String message, final String title, final String[] choices,
			final String defaultChoice) {
		final String selectedValue = (String) JOptionPane.showInputDialog(parent, //
				message, title, JOptionPane.QUESTION_MESSAGE, null, choices,
				(defaultChoice == null) ? choices[0] : defaultChoice);
		return selectedValue;
	}

	public List<String> getMultipleChoices(final String message, final String title, final String[] choices) {
		final JList<String> list = new JList<>(choices);
		JOptionPane.showMessageDialog(
				parent, new JScrollPane(list), title, JOptionPane.QUESTION_MESSAGE);
		return list.getSelectedValuesList();
	}

	public boolean[] getPersistentConfirmation(final String msg, final String title) {
		return getConfirmationAndOption(msg, title, "Remember my choice and do not prompt me again", false);
	}

	public boolean[] getConfirmationAndOption(final String msg, final String title, final String checkboxLabel, final boolean checkboxDefault) {
		final JCheckBox checkbox = new JCheckBox();
		checkbox.setText(getWrappedText(checkbox, checkboxLabel));
		checkbox.setSelected(checkboxDefault);
		final Object[] params = { getLabel(msg), checkbox };
		final boolean result = yesNoDialog(params, title, null) == JOptionPane.YES_OPTION;
		return new boolean[] { result, checkbox.isSelected() };
	}

	/* returns true if user does not want to be warned again */
	public Boolean getPersistentWarning(final String msg, final String title) {
		return getPersistentDialog(msg, title, JOptionPane.WARNING_MESSAGE);
	}

	private Boolean getPersistentDialog(final String msg, final String title, final int type) {
		final JPanel msgPanel = new JPanel();
		msgPanel.setLayout(new BorderLayout());
		msgPanel.add(getLabel(msg), BorderLayout.CENTER);
		final JCheckBox checkbox = new JCheckBox();
		checkbox.setText(getWrappedText(checkbox, "Do not remind me again"));
		msgPanel.add(checkbox, BorderLayout.SOUTH);
		if (JOptionPane.showConfirmDialog(parent, msgPanel, title, JOptionPane.DEFAULT_OPTION,
				type) != JOptionPane.OK_OPTION)
			return null;
		else
			return checkbox.isSelected();
	}

	public String getString(final String promptMsg, final String promptTitle,
		final String defaultValue)
	{
		return (String) getObj(promptMsg, promptTitle, defaultValue);
	}

	public Color getColor(final String title, final Color defaultValue) {
		return SwingColorWidget.showColorDialog(parent, title, defaultValue);
	}

	public ColorRGB getColorRGB(final String title, final Color defaultValue,
		final String... panes)
	{
		final Color color = getColor(title, defaultValue, panes);
		if (color == null) return null;
		return new ColorRGB(color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * Simplified color chooser.
	 *
	 * @param title the title of the chooser dialog
	 * @param defaultValue the initial color set in the chooser
	 * @param panes the panes a list of strings specifying which tabs should be
	 *          displayed. In most platforms this includes: "Swatches", "HSB" and
	 *          "RGB". Note that e.g., the GTK L&amp;F may only include the
	 *          default GtkColorChooser pane
	 * @return the color
	 */
	public Color getColor(final String title, final Color defaultValue,
		final String... panes)
	{

		assert SwingUtilities.isEventDispatchThread();

		final JColorChooser chooser = new JColorChooser(defaultValue != null
			? defaultValue : Color.WHITE);

		// remove preview pane
		chooser.setPreviewPanel(new JPanel());

		// remove spurious panes
		List<String> allowedPanels = new ArrayList<>();
		if (panes != null) {
			allowedPanels = Arrays.asList(panes);
			for (final AbstractColorChooserPanel accp : chooser.getChooserPanels()) {
				if (!allowedPanels.contains(accp.getDisplayName()) && chooser
					.getChooserPanels().length > 1) chooser.removeChooserPanel(accp);
			}
		}

		class ColorTracker implements ActionListener {

			private final JColorChooser chooser;
			private Color color;

			public ColorTracker(final JColorChooser c) {
				chooser = c;
			}

			@Override
			public void actionPerformed(final ActionEvent e) {
				color = chooser.getColor();
			}

			public Color getColor() {
				return color;
			}
		}

		final ColorTracker ok = new ColorTracker(chooser);
		final JDialog dialog = JColorChooser.createDialog(parent, title, true,
			chooser, ok, null);
		makeVisible(dialog, true);
		return ok.getColor();
	}

	public Double getDouble(final String promptMsg, final String promptTitle,
		final Number defaultValue)
	{
		try {
			final NumberFormat nf = NumberFormat.getInstance(Locale.US);
			final Number number = nf.parse((String) getObj(promptMsg, promptTitle,
				defaultValue));
			return number.doubleValue();
		}
		catch (final NullPointerException ignored) {
			return null; // user pressed cancel
		}
		catch (final ParseException ignored) {
			return Double.NaN; // invalid user input
		}
	}

	public float[] getRange(final String promptMsg, final String promptTitle, final float[] defaultRange) {
		final String s = getString(promptMsg, promptTitle, SNTUtils.formatDouble(defaultRange[0], 3) + "-"
				+ SNTUtils.formatDouble(defaultRange[1], 3));
		if (s == null)
			return null; // user pressed cancel
		final float[] values = new float[2];
		try {
			// see https://stackoverflow.com/a/51283413
			final String regex = "([-+]?\\d*\\.?\\d*)\\s*-\\s*([-+]?\\d*\\.?\\d*)";
			final Pattern pattern = Pattern.compile(regex);
			final Matcher matcher = pattern.matcher(s);
			matcher.find();
			values[0] = Float.parseFloat(matcher.group(1));
			values[1] = Float.parseFloat(matcher.group(2));
			return values;
		} catch (final Exception ignored) {
			values[0] = Float.NaN;
			values[1] = Float.NaN;
		}
		return values;
	}

	public File saveFile(final String title, final File file,
		final List<String> allowedExtensions)
	{
		File chosenFile = null;
		final JFileChooser chooser = fileChooser(title, file, JFileChooser.FILES_ONLY, allowedExtensions);
		if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
			chosenFile = chooser.getSelectedFile();
			if (chosenFile != null && allowedExtensions != null && allowedExtensions.size() == 1) {
				final String path = chosenFile.getAbsolutePath();
				final String extension = allowedExtensions.get(0);
				if (!path.endsWith(extension))
					chosenFile = new File(path + extension);
			}
			if (chosenFile.exists()
					&& !getConfirmation(chosenFile.getAbsolutePath() + " already exists. Do you want to replace it?",
							"Override File?")) {
				return null;
			}
		}
		return chosenFile;
	}

	
	@SuppressWarnings("unused")
	private File openFile(final String title, final File file,
		final List<String> allowedExtensions)
	{
		final JFileChooser chooser = fileChooser(title, file,
			JFileChooser.FILES_ONLY, allowedExtensions);
		if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
			return chooser.getSelectedFile();
		return null;
	}

	@SuppressWarnings("unused")
	private File chooseDirectory(final String title, final File file) {
		final JFileChooser chooser = fileChooser(title, file,
			JFileChooser.DIRECTORIES_ONLY, null);
		if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
			return chooser.getSelectedFile();
		return null;
	}

	private JFileChooser fileChooser(final String title, final File file,
		final int type, final List<String> allowedExtensions)
	{
		final JFileChooser chooser = new JFileChooser(file);
		if (file != null) {
			if (file.exists()) {
				chooser.setSelectedFile(file);
			} else {
				chooser.setCurrentDirectory(file.getParentFile());
			}
		}
		chooser.setDialogTitle(title);
		chooser.setFileSelectionMode(type);
		chooser.setDragEnabled(true);
		if (allowedExtensions != null && !allowedExtensions.isEmpty()) {
			chooser.setFileFilter(new FileFilter() {

				@Override
				public String getDescription() {
					return String.join(",", allowedExtensions);
				}

				@Override
				public boolean accept(final File f) {
					if (f.isDirectory()) {
						return true;
					}
					else {
						final String filename = f.getName().toLowerCase();
						for (final String ext : allowedExtensions) {
							if (filename.endsWith(ext)) return true;
						}
						return false;
					}
				}
			});
		}
		return chooser;
	}

	private Object getObj(final String promptMsg, final String promptTitle,
		final Object defaultValue)
	{
		return JOptionPane.showInputDialog(parent, promptMsg, promptTitle,
			JOptionPane.PLAIN_MESSAGE, null, null, defaultValue);
	}

	public void centeredMsg(final String msg, final String title) {
		centeredDialog(msg, title, JOptionPane.PLAIN_MESSAGE);
	}

	public void centeredMsg(final String msg, final String title, final String buttonLabel) {
		if (buttonLabel == null) {
			centeredMsg(msg, title);
		} else {
			final String defaultButtonLabel = UIManager.getString("OptionPane.okButtonText");
			UIManager.put("OptionPane.okButtonText", buttonLabel);
			centeredMsg(msg, title);
			UIManager.put("OptionPane.okButtonText", defaultButtonLabel);
		}
	}

	public JDialog dialog(final String msg, final JComponent component,
		final String title)
	{
		final Object[] params = { getLabel(msg), component };
		final JOptionPane optionPane = new JOptionPane(params,
			JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
		final JDialog dialog = optionPane.createDialog(title);
		if (parent != null) dialog.setLocationRelativeTo(parent);
		return dialog;
	}

	public boolean[] getOptions(final String msg, final String[] options,
		final boolean[] defaults, String title)
	{
		final JPanel panel = new JPanel(new GridLayout(options.length, 1));
		final JCheckBox[] checkboxes = new JCheckBox[options.length];
		for (int i = 0; i < options.length; i++) {
			panel.add(checkboxes[i] = new JCheckBox(options[i], defaults[i]));
		}
		final int result = JOptionPane.showConfirmDialog(parent, new Object[] { msg,
			panel }, title, JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.CANCEL_OPTION) return null;
		final boolean[] answers = new boolean[options.length];
		for (int i = 0; i < options.length; i++) {
			answers[i] = checkboxes[i].isSelected();
		}
		return answers;
	}

	private int centeredDialog(final String msg, final String title,
		final int type)
	{
		/* if SwingDialogs could be centered, we could simply use */
		// final SwingDialog d = new SwingDialog(getLabel(msg), type, false);
		// if (parent != null) d.setParent(parent);
		// return d.show();
		final JOptionPane optionPane = new JOptionPane(getLabel(msg), type,
			JOptionPane.DEFAULT_OPTION);
		final JDialog d = optionPane.createDialog(title);
		if (parent != null) {
			AWTWindows.centerWindow(parent.getBounds(), d);
			// we could also use d.setLocationRelativeTo(parent);
		}
		makeVisible(d, true);
		final Object result = optionPane.getValue();
		if ((!(result instanceof Integer)))
			return SwingDialog.UNKNOWN_OPTION;
		return (Integer) result;
	}

	public void addTooltip(final JComponent c, final String text) {
		final int length = c.getFontMetrics(c.getFont()).stringWidth(text);
		c.setToolTipText("<html>" + ((length > 500) ? "<body><div style='width:500;'>" : "") + text);
	}

	private JLabel getLabel(final String text) {
		if (text == null || text.startsWith("<")) {
			return new JLabel(text);
		}
		else {
			final JLabel label = new JLabel();
			label.setText(getWrappedText(label, text));
			return label;
		}
	}

	private String getWrappedText(final JComponent c, final String text) {
		final int width = c.getFontMetrics(c.getFont()).stringWidth(text);
		final int max = (parent == null) ? 500 : parent.getWidth();
		return "<html><body><div style='width:" + Math.min(width, max) + ";'>" +
			text;
	}

	public void blinkingError(final JComponent blinkingComponent,
		final String msg)
	{
		final Color prevColor = blinkingComponent.getForeground();
		final Color flashColor = Color.RED;
		final Timer blinkTimer = new Timer(400, new ActionListener() {

			private int count = 0;
			private final int maxCount = 100;
			private boolean on = false;

			@Override
			public void actionPerformed(final ActionEvent e) {
				if (count >= maxCount) {
					blinkingComponent.setForeground(prevColor);
					((Timer) e.getSource()).stop();
				}
				else {
					blinkingComponent.setForeground(on ? flashColor : prevColor);
					on = !on;
					count++;
				}
			}
		});
		blinkTimer.start();
		if (centeredDialog(msg, "Ongoing Operation",
			JOptionPane.PLAIN_MESSAGE) > Integer.MIN_VALUE)
		{ // Dialog
			// dismissed
			blinkTimer.stop();
		}
		blinkingComponent.setForeground(prevColor);
	}

	static JDialog showAboutDialog() {
		final JPanel main = new JPanel();
		main.add(SplashScreen.getIconAsLabel());
		final JPanel side = new JPanel();
		main.add(side);
		side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
		final JLabel title = new JLabel(SNTUtils.getReadableVersion());
		SplashScreen.assignStyle(title, 2);
		side.add(title);
		final JLabel subTitle = new JLabel("The ImageJ Framework for Neuroanatomy");
		SplashScreen.assignStyle(subTitle, 1);
		side.add(subTitle);
		side.add(new JLabel(" ")); // spacer
		final JLabel ijDetails = leftAlignedLabel(
				"ImageJ " + ImageJ.VERSION + ImageJ.BUILD + "  |  Java " + System.getProperty("java.version"), "", true);
		ijDetails.setAlignmentX(JLabel.CENTER_ALIGNMENT);
		ijDetails.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				if (IJ.getInstance() == null)
					new ij.plugin.JavaProperties().run("");
				else
					IJ.doCommand("ImageJ Properties");
			}

		});
		side.add(ijDetails);
		side.add(new JLabel(" ")); // spacer
		final JPanel urls = new JPanel();
		side.add(urls);
		JLabel url = leftAlignedLabel("Release Notes   ", "https://github.com/morphonets/SNT/releases", true);
		urls.add(url);
		url = leftAlignedLabel("Documentation   ", "https://imagej.net/SNT", true);
		urls.add(url);
		url = leftAlignedLabel("Forum   ", "https://forum.image.sc/tags/snt", true);
		urls.add(url);
		url = leftAlignedLabel("GitHub   ", "https://github.com/morphonets/SNT/", true);
		urls.add(url);
		url = leftAlignedLabel("Manuscript", "https://doi.org/10.1038/s41592-021-01105-7", true);
		urls.add(url);
		final JOptionPane optionPane = new JOptionPane(main, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
		final JDialog d = optionPane.createDialog("About SNT...");
		d.setLocationRelativeTo(null);
		d.setVisible(true);
		d.toFront();
		d.setAlwaysOnTop(!d.hasFocus()); // see makeVisible()
		return d;
	}

	/* Static methods */

	public static void initSplashScreen() {
		splashScreen = new SplashScreen();
		splashScreen.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				closeSplashScreen();
			}
		});
	}

	public static void closeSplashScreen() {
		if (splashScreen != null) splashScreen.close();
		splashScreen = null;
	}

	public static void collapseAllTreeNodes(final JTree tree) {
		final int row1 = (tree.isRootVisible()) ? 1 : 0;
		for (int i = row1; i < tree.getRowCount(); i++)
			tree.collapseRow(i);
	}

	public static void expandAllTreeNodes(final JTree tree) {
		for (int i = 0; i < tree.getRowCount(); i++)
			tree.expandRow(i);
	}

	public static void addSeparator(final JComponent component,
			final String heading, final boolean vgap, final GridBagConstraints c)
		{
			addSeparator(component, leftAlignedLabel(heading, null, true), vgap, c);
		}

	public static void addSeparator(final JComponent component,
		final JLabel label, final boolean vgap, final GridBagConstraints c)
	{
		final int previousTopGap = c.insets.top;
		final Font font = label.getFont();
		label.setFont(font.deriveFont((float) (font.getSize() * .85)));
		if (vgap) c.insets.top = (int) (component.getFontMetrics(font).getHeight() *
			1.5);
		component.add(label, c);
		if (vgap) c.insets.top = previousTopGap;
	}

	public static JMenuItem menubarButton(final Icon icon, final JMenuBar menuBar) {
		final JMenuItem mi = new JMenuItem(icon);
		mi.setBackground(menuBar.getBackground());
		mi.setPreferredSize(new Dimension(icon.getIconWidth() * 2, icon.getIconHeight()));
		mi.setMinimumSize(new Dimension(icon.getIconWidth() * 2, icon.getIconHeight()));
		if (!ij.IJ.isMacOSX()) {// icon displayed off-bounds on MacOS?
			mi.setMaximumSize(new Dimension(icon.getIconWidth() * 2, icon.getIconHeight()));
		}
		menuBar.add(javax.swing.Box.createHorizontalGlue());
		menuBar.add(mi);
		return mi;
	}

	public static JLabel leftAlignedLabel(final String text, final boolean enabled) {
		return leftAlignedLabel(text, null, enabled);
	}

	public static JLabel leftAlignedLabel(final String text, final String uri,
		final boolean enabled)
	{
		final JLabel label = new JLabel(text);
		label.setHorizontalAlignment(SwingConstants.LEFT);
		label.setEnabled(enabled);
		final Color fg = (enabled) ? label.getForeground() : getDisabledComponentColor(); // required
		label.setForeground(fg);														// for MACOS!?
		if (uri != null && Desktop.isDesktopSupported()) {
			label.addMouseListener(new MouseAdapter() {
				final int w = label.getFontMetrics(label.getFont()).stringWidth(label.getText());

				@Override
				public void mouseEntered(final MouseEvent e) {
					if (e.getX() <= w) {
						label.setForeground(Color.BLUE);
						label.setCursor(new Cursor(Cursor.HAND_CURSOR));
					}
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					label.setForeground(fg);
					label.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				}

				@Override
				public void mouseClicked(final MouseEvent e) {
					if (label.isEnabled() && e.getX() <= w) openURL(uri);
				}
			});
		}
		return label;
	}

	private static void openURL(final String uri) {
		try {
			Desktop.getDesktop().browse(new URI(uri));
		} catch (IOException | URISyntaxException ex) {
			if (uri != null && !uri.isEmpty())
				SNTUtils.log("Could not open " + uri);
		}
	}

	public static ImageIcon createIcon(final Color color, final int width,
		final int height)
	{
		if (color == null) return null;
		final BufferedImage image = new BufferedImage(width, height,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = image.createGraphics();
		graphics.setColor(color);
		graphics.fillRect(0, 0, width, height);
		graphics.setXORMode(Color.DARK_GRAY);
		graphics.drawRect(0, 0, width - 1, height - 1);
		image.flush();
		return new ImageIcon(image);
	}

	public static int getMenuItemHeight() {
		Font font = UIManager.getDefaults().getFont("CheckBoxMenuItem.font");
		if (font == null) font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
		final Canvas c = new Canvas();
		return c.getFontMetrics(font).getHeight();
	}

	public static JMenuItem menuItemWithoutAccelerator() {
		class JMenuItemAcc extends JMenuItem {
			// https://stackoverflow.com/a/1719250
			private static final long serialVersionUID = 1L;

			@Override
			public void setAccelerator(final KeyStroke keyStroke) {
				super.setAccelerator(keyStroke);
				getInputMap(WHEN_IN_FOCUSED_WINDOW).put(keyStroke, "none");
			}
		}
		return new JMenuItemAcc();
	}

	public static String ctrlKey() {
		return (PlatformUtils.isMac()) ? "Cmd" : "Ctrl";
	}

	public static String modKey() {
		return (PlatformUtils.isMac()) ? "Alt" : "Ctrl";
	}

	public static GridBagConstraints defaultGbc() {
		final GridBagConstraints cp = new GridBagConstraints();
		cp.anchor = GridBagConstraints.LINE_START;
		cp.gridwidth = GridBagConstraints.REMAINDER;
		cp.fill = GridBagConstraints.HORIZONTAL;
		cp.insets = new Insets(0, 0, 0, 0);
		cp.weightx = 1.0;
		cp.gridx = 0;
		cp.gridy = 0;
		return cp;
	}

	public static List<JMenuItem> getMenuItems(final JMenuBar menuBar) {
		final List<JMenuItem> list = new ArrayList<>();
		for (int i = 0; i < menuBar.getMenuCount(); i++) {
			final JMenu menu = menuBar.getMenu(i);
			getMenuItems(menu, list);
		}
		return list;
	}

	public static List<JMenuItem> getMenuItems(final JPopupMenu popupMenu) {
		final List<JMenuItem> list = new ArrayList<>();
		for (final MenuElement me : popupMenu.getSubElements()) {
			if (me == null) {
				continue;
			} else if (me instanceof JMenuItem) {
				list.add((JMenuItem) me);
			} else if (me instanceof JMenu) {
				getMenuItems((JMenu) me, list);
			}
		}
		return list;
	}

	private static void getMenuItems(final JMenu menu, final List<JMenuItem> holdingList) {
		for (int j = 0; j < menu.getItemCount(); j++) {
			final JMenuItem jmi = menu.getItem(j);
			if (jmi == null)
				continue;
			if (jmi instanceof JMenu) {
				getMenuItems((JMenu) jmi, holdingList);
			} else {
				holdingList.add(jmi);
			}
		}
	}

	public JTextField textField(final String placeholder) {
		return new TextFieldWithPlaceholder(placeholder);
	}

	public static JMenu helpMenu() {
		final JMenu helpMenu = new JMenu("Help");
		final String URL = "https://imagej.net/SNT";
		JMenuItem mi = menuItemTriggeringURL("Main Documentation Page", URL);
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.HOME));
		helpMenu.add(mi);
		helpMenu.addSeparator();
		mi = menuItemTriggeringURL("User Manual", URL + ":_Manual");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.BOOK_READER));
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("Screencasts", URL + ":_Screencasts");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.VIDEO));
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("Step-by-step Instructions", URL + ":_Step-By-Step_Instructions");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.FOOTPRINTS));
		helpMenu.add(mi);

		helpMenu.addSeparator();
		mi = menuItemTriggeringURL("Analysis", URL + ":_Analysis");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.CHART));
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("Reconstruction Viewer", URL + ":_Reconstruction_Viewer");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.CUBE));
		helpMenu.add(mi);

		helpMenu.addSeparator();
		mi = menuItemTriggeringURL("List of Shortcuts", URL + ":_Key_Shortcuts");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.KEYBOARD));
		helpMenu.add(mi);
		helpMenu.addSeparator();

		mi = menuItemTriggeringURL("Ask a Question", "https://forum.image.sc/tags/snt");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.COMMENTS));
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("FAQs", URL + ":_FAQ");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.QUESTION));
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("Known Issues", "https://github.com/morphonets/SNT/issues");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.BUG));
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("Release Notes", "https://github.com/morphonets/SNT/releases");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.NEWSPAPER));
		helpMenu.add(mi);

		helpMenu.addSeparator();
		mi = menuItemTriggeringURL("Scripting", URL + ":_Scripting");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.CODE));
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("Jupyter Notebooks", "https://github.com/morphonets/SNT/tree/master/notebooks");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.SCROLL));
		helpMenu.add(mi);
		helpMenu.addSeparator();

		mi = menuItemTriggeringURL("SNT's API", "https://morphonets.github.io/SNT/");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.CODE2));
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("SNT's Algorithms", "https://github.com/morphonets/SNT/blob/master/NOTES.md#algorithms");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.COGS));
		helpMenu.add(mi);
		mi = menuItemTriggeringURL("SNT Manuscript", "https://doi.org/10.1101/2020.07.13.179325");
		mi.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.FILE));
		helpMenu.add(mi);
		helpMenu.addSeparator();

		final JMenuItem about = new JMenuItem("About...");
		about.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.INFO));
		about.addActionListener(e -> showAboutDialog());
		helpMenu.add(about);

		return helpMenu;
	}

	public static JMenuItem menuItemTriggeringURL(final String label, final String URL) {
		final JMenuItem mi = new JMenuItem(label);
		mi.addActionListener(e -> IJ.runPlugIn("ij.plugin.BrowserLauncher", URL));
		return mi;
	}

	static class TextFieldWithPlaceholder extends JTextField {

		private static final long serialVersionUID = 1L;
		private String initialPlaceholder;
		private String placeholder;

		TextFieldWithPlaceholder(final String placeholder) {
			changePlaceholder(placeholder, true);
			setBorder(null);
		}

		void changePlaceholder(final String placeholder, final boolean overrideInitialPlaceholder) {
			this.placeholder = placeholder;
			if (overrideInitialPlaceholder) initialPlaceholder = placeholder;
			update(getGraphics());
		}

		void resetPlaceholder() {
			changePlaceholder(initialPlaceholder, false);
		}

		@Override
		protected void paintComponent(final java.awt.Graphics g) {
			super.paintComponent(g);
			if (getText().isEmpty() && !(FocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == this)) {
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
						RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setColor(Color.GRAY);
				g2.setFont(getFont().deriveFont(Font.ITALIC));
				g2.drawString(placeholder, 4, g2.getFontMetrics().getHeight());
				g2.dispose();
			}
		}

	}

	public static Color getDisabledComponentColor() {
		try {
			return UIManager.getColor("MenuItem.disabledForeground");
		}
		catch (final Exception ignored) {
			return Color.GRAY;
		}
	}

	public static JButton smallButton(final String text) {
		final double SCALE = .85;
		final JButton button = new JButton(text);
		final Font font = button.getFont();
		button.setFont(font.deriveFont((float) (font.getSize() * SCALE)));
		final Insets insets = button.getMargin();
		button.setMargin(new Insets((int) (insets.top * SCALE), (int) (insets.left *
			SCALE), (int) (insets.bottom * SCALE), (int) (insets.right * SCALE)));
		return button;
	}

	public static JSpinner integerSpinner(final int value, final int min,
		final int max, final int step, final boolean allowEditing)
	{
		final int maxDigits = Integer.toString(max).length();
		final SpinnerModel model = new SpinnerNumberModel(value, min, max, step);
		final JSpinner spinner = new JSpinner(model);
		final JFormattedTextField textfield = ((DefaultEditor) spinner.getEditor())
			.getTextField();
		textfield.setColumns(maxDigits);
		try {
			if (allowEditing) {
				((NumberFormatter) textfield.getFormatter()).setAllowsInvalid(false);
			}
			textfield.setEditable(allowEditing);
		} catch (final Exception ignored){
			textfield.setEditable(false);
		}
		return spinner;
	}

	public static JSpinner doubleSpinner(final double value, final double min,
		final double max, final double step, final int nDecimals)
	{
		final int maxDigits = SNTUtils.formatDouble(max, nDecimals).length();
		final SpinnerModel model = new SpinnerNumberModel(value, min, max, step);
		final JSpinner spinner = new JSpinner(model);
		final JFormattedTextField textfield = ((DefaultEditor) spinner.getEditor())
			.getTextField();
		textfield.setColumns(maxDigits);
		final NumberFormatter formatter = (NumberFormatter) textfield
			.getFormatter();
		StringBuilder decString = new StringBuilder();
		while (decString.length() <= nDecimals)
			decString.append("0");
		final DecimalFormat decimalFormat = new DecimalFormat("0." + decString);
		formatter.setFormat(decimalFormat);
		formatter.setAllowsInvalid(false);
//		textfield.addPropertyChangeListener(new PropertyChangeListener() {
//
//			@Override
//			public void propertyChange(final PropertyChangeEvent evt) {
//				if ("editValid".equals(evt.getPropertyName()) && Boolean.FALSE.equals(evt.getNewValue())) {
//
//					new GuiUtils(spinner).getPopup("Number must be between " + SNT.formatDouble(min, nDecimals)
//							+ " and " + SNT.formatDouble(max, nDecimals), spinner).showPopup();
//
//				}
//
//			}
//		});
		return spinner;
	}

	public static double extractDouble(final JTextField textfield) {
		try {
			final NumberFormat nf = NumberFormat.getInstance(Locale.US);
			final Number number = nf.parse(textfield.getText());
			return number.doubleValue();
		}
		catch (final NullPointerException | ParseException ignored) {
			return Double.NaN; // invalid user input
		}
	}

	public static void enableComponents(final java.awt.Container container,
		final boolean enable)
	{
		final Component[] components = container.getComponents();
		for (final Component component : components) {
			if (!(component instanceof JPanel)) component.setEnabled(enable); // otherwise
																																				// JPanel
																																				// background
																																				// will
																																				// change
			if (component instanceof java.awt.Container) {
				enableComponents((java.awt.Container) component, enable);
			}
		}
	}

	public static String micrometer() {
		return "\u00B5m";
	}

	/**
	 * Returns a more human readable representation of a length in micrometers.
	 * <p>
	 * E.g., scaledMicrometer(0.01,1) returns "1.0nm"
	 * </p>
	 *
	 * @param umLength the length in micrometers
	 * @param digits the number of output decimals
	 * @return the scaled unit
	 */
	public static String scaledMicrometer(final double umLength,
		final int digits)
	{
		String symbol = "";
		double length = 0;
		if (umLength < 0.0001) {
			length = umLength * 10000;
			symbol = "\u00C5";
		}
		if (umLength < 1) {
			length = umLength * 1000;
			symbol = "nm";
		}
		else if (umLength < 1000) {
			length = umLength;
			symbol = micrometer();
		}
		else if (umLength > 1000 && umLength < 10000) {
			length = umLength / 1000;
			symbol = "mm";
		}
		else if (umLength > 10000 && umLength < 1000000) {
			length = umLength / 10000;
			symbol = "cm";
		}
		else if (umLength > 1000000) {
			length = umLength / 1000000;
			symbol = "m";
		}
		else if (umLength > 1000000000) {
			length = umLength / 1000000000;
			symbol = "km";
		}
		return SNTUtils.formatDouble(length, digits) + symbol;
	}

	public static void errorPrompt(final String msg) {
		new GuiUtils().error(msg, "SNT v" + SNTUtils.VERSION);
	}

	public static String[] availableLookAndFeels() {
		return new String[] { LAF_DEFAULT, LAF_LIGHT, LAF_LIGHT_INTJ, LAF_DARK, LAF_DARCULA };
	}

	public static void setLookAndFeel() {
		storeExistingLookAndFeel();
		final String lafName = SNTPrefs.getLookAndFeel(); // never null
		if (existingLaf == null || !lafName.equals(existingLaf.getName()))
			setLookAndFeel(SNTPrefs.getLookAndFeel(), false);
	}

	private static void storeExistingLookAndFeel() {
		existingLaf = UIManager.getLookAndFeel();
		if (existingLaf instanceof FlatLaf) 
			existingLaf = null;
	}

	public static void restoreLookAndFeel() {
		try {
			if (existingLaf != null) UIManager.setLookAndFeel(existingLaf);
		} catch (final Error | Exception ignored) {
			// do nothing
		}
	}

	private static boolean setSystemLookAndFeel() {
		try {
			// With Ubuntu and java 8 we need to ensure we're using
			// GTK+ L&F otherwise no scaling occurs with hiDPI screens
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			LookAndFeelFactory.installDefaultLookAndFeelAndExtension();
			LookAndFeelFactory.setProductsUsed(ProductNames.PRODUCT_COMMON);
			return true;
			// checkGTKLookAndFeel();
		} catch (final Error | Exception ignored) {
			return false;
		}
	}

	public static boolean setLookAndFeel(final String lookAndFeelName, final boolean persistentChoice, final Component... componentsToUpdate) {
		boolean success;
		storeExistingLookAndFeel();
		// embedded menu bar make dialogs exaggeratedly wide in main UI
		UIManager.put("TitlePane.menuBarEmbedded", false);
		switch (lookAndFeelName) {
		case (LAF_LIGHT):
			success = FlatLightLaf.install();
			break;
		case (LAF_LIGHT_INTJ):
			success = FlatIntelliJLaf.install();
			break;
		case (LAF_DARK):
			success = FlatDarkLaf.install();
			break;
		case (LAF_DARCULA):
			success = FlatDarculaLaf.install();
			break;
		default:
			success = setSystemLookAndFeel();
			if (!success) existingLaf = null;
			break;
		}
		if (success && componentsToUpdate != null) {
			for (final Component component : componentsToUpdate) {
				if (component == null)
					continue;
				final Window window = (component instanceof Window) ? (Window)component
						: SwingUtilities.windowForComponent(component);
				try {
					if (window == null)
						SwingUtilities.updateComponentTreeUI(component);
					else
						SwingUtilities.updateComponentTreeUI(window);
				} catch (final Exception ex) {
					SNTUtils.error("", ex);
				}
			}
		}
		if (success && persistentChoice) {
			SNTPrefs.setLookAndFeel(lookAndFeelName);
		}
		return success;
	}

	/** HACK Font too big on ubuntu: https://stackoverflow.com/a/31345102 */
	@SuppressWarnings("unused")
	private static void checkGTKLookAndFeel() throws Exception {
		final LookAndFeel look = UIManager.getLookAndFeel();
		if (!look.getID().equals("GTK")) return;
		final int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
		if (dpi <= 72) return;
		final float scaleFont = dpi / 72;
		new JFrame();
		new JButton();
		new JComboBox<>();
		new JRadioButton();
		new JCheckBox();
		new JTextArea();
		new JTextField();
		new JTable();
		new JToggleButton();
		new JSpinner();
		new JSlider();
		new JTabbedPane();
		new JMenu();
		new JMenuBar();
		new JMenuItem();

		Object styleFactory;
		final Field styleFactoryField = look.getClass().getDeclaredField(
			"styleFactory");
		styleFactoryField.setAccessible(true);
		styleFactory = styleFactoryField.get(look);

		final Field defaultFontField = styleFactory.getClass().getDeclaredField(
			"defaultFont");
		defaultFontField.setAccessible(true);
		final Font defaultFont = (Font) defaultFontField.get(styleFactory);
		FontUIResource newFontUI;
		newFontUI = new FontUIResource(defaultFont.deriveFont(defaultFont
			.getSize() - scaleFont));
		defaultFontField.set(styleFactory, newFontUI);

		final Field stylesCacheField = styleFactory.getClass().getDeclaredField(
			"stylesCache");
		stylesCacheField.setAccessible(true);
		final Object stylesCache = stylesCacheField.get(styleFactory);
		final Map<?, ?> stylesMap = (Map<?, ?>) stylesCache;
		for (final Object mo : stylesMap.values()) {
			final Field f = mo.getClass().getDeclaredField("font");
			f.setAccessible(true);
			final Font fo = (Font) f.get(mo);
			f.set(mo, fo.deriveFont(fo.getSize() - scaleFont));
		}
	}

	public static void setAutoDismiss(final JDialog dialog) {
		final int DELAY = 2500;
		final Timer timer = new Timer(DELAY, e -> dialog.dispose());
		timer.setRepeats(false);
		dialog.addMouseListener(new MouseAdapter() {

			private long lastUpdate;

			@Override
			public void mouseClicked(final MouseEvent e) {
				dialog.dispose();
			}

			@Override
			public void mouseExited(final MouseEvent e) {
				if (System.currentTimeMillis() - lastUpdate > DELAY) dialog.dispose();
				else timer.start();
			}

			@Override
			public void mouseEntered(final MouseEvent e) {
				lastUpdate = System.currentTimeMillis();
				timer.stop();
			}

		});
		timer.start();
	}

	public void showHTMLDialog(final String msg, final String title, final boolean modal) {
		new HTMLDialog(msg, title, modal).setVisible(true);
	}

	/** Tweaked version of ij.gui.HTMLDialog that is aware of parent */
	private class HTMLDialog extends JDialog implements ActionListener, KeyListener, HyperlinkListener {

		private static final long serialVersionUID = 1L;
		private JEditorPane editorPane;

		public HTMLDialog(final String message, final String title, final boolean modal) {
			super();
			setModal(modal);
			setTitle(title);
			init(message);
		}

		@Override
		public void setVisible(final boolean b) {
			if (parent != null)
				setLocationRelativeTo(parent);
			else
				AWTWindows.centerWindow(this);
			super.setVisible(b);
		}

		private void init(String message) {
			getContentPane().setLayout(new BorderLayout());
			if (message == null)
				message = "";
			editorPane = new JEditorPane("text/html", "");
			editorPane.setEditable(false);
			final HTMLEditorKit kit = new HTMLEditorKit();
			editorPane.setEditorKit(kit);
			final StyleSheet styleSheet = kit.getStyleSheet();
			styleSheet.addRule("body{font-family:Verdana,sans-serif; font-size:11.5pt; margin:5px 10px 5px 10px;}"); // top
																														// right
																														// bottom
																														// left
			styleSheet.addRule("h1{font-size:18pt;}");
			styleSheet.addRule("h2{font-size:15pt;}");
			styleSheet.addRule("dl dt{font-face:bold;}");
			editorPane.setText(message); // display the html text with the above style
			editorPane.getActionMap().put("insert-break", new AbstractAction() {
				private static final long serialVersionUID = 1L;

				public void actionPerformed(final ActionEvent e) {
				}
			}); // suppress beep on <ENTER> key
			final JScrollPane scrollPane = new JScrollPane(editorPane);
			getContentPane().add(scrollPane);
			final JButton button = new JButton("OK");
			button.addActionListener(this);
			button.addKeyListener(this);
			editorPane.addKeyListener(this);
			editorPane.addHyperlinkListener(this);
			final JPanel panel = new JPanel();
			panel.add(button);
			getContentPane().add(panel, "South");
			setForeground(Color.black);
			pack();
			final Dimension screenD = Toolkit.getDefaultToolkit().getScreenSize();
			final Dimension dialogD = getSize();
			final int maxWidth = (int) (Math.min(0.70 * screenD.width, 800)); // max 70% of screen width, but not more
																				// than 800 pxl
			if (maxWidth > 400 && dialogD.width > maxWidth)
				dialogD.width = maxWidth;
			if (dialogD.height > 0.80 * screenD.height && screenD.height > 400) // max 80% of screen height
				dialogD.height = (int) (0.80 * screenD.height);
			setSize(dialogD);
		}

		public void actionPerformed(final ActionEvent e) {
			dispose();
		}

		public void keyPressed(final KeyEvent e) {
			final int keyCode = e.getKeyCode();
			if (keyCode == KeyEvent.VK_C) {
				if (editorPane.getSelectedText() == null || editorPane.getSelectedText().length() == 0)
					editorPane.selectAll();
				editorPane.copy();
				editorPane.select(0, 0);
			} else if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_W || keyCode == KeyEvent.VK_ESCAPE)
				dispose();
		}

		public void hyperlinkUpdate(final HyperlinkEvent e) {
			if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				final String url = e.getDescription(); // getURL does not work for relative links within document such
														// as "#top"
				if (url == null)
					return;
				if (url.startsWith("#"))
					editorPane.scrollToReference(url.substring(1));
				else {
					IJ.runPlugIn("ij.plugin.BrowserLauncher", url);
				}
			}
		}

		@Override
		public void keyReleased(final KeyEvent arg0) {
			// DO nothing
		}

		@Override
		public void keyTyped(final KeyEvent arg0) {
			// DO nothing
		}

	}


	private class FloatingDialog extends JDialog implements ComponentListener,
		WindowListener
	{

		private static final long serialVersionUID = 1L;

		public FloatingDialog(final String msg) {
			super();
			setUndecorated(true);
			setModal(false);
			setResizable(false);
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			setAlwaysOnTop(true);
			getContentPane().setBackground(background);
			setBackground(background);
			final JLabel label = getLabel(msg);
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setBorder(new EmptyBorder(10, 10, 10, 10));
			label.setBackground(background);
			label.setForeground(foreground);
			add(label);
			pack();
			centerOnParent();
			if (parent != null) parent.addComponentListener(this);
			setVisible(true);
			toFront();
		}

		@Override
		public void dispose() {
			if (parent != null) parent.removeComponentListener(this);
			super.dispose();
		}

		private void centerOnParent() {
			if (parent == null) return;
			final Point p = new Point(parent.getWidth() / 2 - getWidth() / 2, parent
				.getHeight() / 2 - getHeight() / 2);
			setLocation(p.x + parent.getX(), p.y + parent.getY());
		}

		private void recenter() {
			assert SwingUtilities.isEventDispatchThread();
			// setVisible(false);
			centerOnParent();
			// setVisible(true);
		}

		@Override
		public void componentResized(final ComponentEvent e) {
			recenter();
		}

		@Override
		public void componentMoved(final ComponentEvent e) {
			recenter();
		}

		@Override
		public void componentShown(final ComponentEvent e) {
			setVisible(true);
			toFront();
		}

		@Override
		public void componentHidden(final ComponentEvent e) {
			setVisible(false);
		}

		@Override
		public void windowClosing(final WindowEvent e) {
			setVisible(false);
		}

		@Override
		public void windowIconified(final WindowEvent e) {
			setVisible(false);
		}

		@Override
		public void windowDeiconified(final WindowEvent e) {
			setVisible(true);
			toFront();
		}

		@Override
		public void windowOpened(final WindowEvent e) {
			// do nothing
		}

		@Override
		public void windowClosed(final WindowEvent e) {
			setVisible(false);
		}

		@Override
		public void windowActivated(final WindowEvent e) {
			// do nothing
		}

		@Override
		public void windowDeactivated(final WindowEvent e) {
			// do nothing
		}

	}

}
