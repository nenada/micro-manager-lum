package org.lumencor.targa;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import mmcorej.CMMCore;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Vector;
import java.util.prefs.Preferences;

import mmcorej.StorageDataType;
import org.micromanager.Studio;
import org.micromanager.data.*;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.FileDialogs;

/**
 * Targa acquisition plugin main window
 * @author Milos Jovanovic <milos@tehnocad.rs>
 */
public class TargaAcqWindow extends JFrame implements AcqRunnerListener {
	private final static String CFG_LOCATION = "DataDir";
	private final static String CFG_NAME = "NAME";
	private final static String CFG_TIMEPOINTS = "TimePoints";
	private final static String CFG_TIMELAPSE = "TimeLapse";
	private final static String CFG_TIMEINTERVAL = "TimeInterval";
	private final static String CFG_CHANNELS = "Channels";
	private final static String CFG_WNDX = "WndX";
	private final static String CFG_WNDY = "WndY";

	private final JTextField tbLocation_;
	private final JTextField tbName_;
	private final JSpinner tbTimePoints_;
	private final JFormattedTextField tbTimeInterval_;
	private final JCheckBox cbTimeLapse_;
	private final JButton loadButton_;
	private final JButton cancelLoadButton_;
	private final JButton chooseLocationButton_;
	private final JButton startAcqButton_;
	private final JButton stopAcqButton_;
	private final JButton channelAddButton_;
	private final JButton channelRemoveButton_;
	private final JButton channelUpButton_;
	private final JButton channelDownButton_;
	private final JList<String> listChannels_;
	private final JLabel labelDataSize_;
	private final JLabel labelDuration_;
	private final JLabel labelExposure_;
	private final JLabel labelFramerate_;
	private final JLabel labelImageSize_;
	private final JLabel labelPixelDataSize_;
	private final JLabel labelPixelSize_;
	private final JLabel statusInfo_;
	private final JLabel labelCbuffStatus_;
	private final JLabel labelCbuffMemory_;
	private final JLabel labelAcqStoreFps_;
	private final JProgressBar progressBar_;
	private final JProgressBar cbuffCapacity_;

	private final Studio mmstudio_;
	private final CMMCore core_;
	private final Vector<String> channels_;
	private String dataDir_;
	private String acqName_;
	private int timePoints_;
	private int timeIntervalMs_;
	private double totalStoreTime_;
	private boolean runnerActive_;
	private boolean loadActive_;
	private boolean coreInit_;
	private AcqRunner worker_;
	private RewritableDatastore currentAcq_;

