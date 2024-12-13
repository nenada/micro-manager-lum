package org.lumencor.targa;

import javax.swing.*;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

/**
 * Log view
 * @author Milos Jovanovic <milos@tehnocad.rs>
 */
public class LogView extends JPanel {
	private final JScrollPane logScrollPane_;                                                    ///< Text pane scroll view
	private final JPanel logToolbar_;                                                            ///< Log panel toolbar
	private final JTextPane logPane_;                                                            ///< Text pane
	private final JToggleButton btnLockScroll;                                                   ///< Scroll lock / unlock button
	private final Style logPaneStyle_;                                                           ///< Text pane current style

	/**
	 * Default class constructor
	 */
	public LogView()
	{
		setLayout(new BorderLayout());

		logScrollPane_ = new JScrollPane();

		Font font = new Font("Consolas", Font.PLAIN, 12);
		logPane_ = new JTextPane();
		logPane_.setEditable(false);
		//logPane_.setBackground(Color.getColor("#aaa"));
		logPane_.setFont(font);
		logPaneStyle_ = logPane_.addStyle("Color Style", null);
		try
		{
			logPane_.getStyledDocument().insertString(0, "\n", logPaneStyle_);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		logScrollPane_.getViewport().add(logPane_);
		add(logScrollPane_, BorderLayout.CENTER);

		SpringLayout toollayout = new SpringLayout();
		logToolbar_ = new JPanel();
		logToolbar_.setPreferredSize(new Dimension(30, 0));
		logToolbar_.setLayout(toollayout);
		add(logToolbar_, BorderLayout.LINE_START);

		int nsize = 16;
		ImageIcon icolock = new ImageIcon(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/org/micromanager/icons/locked.png")));
		ImageIcon icounlock = new ImageIcon(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/org/micromanager/icons/unlocked.png")));
		ImageIcon icoclear = new ImageIcon(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/org/micromanager/icons/clearlist.png")));
		Image imga = icolock.getImage();
		Image imgb = icounlock.getImage();
		Image imgc = icoclear.getImage();
		Image nicolock = imga.getScaledInstance(nsize, nsize, java.awt.Image.SCALE_SMOOTH);
		Image nicounlock = imgb.getScaledInstance(nsize, nsize, java.awt.Image.SCALE_SMOOTH);
		Image nicoclear = imgc.getScaledInstance(nsize, nsize, java.awt.Image.SCALE_SMOOTH);

		btnLockScroll = new JToggleButton();
		btnLockScroll.setToolTipText("Lock / Unlock Log Panel Scrolling");
		btnLockScroll.setIcon(new ImageIcon(nicolock));
		btnLockScroll.setSelectedIcon(new ImageIcon(nicounlock));
		btnLockScroll.setHorizontalAlignment(SwingConstants.CENTER);
		btnLockScroll.setPreferredSize(new Dimension(0, 28));
		btnLockScroll.addActionListener(ev -> {});
		logToolbar_.add(btnLockScroll);
		toollayout.putConstraint(SpringLayout.NORTH, btnLockScroll, 0, SpringLayout.NORTH, logToolbar_);
		toollayout.putConstraint(SpringLayout.WEST, btnLockScroll, 1, SpringLayout.WEST, logToolbar_);
		toollayout.putConstraint(SpringLayout.EAST, btnLockScroll, 1, SpringLayout.EAST, logToolbar_);

		JButton btnClearLog = new JButton();
		btnClearLog.setToolTipText("Clear log view");
		btnClearLog.setIcon(new ImageIcon(nicoclear));
		btnClearLog.setHorizontalAlignment(SwingConstants.CENTER);
		btnClearLog.setPreferredSize(new Dimension(0, 28));
		btnClearLog.addActionListener(ev -> {
			SwingUtilities.invokeLater(() -> {
				try
				{
					logPane_.getStyledDocument().remove(0, logPane_.getStyledDocument().getLength() - 1);
					logPane_.setCaretPosition(0);
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			});
		});
		logToolbar_.add(btnClearLog);
		toollayout.putConstraint(SpringLayout.NORTH, btnClearLog, 1, SpringLayout.SOUTH, btnLockScroll);
		toollayout.putConstraint(SpringLayout.WEST, btnClearLog, 1, SpringLayout.WEST, logToolbar_);
		toollayout.putConstraint(SpringLayout.EAST, btnClearLog, 1, SpringLayout.EAST, logToolbar_);
	}

	/**
	 * Log message
	 * @param msg Message
	 * @param err Write error message
	 */
	public void logMessage(String msg, boolean err)
	{
		Date now = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		String strdate = sdf.format(now);

		String msgtxt = "(" + strdate + "): " + msg + "\n";
		Color color = err ? Color.RED : Color.black;

		// access swing component to display the message
		SwingUtilities.invokeLater(() -> {
			try
			{
				int cpos = logPane_.getCaretPosition();
				StyleConstants.setForeground(logPaneStyle_, color);
				logPane_.getStyledDocument().insertString(logPane_.getStyledDocument().getLength() - 1, msgtxt, logPaneStyle_);
				if(btnLockScroll.isSelected())
					logPane_.setCaretPosition(cpos);
				else
					logPane_.setCaretPosition(logPane_.getStyledDocument().getLength() - 1);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		});
	}

	/**
	 * Append message line
	 * @param msg Message
	 */
	public void writeLine(String msg) {
		// access swing component to display the message
		SwingUtilities.invokeLater(() -> {
			try
			{
				int cpos = logPane_.getCaretPosition();
				//StyleConstants.setForeground(logPaneStyle_, Color.black);
				logPane_.getStyledDocument().insertString(logPane_.getStyledDocument().getLength() - 1, msg, logPaneStyle_);
				if(btnLockScroll.isSelected())
					logPane_.setCaretPosition(cpos);
				else
					logPane_.setCaretPosition(logPane_.getStyledDocument().getLength() - 1);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		});
	}

}
