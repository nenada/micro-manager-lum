package org.lumencor.targa;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Vector;

public class ChannelSelectionDlg extends JDialog {
	private String selectedChannel;
	private double exposure;
	private int intensity;
	private final JComboBox<String> channelSelector_;
	private final JSpinner tbExposure_;
	private final JSpinner tbIntensity_;

	/**
	 * Class constructor
	 * @param parent Parent window
	 * @param achannels Available channels
	 */
	ChannelSelectionDlg(JFrame parent, Vector<String> achannels) {
		super(parent, "Channel Selection", true);
		intensity = 1000;
		exposure = 10.0;

		setPreferredSize(new Dimension(500, 270));
		setLocationRelativeTo(parent);
		setLocation(parent.getLocation().x + 150, parent.getLocation().y + 120);
		pack();

		// Set layout manager
		SpringLayout layout = new SpringLayout();
		Container contentPane = super.getContentPane();
		contentPane.setLayout(layout);

		// Add UI components
		// Add Selection label
		JLabel labelChannels = new JLabel("Channel:");
		contentPane.add(labelChannels);
		layout.putConstraint(SpringLayout.WEST, labelChannels, 15, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.NORTH, labelChannels, 10, SpringLayout.NORTH, contentPane);

		// Add channel dropdown
		channelSelector_ = new JComboBox<>(achannels);
		contentPane.add(channelSelector_);
		layout.putConstraint(SpringLayout.WEST, channelSelector_, 10, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.EAST, channelSelector_, -10, SpringLayout.EAST, contentPane);
		layout.putConstraint(SpringLayout.NORTH, channelSelector_, 5, SpringLayout.SOUTH, labelChannels);

		// Add exposure label
		JLabel labelExp = new JLabel("Exposure [ms]:");
		contentPane.add(labelExp);
		layout.putConstraint(SpringLayout.WEST, labelExp, 0, SpringLayout.WEST, labelChannels);
		layout.putConstraint(SpringLayout.NORTH, labelExp, 10, SpringLayout.SOUTH, channelSelector_);

		// Add exposure text box
		tbExposure_ = new JSpinner(new SpinnerNumberModel(10, 1, 10000.0, 0.1));
		tbExposure_.addChangeListener((ChangeEvent e) -> applySettingsFromUI());
		tbExposure_.setEditor(new JSpinner.NumberEditor(tbExposure_));
		contentPane.add(tbExposure_);
		layout.putConstraint(SpringLayout.WEST, tbExposure_, 150, SpringLayout.WEST, labelExp);
		layout.putConstraint(SpringLayout.EAST, tbExposure_, 80, SpringLayout.WEST, tbExposure_);
		layout.putConstraint(SpringLayout.NORTH, tbExposure_, -2, SpringLayout.NORTH, labelExp);

		// Add intensity label
		JLabel labelIntensity = new JLabel("Intensity:");
		contentPane.add(labelIntensity);
		layout.putConstraint(SpringLayout.WEST, labelIntensity, 0, SpringLayout.WEST, labelChannels);
		layout.putConstraint(SpringLayout.NORTH, labelIntensity, 10, SpringLayout.SOUTH, labelExp);

		// Add intensity text box
		tbIntensity_ = new JSpinner(new SpinnerNumberModel(1000, 0, 1000, 1));
		tbIntensity_.addChangeListener((ChangeEvent e) -> applySettingsFromUI());
		contentPane.add(tbIntensity_);
		layout.putConstraint(SpringLayout.WEST, tbIntensity_, 150, SpringLayout.WEST, labelIntensity);
		layout.putConstraint(SpringLayout.EAST, tbIntensity_, 80, SpringLayout.WEST, tbIntensity_);
		layout.putConstraint(SpringLayout.NORTH, tbIntensity_, -2, SpringLayout.NORTH, labelIntensity);

		// Add OK button
		JButton okButton = new JButton("OK");
		okButton.setMargin(new Insets(5, 15, 5, 15));
		okButton.addActionListener((final ActionEvent e) -> { applySettingsFromUI(); selectedChannel = (String)channelSelector_.getSelectedItem(); this.setVisible(false); });
		okButton.setPreferredSize(new Dimension(130, 30));
		contentPane.add(okButton);
		layout.putConstraint(SpringLayout.WEST, okButton, 100, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, okButton, -20, SpringLayout.SOUTH, contentPane);

		// Add cancel button
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setMargin(new Insets(5, 15, 5, 15));
		cancelButton.addActionListener((final ActionEvent e) -> { selectedChannel = ""; this.setVisible(false); });
		cancelButton.setPreferredSize(new Dimension(130, 30));
		contentPane.add(cancelButton);
		layout.putConstraint(SpringLayout.EAST, cancelButton, -100, SpringLayout.EAST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, cancelButton, -20, SpringLayout.SOUTH, contentPane);
	}

	/**
	 * Get selected channel
	 * @return Selected channel
	 */
	public String getSelectedChannel() { return selectedChannel; }

	/**
	 * Get exposure
	 * @return TTL Exposure (ms)
	 */
	public double getExposure() { return exposure; }

	/**
	 * Get channel intensity
	 * @return Channel intensity
	 */
	public int getIntensity() { return intensity; }

	/**
	 * Update channel settings from UI elements
	 */
	private void applySettingsFromUI() {
		try {
			exposure = (double)tbExposure_.getValue();
		} catch(NumberFormatException e) {
			tbExposure_.setValue(exposure);
		}
		try {
			intensity = (int)tbIntensity_.getValue();
		} catch(NumberFormatException e) {
			tbIntensity_.setValue(intensity);
		}
	}
}