	/**
	 * Class constructor
	 * @param studio Micro-manager app handle
	 */
	TargaAcqWindow(Studio studio) {
		super();
		totalStoreTime_ = 0;
		coreInit_ = false;
		runnerActive_ = false;
		loadActive_ = false;
		mmstudio_ = studio;
		core_ = studio.getCMMCore();
		channels_ = new Vector<>();

		// Set window properties
		super.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent e) {
				saveSettings();
			}
		});
		super.setTitle("Targa Acquisition " + TargaPlugin.VERSION_INFO);
		super.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/org/micromanager/icons/microscope.gif")));
		super.setResizable(false);
		super.setPreferredSize(new Dimension(800, 530));
		super.setBounds(400, 200, 800, 530);
		super.pack();

		// Set layout manager
		SpringLayout layout = new SpringLayout();
		Container contentPane = super.getContentPane();
		contentPane.setLayout(layout);

		// Set common event handlers
		FocusListener focusListener = new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) { }

			@Override
			public void focusLost(FocusEvent e) {
				applySettingsFromUI();
			}
		};

		NumberFormat integerFieldFormatter = NumberFormat.getIntegerInstance();
		integerFieldFormatter.setMaximumFractionDigits(0);

		// Add UI components
		// Add status bar
		SpringLayout statusbarlayout = new SpringLayout();
		JPanel statusPanel = new JPanel();
		statusPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		statusPanel.setPreferredSize(new Dimension(contentPane.getWidth(), 25));
		statusPanel.setLayout(statusbarlayout);
		contentPane.add(statusPanel);
		layout.putConstraint(SpringLayout.WEST, statusPanel, 0, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.EAST, statusPanel, 0, SpringLayout.EAST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, statusPanel, 0, SpringLayout.SOUTH, contentPane);

		statusInfo_ = new JLabel("Ready");
		statusInfo_.setHorizontalAlignment(SwingConstants.LEFT);
		statusPanel.add(statusInfo_);
		statusbarlayout.putConstraint(SpringLayout.WEST, statusInfo_, 10, SpringLayout.WEST, statusPanel);
		statusbarlayout.putConstraint(SpringLayout.SOUTH, statusInfo_, -1, SpringLayout.SOUTH, statusPanel);

		// Add acquisition location label
		final JLabel labelDir = new JLabel("Project Directory");
		contentPane.add(labelDir);
		layout.putConstraint(SpringLayout.WEST, labelDir, 20, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.NORTH, labelDir, 12, SpringLayout.NORTH, contentPane);

		// Add acquisition location field
		tbLocation_ = new JTextField();
		tbLocation_.addFocusListener(focusListener);
		contentPane.add(tbLocation_);
		layout.putConstraint(SpringLayout.WEST, tbLocation_, 170, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.EAST, tbLocation_, -50, SpringLayout.EAST, contentPane);
		layout.putConstraint(SpringLayout.NORTH, tbLocation_, 10, SpringLayout.NORTH, contentPane);

		chooseLocationButton_ = new JButton("...");
		chooseLocationButton_.setToolTipText("Browse");
		chooseLocationButton_.setMargin(new Insets(2, 5, 2, 5));
		chooseLocationButton_.setFont(new Font("Dialog", Font.PLAIN, 12));
		chooseLocationButton_.addActionListener((final ActionEvent e) -> setSaveLocation());
		contentPane.add(chooseLocationButton_);
		layout.putConstraint(SpringLayout.WEST, chooseLocationButton_, 5, SpringLayout.EAST, tbLocation_);
		layout.putConstraint(SpringLayout.NORTH, chooseLocationButton_, 2, SpringLayout.NORTH, tbLocation_);

		// Add acquisition name label
		final JLabel labelName = new JLabel("File Name");
		contentPane.add(labelName);
		layout.putConstraint(SpringLayout.WEST, labelName, 20, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.NORTH, labelName, 42, SpringLayout.NORTH, contentPane);

		// Add acquisition name field
		tbName_ = new JTextField();
		tbName_.addFocusListener(focusListener);
		contentPane.add(tbName_);
		layout.putConstraint(SpringLayout.WEST, tbName_, 0, SpringLayout.WEST, tbLocation_);
		layout.putConstraint(SpringLayout.EAST, tbName_, 0, SpringLayout.EAST, tbLocation_);
		layout.putConstraint(SpringLayout.NORTH, tbName_, 40, SpringLayout.NORTH, contentPane);

		// Add load acquisition button
		loadButton_ = new JButton("Load Acquisition");
		loadButton_.setToolTipText("Load Existing Acquisition Data");
		loadButton_.setMargin(new Insets(5, 15, 5, 15));
		loadButton_.addActionListener((final ActionEvent e) -> loadAcquisition());
		contentPane.add(loadButton_);
		layout.putConstraint(SpringLayout.EAST, loadButton_, 0, SpringLayout.EAST, tbName_);
		layout.putConstraint(SpringLayout.NORTH, loadButton_, 10, SpringLayout.SOUTH, tbName_);

		// Add cancel load button
		cancelLoadButton_ = new JButton("Cancel Loading");
		cancelLoadButton_.setToolTipText("Cancel Acquisition Loading");
		cancelLoadButton_.setMargin(new Insets(5, 15, 5, 15));
		cancelLoadButton_.addActionListener((final ActionEvent e) -> cancelLoadAcquisition());
		cancelLoadButton_.setVisible(false);
		contentPane.add(cancelLoadButton_);
		layout.putConstraint(SpringLayout.EAST, cancelLoadButton_, 0, SpringLayout.EAST, tbName_);
		layout.putConstraint(SpringLayout.NORTH, cancelLoadButton_, 10, SpringLayout.SOUTH, tbName_);

		// Add timepoints label
		final JLabel labelTimepoints = new JLabel("Timepoints");
		contentPane.add(labelTimepoints);
		layout.putConstraint(SpringLayout.WEST, labelTimepoints, 20, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.NORTH, labelTimepoints, 142, SpringLayout.NORTH, contentPane);

		// Add timepoints count text box
		tbTimePoints_ = new JSpinner(new SpinnerNumberModel(5, 1, null, 1));
		tbTimePoints_.addChangeListener((ChangeEvent e) -> applySettingsFromUI());
		contentPane.add(tbTimePoints_);
		layout.putConstraint(SpringLayout.WEST, tbTimePoints_, 0, SpringLayout.WEST, tbLocation_);
		layout.putConstraint(SpringLayout.EAST, tbTimePoints_, 80, SpringLayout.WEST, tbTimePoints_);
		layout.putConstraint(SpringLayout.NORTH, tbTimePoints_, -2, SpringLayout.NORTH, labelTimepoints);

		// Add time lapse check box
		cbTimeLapse_ = new JCheckBox("Time Lapse");
		cbTimeLapse_.setHorizontalTextPosition(SwingConstants.LEADING);
		cbTimeLapse_.addActionListener((ActionEvent e) -> updateTimeLapseOptions());
		layout.putConstraint(SpringLayout.WEST, cbTimeLapse_, 50, SpringLayout.EAST, tbTimePoints_);
		layout.putConstraint(SpringLayout.NORTH, cbTimeLapse_, -3, SpringLayout.NORTH, labelTimepoints);
		contentPane.add(cbTimeLapse_);

		// Add time interval label
		final JLabel labelTimeInterval = new JLabel("Interval [ms]");
		contentPane.add(labelTimeInterval);
		layout.putConstraint(SpringLayout.WEST, labelTimeInterval, 50, SpringLayout.EAST, cbTimeLapse_);
		layout.putConstraint(SpringLayout.NORTH, labelTimeInterval, 0, SpringLayout.NORTH, labelTimepoints);

		// Add time interval text box
		tbTimeInterval_ = new JFormattedTextField(integerFieldFormatter);
		tbTimeInterval_.addFocusListener(focusListener);
		contentPane.add(tbTimeInterval_);
		layout.putConstraint(SpringLayout.WEST, tbTimeInterval_, 10, SpringLayout.EAST, labelTimeInterval);
		layout.putConstraint(SpringLayout.EAST, tbTimeInterval_, 80, SpringLayout.WEST, tbTimeInterval_);
		layout.putConstraint(SpringLayout.NORTH, tbTimeInterval_, 0, SpringLayout.NORTH, tbTimePoints_);

		// Add channels configuration
		final JLabel labelChannels = new JLabel("Channels");
		contentPane.add(labelChannels);
		layout.putConstraint(SpringLayout.WEST, labelChannels, 0, SpringLayout.WEST, labelTimepoints);
		layout.putConstraint(SpringLayout.NORTH, labelChannels, 20, SpringLayout.SOUTH, labelTimepoints);

		// Add channels list
		listChannels_ = new JList<>();
		listChannels_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		listChannels_.setLayoutOrientation(JList.VERTICAL);
		listChannels_.setVisibleRowCount(-1);
		listChannels_.addListSelectionListener((ListSelectionEvent e) -> updateChannelCommands());
		JScrollPane listScroller = new JScrollPane(listChannels_);
		listScroller.setPreferredSize(new Dimension(180, 150));
		contentPane.add(listScroller);
		layout.putConstraint(SpringLayout.WEST, listScroller, 0, SpringLayout.WEST, tbTimePoints_);
		layout.putConstraint(SpringLayout.NORTH, listScroller, -2, SpringLayout.NORTH, labelChannels);

		// Add channels list command buttons -> ADD
		channelAddButton_ = new JButton("+");
		channelAddButton_.setToolTipText("Add Channel");
		channelAddButton_.setMargin(new Insets(2, 5, 2, 5));
		channelAddButton_.setPreferredSize(new Dimension(48, 24));
		channelAddButton_.addActionListener((final ActionEvent e) -> addChannel());
		contentPane.add(channelAddButton_);
		layout.putConstraint(SpringLayout.WEST, channelAddButton_, 5, SpringLayout.EAST, listScroller);
		layout.putConstraint(SpringLayout.NORTH, channelAddButton_, 0, SpringLayout.NORTH, listScroller);

		// Add channels list command buttons -> REMOVE
		channelRemoveButton_ = new JButton("-");
		channelRemoveButton_.setToolTipText("Remove Channel");
		channelRemoveButton_.setMargin(new Insets(2, 5, 2, 5));
		channelRemoveButton_.setPreferredSize(new Dimension(48, 24));
		channelRemoveButton_.addActionListener((final ActionEvent e) -> removeChannel());
		channelRemoveButton_.setEnabled(false);
		contentPane.add(channelRemoveButton_);
		layout.putConstraint(SpringLayout.WEST, channelRemoveButton_, 0, SpringLayout.WEST, channelAddButton_);
		layout.putConstraint(SpringLayout.NORTH, channelRemoveButton_, 5, SpringLayout.SOUTH, channelAddButton_);

		// Add channels list command buttons -> MOVE UP
		channelUpButton_ = new JButton("UP");
		channelUpButton_.setToolTipText("Move Channel UP");
		channelUpButton_.setMargin(new Insets(2, 5, 2, 5));
		channelUpButton_.setPreferredSize(new Dimension(48, 24));
		channelUpButton_.addActionListener((final ActionEvent e) -> moveChannelUp());
		channelUpButton_.setEnabled(false);
		contentPane.add(channelUpButton_);
		layout.putConstraint(SpringLayout.WEST, channelUpButton_, 0, SpringLayout.WEST, channelAddButton_);
		layout.putConstraint(SpringLayout.NORTH, channelUpButton_, 5, SpringLayout.SOUTH, channelRemoveButton_);

		// Add channels list command buttons -> MOVE DOWN
		channelDownButton_ = new JButton("DN");
		channelDownButton_.setToolTipText("Move Channel Down");
		channelDownButton_.setMargin(new Insets(2, 5, 2, 5));
		channelDownButton_.setPreferredSize(new Dimension(48, 24));
		channelDownButton_.addActionListener((final ActionEvent e) -> moveChannelDown());
		channelDownButton_.setEnabled(false);
		contentPane.add(channelDownButton_);
		layout.putConstraint(SpringLayout.WEST, channelDownButton_, 0, SpringLayout.WEST, channelAddButton_);
		layout.putConstraint(SpringLayout.NORTH, channelDownButton_, 5, SpringLayout.SOUTH, channelUpButton_);

		// Add info labels
		labelDataSize_ = new JLabel("Dataset size: -");
		contentPane.add(labelDataSize_);
		layout.putConstraint(SpringLayout.WEST, labelDataSize_, 100, SpringLayout.EAST, channelAddButton_);
		layout.putConstraint(SpringLayout.NORTH, labelDataSize_, 0, SpringLayout.NORTH, listScroller);

		labelDuration_ = new JLabel("Dataset duration: -");
		contentPane.add(labelDuration_);
		layout.putConstraint(SpringLayout.WEST, labelDuration_, 0, SpringLayout.WEST, labelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelDuration_, 10, SpringLayout.SOUTH, labelDataSize_);

		labelExposure_ = new JLabel("Camera exposure: -");
		contentPane.add(labelExposure_);
		layout.putConstraint(SpringLayout.WEST, labelExposure_, 0, SpringLayout.WEST, labelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelExposure_, 10, SpringLayout.SOUTH, labelDuration_);

		labelFramerate_ = new JLabel("Frame rate: -");
		contentPane.add(labelFramerate_);
		layout.putConstraint(SpringLayout.WEST, labelFramerate_, 0, SpringLayout.WEST, labelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelFramerate_, 10, SpringLayout.SOUTH, labelExposure_);

		labelImageSize_ = new JLabel("Image size: -");
		contentPane.add(labelImageSize_);
		layout.putConstraint(SpringLayout.WEST, labelImageSize_, 0, SpringLayout.WEST, labelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelImageSize_, 10, SpringLayout.SOUTH, labelFramerate_);

		labelPixelDataSize_ = new JLabel("Bytes per pixel: -");
		contentPane.add(labelPixelDataSize_);
		layout.putConstraint(SpringLayout.WEST, labelPixelDataSize_, 0, SpringLayout.WEST, labelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelPixelDataSize_, 10, SpringLayout.SOUTH, labelImageSize_);

		labelPixelSize_ = new JLabel("Pixel size: -");
		contentPane.add(labelPixelSize_);
		layout.putConstraint(SpringLayout.WEST, labelPixelSize_, 0, SpringLayout.WEST, labelPixelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelPixelSize_, 10, SpringLayout.SOUTH, labelPixelDataSize_);

		// Add start acquisition button
		startAcqButton_ = new JButton("Start Acquisition");
		startAcqButton_.setToolTipText("Start Data Acquisition");
		startAcqButton_.setMargin(new Insets(5, 15, 5, 15));
		startAcqButton_.addActionListener((final ActionEvent e) -> startAcquisition());
		contentPane.add(startAcqButton_);
		layout.putConstraint(SpringLayout.WEST, startAcqButton_, 300, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.EAST, startAcqButton_, -300, SpringLayout.EAST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, startAcqButton_, -5, SpringLayout.NORTH, statusPanel);

		// Add stop acquisition button
		stopAcqButton_ = new JButton("Stop Acquisition");
		stopAcqButton_.setToolTipText("Start Data Acquisition");
		stopAcqButton_.setMargin(new Insets(5, 15, 5, 15));
		stopAcqButton_.addActionListener((final ActionEvent e) -> stopAcquisition());
		stopAcqButton_.setPreferredSize(new Dimension(170, 35));
		stopAcqButton_.setVisible(false);
		contentPane.add(stopAcqButton_);
		layout.putConstraint(SpringLayout.EAST, stopAcqButton_, -20, SpringLayout.EAST, contentPane);
		layout.putConstraint(SpringLayout.SOUTH, stopAcqButton_, -5, SpringLayout.NORTH, statusPanel);

		// Add progress bar
		progressBar_ = new JProgressBar();
		progressBar_.setValue(0);
		progressBar_.setMinimum(0);
		progressBar_.setStringPainted(true);
		progressBar_.setString("0 / 0 (0%)");
		progressBar_.setVisible(false);
		contentPane.add(progressBar_);
		layout.putConstraint(SpringLayout.WEST, progressBar_, 20, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.EAST, progressBar_, -40, SpringLayout.WEST, stopAcqButton_);
		layout.putConstraint(SpringLayout.SOUTH, progressBar_, -8, SpringLayout.NORTH, statusPanel);

		// Add circular buffer status label
		labelCbuffStatus_ = new JLabel("Sequence buffer usage:");
		labelCbuffStatus_.setVisible(false);
		contentPane.add(labelCbuffStatus_);
		layout.putConstraint(SpringLayout.WEST, labelCbuffStatus_, 0, SpringLayout.WEST, progressBar_);
		layout.putConstraint(SpringLayout.SOUTH, labelCbuffStatus_, -10, SpringLayout.NORTH, progressBar_);

		// Add circular buffer status bar
		cbuffCapacity_ = new JProgressBar();
		cbuffCapacity_.setValue(0);
		cbuffCapacity_.setMinimum(0);
		cbuffCapacity_.setStringPainted(true);
		cbuffCapacity_.setString("0 / 0 (0%)");
		cbuffCapacity_.setVisible(false);
		contentPane.add(cbuffCapacity_);
		layout.putConstraint(SpringLayout.WEST, cbuffCapacity_, 10, SpringLayout.EAST, labelCbuffStatus_);
		layout.putConstraint(SpringLayout.EAST, cbuffCapacity_, 200, SpringLayout.EAST, labelCbuffStatus_);
		layout.putConstraint(SpringLayout.SOUTH, cbuffCapacity_, -8, SpringLayout.NORTH, progressBar_);

		// Add label for storage write frame-rate during acquisition
		labelAcqStoreFps_ = new JLabel("Storage write frame rate: -");
		labelAcqStoreFps_.setVisible(false);
		contentPane.add(labelAcqStoreFps_);
		layout.putConstraint(SpringLayout.WEST, labelAcqStoreFps_, 0, SpringLayout.WEST, labelCbuffStatus_);
		layout.putConstraint(SpringLayout.SOUTH, labelAcqStoreFps_, -15, SpringLayout.NORTH, labelCbuffStatus_);

		// Add circular buffer memory footprint label
		labelCbuffMemory_ = new JLabel("0 MB");
		labelCbuffMemory_.setVisible(false);
		contentPane.add(labelCbuffMemory_);
		layout.putConstraint(SpringLayout.WEST, labelCbuffMemory_, 10, SpringLayout.EAST, cbuffCapacity_);
		layout.putConstraint(SpringLayout.NORTH, labelCbuffMemory_, 0, SpringLayout.NORTH, labelCbuffStatus_);

		loadSettings();
		updateAcqInfo();
	}

	/**
	 * Release the window
	 */
	public void dispose() {
		saveSettings();
	}

	/**
	 * Save plugin settings
	 */
	protected void saveSettings() {
		Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
		if(prefs == null)
			return;
		StringBuilder chlist = new StringBuilder();
		for(String s : channels_)
			chlist.append((chlist.length() == 0) ? "" : ",").append(s);

		prefs.put(CFG_LOCATION, dataDir_);
		prefs.put(CFG_NAME, acqName_);
		prefs.putInt(CFG_TIMEPOINTS, timePoints_);
		prefs.putBoolean(CFG_TIMELAPSE, cbTimeLapse_.isSelected());
		prefs.putInt(CFG_TIMEINTERVAL, timeIntervalMs_);
		prefs.put(CFG_CHANNELS, chlist.toString());
		prefs.putInt(CFG_WNDX, super.getBounds().x);
		prefs.putInt(CFG_WNDY, super.getBounds().y);
	}

	/**
	 * Load plugin settings
	 */
	protected void loadSettings() {
		Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
		if(prefs == null)
			return;

		// Load values
		dataDir_ = prefs.get(CFG_LOCATION, "");
		acqName_ = prefs.get(CFG_NAME, "");
		timePoints_ = prefs.getInt(CFG_TIMEPOINTS, 1);
		timeIntervalMs_ = prefs.getInt(CFG_TIMEINTERVAL, 100);
		boolean tl = prefs.getBoolean(CFG_TIMELAPSE, false);
		int wndx = prefs.getInt(CFG_WNDX, super.getBounds().x);
		int wndy = prefs.getInt(CFG_WNDY, super.getBounds().y);
		String chlist = prefs.get(CFG_CHANNELS, "");
		channels_.clear();
		if(!chlist.isEmpty())
			channels_.addAll(Arrays.asList(chlist.split(",")));

		// Update UI
		tbLocation_.setText(dataDir_);
		tbName_.setText(acqName_);
		tbTimePoints_.setValue(timePoints_);
		cbTimeLapse_.setSelected(tl);
		tbTimeInterval_.setValue(timeIntervalMs_);
		tbTimeInterval_.setEnabled(tl);
		listChannels_.setListData(channels_);
		super.setLocation(wndx, wndy);
	}

	/**
	 * Load existing data acquisition
	 */
	protected void loadAcquisition() {
		File result = FileDialogs.openFile(this, "Please choose a valid dataset", FileDialogs.MM_DATA_SET);
		if(result == null)
			return;
		statusInfo_.setText(String.format("Loading file: %s...", result.getAbsolutePath()));
		SwingUtilities.invokeLater(() -> {
			try {
				String handle = core_.loadDataset(result.getAbsolutePath());
				mmcorej.LongVector shape = core_.getDatasetShape(handle);
				mmcorej.StorageDataType type = core_.getDatasetPixelType(handle);
				if(shape == null || type == null || shape.size() != 5) {
					statusInfo_.setText("Dataset load failed. Selected file is not a valid dataset");
					return;
				}
				String dsmeta = core_.getSummaryMeta(handle);
				int w = shape.get((int)shape.size() - 1);
				int h = shape.get((int)shape.size() - 2);
				int bpp = type == StorageDataType.StorageDataType_GRAY16 ? 2 : 1;
				int numImages = 1;
				for(int i = 0; i < shape.size() - 2; i++)
					numImages *= shape.get(i);

				Datastore store = mmstudio_.data().createRAMDatastore();
				DisplayWindow display = mmstudio_.displays().createDisplay(store);
				display.setCustomTitle(result.getAbsolutePath());

				if(dsmeta != null && !dsmeta.isEmpty()) {
					try {
						JsonElement je = new JsonParser().parse(dsmeta);
						SummaryMetadata smeta = DefaultSummaryMetadata.fromPropertyMap(NonPropertyMapJSONFormats.metadata().fromGson(je));
						store.setSummaryMetadata(smeta);
					} catch(Exception e) {
						mmstudio_.getLogManager().logError(e);
					}
				}
				for(int i = 0; i < shape.get(0); i++) {
					for(int j = 0; j < shape.get(1); j++) {
						for(int k = 0; k < shape.get(2); k++) {
							mmcorej.LongVector coords = new mmcorej.LongVector();
							coords.add(i);
							coords.add(j);
							coords.add(k);
							Object pixdata = core_.getImage(handle, coords);
							if(pixdata == null)
								continue;
							Metadata meta = null;
							String metastr = core_.getImageMeta(handle, coords);
							if(metastr != null && !metastr.isEmpty()) {
								try {
									JsonElement je = new JsonParser().parse(metastr);
									meta = DefaultMetadata.fromPropertyMap(NonPropertyMapJSONFormats.metadata().fromGson(je));
								} catch(Exception e) {
									mmstudio_.getLogManager().logError(e);
								}
							}
							Coords.CoordsBuilder builder = mmstudio_.data().coordsBuilder();
							builder.time(j).channel(k);
							Image img = new DefaultImage(pixdata, w, h, bpp, 1, builder.build(), meta);
							store.putImage(img);
						}
					}
				}
				statusInfo_.setText(String.format("Dataset loaded successfully: %d x %d, images %d, type %s", w, h, numImages, type));
				core_.closeDataset(handle);
			} catch(Exception ex) {
				statusInfo_.setText("Dataset load failed. " + ex.getMessage());
				mmstudio_.getLogManager().logError(ex);
			}
		});
	}

	/**
	 * Cancel acquisition loading
	 */
	protected void cancelLoadAcquisition() {

	}

	/**
	 * Start data acquisition
	 */
	protected void startAcquisition() {
		runnerActive_ = true;
		totalStoreTime_ = 0;
		worker_ = new AcqRunner(core_, dataDir_, acqName_, cbTimeLapse_.isSelected(), timePoints_, channels_, timeIntervalMs_);
		worker_.addListener(this);
		worker_.start();

		Thread timerThread_ = new Thread(this::updateAcqTime);
		timerThread_.start();

		statusInfo_.setText("Starting acquisition...");
		currentAcq_ = mmstudio_.getDataManager().createRewritableRAMDatastore();
		mmstudio_.getDisplayManager().createDisplay(currentAcq_);
		updateFormState();
		updateChannelCommands();
	}

	/**
	 * Stop / cancel data acquisition
	 */
	protected void stopAcquisition() {
		runnerActive_ = false;
		worker_.cancel();
		try {
			if(worker_.isAlive())
				worker_.join();
		}catch(InterruptedException e) {
			mmstudio_.getLogManager().logError(e);
		}
		worker_ = null;
		statusInfo_.setText("Acquisition stopped by the user");
		updateFormState();
		updateChannelCommands();
	}

	/**
	 * Add channel
	 */
	protected void addChannel() {
		// Obtain channels list
		String chgrp = core_.getChannelGroup();
		if(chgrp.isEmpty()) {
			statusInfo_.setText("Channels configuration is not defined");
			return;
		}
		String[] channelSource = core_.getAvailableConfigs(chgrp).toArray();
		//String[] channelSource = { "CYAN", "GREEN", "RED", "UV", "TEAL" };
		Vector<String> allchannels = new Vector<>();
		for(String s : channelSource) {
			if(channels_.contains(s))
				continue;
			allchannels.add(s);
		}
		if(allchannels.isEmpty()) {
			statusInfo_.setText("No channels found");
			return;
		}


		// Show channel selection dialog
		ChannelSelectionDlg wnd = new ChannelSelectionDlg(this, allchannels);
		wnd.setVisible(true);

		String schannel = wnd.getSelectedChannel();
		if(schannel == null || schannel.isEmpty()) {
			statusInfo_.setText("No channel selected");
			return;
		}
		channels_.add(schannel);
		listChannels_.setListData(channels_);
		updateAcqInfo();
	}

	/**
	 * Remove selected channel
	 */
	protected void removeChannel() {
		if(listChannels_.getSelectedIndex() < 0 || listChannels_.getSelectedIndex() >= channels_.size())
			return;

		channels_.remove(listChannels_.getSelectedIndex());
		listChannels_.setListData(channels_);
		updateAcqInfo();
	}

	/**
	 * Move selected channel up
	 */
	protected void moveChannelUp() {
		int ind = listChannels_.getSelectedIndex();
		if(ind < 1 || ind >= channels_.size())
			return;

		// Switch channels
		String tmp = channels_.get(ind);
		channels_.set(ind, channels_.get(ind - 1));
		channels_.set(ind - 1, tmp);

		// Update UI
		listChannels_.setListData(channels_);
		listChannels_.setSelectedIndex(ind - 1);
	}

	/**
	 * Move selected channel down
	 */
	protected void moveChannelDown() {
		int ind = listChannels_.getSelectedIndex();
		if(ind < 0 || ind >= channels_.size() - 1)
			return;

		// Switch channels
		String tmp = channels_.get(ind);
		channels_.set(ind, channels_.get(ind + 1));
		channels_.set(ind + 1, tmp);

		// Update UI
		listChannels_.setListData(channels_);
		listChannels_.setSelectedIndex(ind + 1);
	}

	/**
	 * Update channel list command buttons
	 */
	protected void updateChannelCommands() {
		int ind = listChannels_.getSelectedIndex();
		channelAddButton_.setEnabled(!runnerActive_);
		channelRemoveButton_.setEnabled(!runnerActive_ && listChannels_.getSelectedIndex() >= 0 && !channels_.isEmpty());
		channelUpButton_.setEnabled(!runnerActive_ && listChannels_.getSelectedIndex() > 0 && !channels_.isEmpty());
		channelDownButton_.setEnabled(!runnerActive_ && listChannels_.getSelectedIndex() >= 0 && listChannels_.getSelectedIndex() < channels_.size() - 1);
	}

	/**
	 * Update UI state based on the acquisition status
	 */
	protected void updateFormState() {
		startAcqButton_.setVisible(!runnerActive_);
		stopAcqButton_.setVisible(runnerActive_);
		progressBar_.setVisible(runnerActive_);
		tbTimePoints_.setEnabled(!runnerActive_);
		tbTimeInterval_.setEnabled(!runnerActive_ && cbTimeLapse_.isSelected());
		cbTimeLapse_.setEnabled(!runnerActive_);
		tbLocation_.setEnabled(!runnerActive_);
		tbName_.setEnabled(!runnerActive_);
		loadButton_.setEnabled(!runnerActive_);
		chooseLocationButton_.setEnabled(!runnerActive_);
		listChannels_.setEnabled(!runnerActive_);
		labelCbuffStatus_.setVisible(runnerActive_);
		cbuffCapacity_.setVisible(runnerActive_);
		labelCbuffMemory_.setVisible(runnerActive_);
		labelAcqStoreFps_.setVisible(runnerActive_);
	}

	/**
	 * Update acquisition settings from UI elements
	 */
	protected void applySettingsFromUI() {
		acqName_ = tbName_.getText().replaceAll("[/\\\\*!':]", "-").trim();
		dataDir_ = tbLocation_.getText().trim();
		try {
			timePoints_ = (int)tbTimePoints_.getValue();
		} catch(NumberFormatException e) {
			tbTimePoints_.setValue(timePoints_);
		}
		try {
			timeIntervalMs_ = Integer.parseInt(tbTimeInterval_.getText());
		} catch(NumberFormatException e) {
			tbTimeInterval_.setValue(timeIntervalMs_);
		}
		updateAcqInfo();
	}

	/**
	 * Update acquisition info labels
	 */
	protected void updateAcqInfo() {
		try {
			if(core_ == null)
				return;
			if(!coreInit_) {
				core_.snapImage();
				coreInit_ = true;
			}
			int bpp = (int)core_.getBytesPerPixel();
			int imgw = (int)core_.getImageWidth();
			int imgh = (int)core_.getImageHeight();
			double exp = core_.getExposure();
			double fps = exp == 0 ? 0 : 1000.0 / exp;
			double durms = (exp + (cbTimeLapse_.isSelected() ? timeIntervalMs_ : 0)) * timePoints_;
			double dsizemb = (double)imgw * (double)imgh * bpp * timePoints_ * (channels_.isEmpty() ? 1 : channels_.size()) / (1024.0 * 1024.0);
			double psize = core_.getPixelSizeUm();

			if(dsizemb < 1024.0)
				labelDataSize_.setText(String.format("Dataset size: %.1f MB", dsizemb));
			else
				labelDataSize_.setText(String.format("Dataset size: %.2f GB", dsizemb / 1024.0));
			if(durms < 100.0)
				labelDuration_.setText(String.format("Dataset duration: %.1f ms", durms));
			else
				labelDuration_.setText(String.format("Dataset duration: %.2f s", durms / 1000.0));
			labelExposure_.setText(String.format("Camera exposure: %.1f ms", exp));
			labelFramerate_.setText(String.format("Frame rate: %.1f FPS", fps));
			labelImageSize_.setText(String.format("Image size: %d x %d", imgw, imgh));
			labelPixelDataSize_.setText(String.format("Bytes per pixel: %d bytes", bpp));
			labelPixelSize_.setText(String.format("Pixel size: %.1f um", psize));
		} catch(Exception e) {
			mmstudio_.getLogManager().logError(e);
		}
	}

	/**
	 * Update acquisition info labels after exposure update
	 * @param nexp New exposure value (ms)
	 */
	protected void updateExposure(double nexp) {
		double fps = nexp == 0 ? 0 : 1000.0 / nexp;
		double durms = (nexp + (cbTimeLapse_.isSelected() ? timeIntervalMs_ : 0)) * timePoints_;
		if(durms < 100.0)
			labelDuration_.setText(String.format("Dataset duration: %.1f ms", durms));
		else
			labelDuration_.setText(String.format("Dataset duration: %.2f s", durms / 1000.0));
		labelExposure_.setText(String.format("Camera exposure: %.1f ms", nexp));
		labelFramerate_.setText(String.format("Frame rate: %.1f FPS", fps));
	}

	/**
	 * Update UI for time lapse parameters
	 */
	protected void updateTimeLapseOptions() {
		tbTimeInterval_.setEnabled(!runnerActive_ && cbTimeLapse_.isSelected());
		updateAcqInfo();
	}

	/**
	 * Select and update data acquisition location
	 */
	protected void setSaveLocation() {
		File result = FileDialogs.openDir(this, "Please choose a directory root for image data", FileDialogs.MM_DATA_SET);
		if(result != null) {
			dataDir_ = result.getAbsolutePath().trim();
			tbLocation_.setText(dataDir_);
		}
	}

	/**
	 * Update acquisition elapsed time
	 */
	protected void updateAcqTime() {
		long startTime = System.nanoTime();
		while(runnerActive_) {
			try {
				long currtime = System.nanoTime();
				double tottimesec = (currtime - startTime) / 1000000000.0;
				int thours = (int)Math.floor(tottimesec / 3600.0);
				int tmins = (int)Math.floor((tottimesec - thours * 3600.0) / 60.0);
				int tsec = (int)Math.round(tottimesec - thours * 3600.0 - tmins * 60.0);
				statusInfo_.setText(String.format("Running, elapsed time: %02d:%02d:%02d", tmins, tmins, tsec));
				Thread.sleep(500);
			} catch(InterruptedException e) {
				mmstudio_.getLogManager().logError(e);
			}
		}
	}

	/**
	 * Acquisition completed event handler
	 */
	@Override
	public void notifyWorkCompleted() {
		runnerActive_ = false;
		statusInfo_.setText("Acquisition completed successfully");
		updateFormState();
		updateChannelCommands();
	}

	/**
	 * Acquisition started event handler
	 */
	@Override
	public void notifyWorkStarted() {
	}

	/**
	 * Acquisition failed event handler
	 * @param msg Error message
	 */
	@Override
	public void notifyWorkFailed(String msg) {
		runnerActive_ = false;
		statusInfo_.setText("ERROR: " + msg);
		updateFormState();
		updateChannelCommands();
	}

	/**
	 * Acquisition status update event handler
	 * @param curr Current image index
	 * @param total Total number of images
	 * @param image Current image handle
	 * @param bufffree Circular buffer free capacity
	 * @param bufftotal Circular buffer total capacity
	 * @param storetimems Storage write time (in ms)
	 */
	@Override
	public void notifyStatusUpdate(int curr, int total, Image image, int bufffree, int bufftotal, double storetimems) {
		int perc = (int)Math.ceil(100.0 * (double)curr / total);
		progressBar_.setMaximum(total);
		progressBar_.setValue(curr);
		progressBar_.setString(String.format("%d / %d (%d%%)", curr, total, perc));

		int buffperc = (int)Math.ceil(100.0 * (double)(bufftotal - bufffree) / bufftotal);
		cbuffCapacity_.setMaximum(bufftotal);
		cbuffCapacity_.setValue(bufftotal - bufffree);
		cbuffCapacity_.setString(String.format("%d / %d (%d%%)", bufftotal - bufffree, bufftotal, buffperc));

		double cbuffmb = core_.getCircularBufferMemoryFootprint();
		if(cbuffmb > 1024.0)
			labelCbuffMemory_.setText(String.format("%.2f GB", cbuffmb / 1024.0));
		else
			labelCbuffMemory_.setText(String.format("%.1f MB", cbuffmb));
		//statusInfo_.setText(String.format("Running: %d / %d", curr, total));

		// Calculate storage driver statistics
		totalStoreTime_ += storetimems;
		double avgtime = curr == 0 ? 0 : totalStoreTime_ / curr;
		double avgfps = avgtime == 0 ? 0 : 1000.0 / avgtime;
		labelAcqStoreFps_.setText(String.format("Storage write frame rate: %.1f FPS", avgfps));

		// Update current image
		try {
			currentAcq_.putImage(image);
		} catch(IOException e) {
			mmstudio_.getLogManager().logError(e);
		}
	}

	/**
	 * Application entry point
	 * Should be used only for testing
	 * @param args Program arguments
	 */
	public static void main(String[] args) {
		// Parse program arguments
		String storageEngine = args.length > 0 ? args[0] : "bigtiff";
		if(!storageEngine.equals("zarr") && !storageEngine.equals("bigtiff")) {
			System.out.println("Invalid storage engine selected: " + storageEngine);
			return;
		}
		boolean directio = args.length > 1 && Integer.parseInt(args[1]) == 1;
		int flushCycle = args.length > 2 ? Integer.parseInt(args[2]) : 0;

		MMStudio studio = new MMStudio(false, null);
		try {
			// Create micro-manager instance
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

			// Setup storage engine
			if(storageEngine.equals("zarr"))
				studio.getCMMCore().loadDevice("Store", "go2scope", "AcquireZarrStorage");
			else
				studio.getCMMCore().loadDevice("Store", "go2scope", "G2SBigTiffStorage");

			// Setup demo camera
			studio.getCMMCore().loadDevice("Camera", "DemoCamera", "DCam");

			// Initialize the system, this will in turn initialize each device
			studio.getCMMCore().initializeAllDevices();

			// Configure the camera device
			studio.getCMMCore().setProperty("Camera", "PixelType", "16bit");
			studio.getCMMCore().setProperty("Camera", "OnCameraCCDXSize", "4432");
			studio.getCMMCore().setProperty("Camera", "OnCameraCCDYSize", "2368");
			studio.getCMMCore().setExposure(5.0);

			// Configure the storage device
			if(storageEngine.equals("bigtiff")) {
				studio.getCMMCore().setProperty("Store", "DirectIO", directio ? 1 : 0);
				studio.getCMMCore().setProperty("Store", "FlushCycle", flushCycle);
			}

			// Create main window
			TargaAcqWindow window = new TargaAcqWindow(studio);
			window.setVisible(true);
		} catch(Exception e) {
			studio.getLogManager().logError(e);
		}
	}
}
