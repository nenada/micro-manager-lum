package org.lumencor.targa;

import mmcorej.CMMCore;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Vector;
import java.util.prefs.Preferences;

import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.data.*;
import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.FileDialogs;

/**
 * Targa acquisition plugin main window
 * @author Milos Jovanovic <milos@tehnocad.rs>
 */
public class TargaAcqWindow extends JFrame implements AcqRunnerListener, LoadRunnerListener {
	private final static String CFG_LOCATION = "DataDir";
	private final static String CFG_NAME = "NAME";
	private final static String CFG_TIMEPOINTS = "TimePoints";
	private final static String CFG_TIMELAPSE = "TimeLapse";
	private final static String CFG_TIMEINTERVAL = "TimeInterval";
	private final static String CFG_CHUNKSIZE = "ChunkSize";
	private final static String CFG_FLUSHCYCLE = "FlushCycle";
	private final static String CFG_DIRECTIO = "DirectIO";
	private final static String CFG_FASTEXP = "FastExp";
	private final static String CFG_CHANNELS = "Channels";
	private final static String CFG_WNDX = "WndX";
	private final static String CFG_WNDY = "WndY";
	private final static String CFG_CHNAME = "name";
	private final static String CFG_CHEXP = "exposure";
	private final static String CFG_CHINT = "intensity";
	private final static String CFG_WNDW = "WndWidth";
	private final static String CFG_WNDH = "WndHeight";
	private final static String CFG_LOGVIEWDIVIDER = "LogDivider";
	private static final String CFG_AUTOFOCUS = "AutoFocus";

	private final JTextField tbLocation_;
	private final JTextField tbName_;
	private final JSpinner tbTimePoints_;
	private final JSpinner tbChunkSize_;
	private final JSpinner tbFlushCycle_;
	private final JSpinner tbTimeInterval_;
	private final JCheckBox cbTimeLapse_;
	private final JCheckBox cbDirectIo_;
	private final JCheckBox cbFastExp_;
	private final JButton loadButton_;
	private final JButton cancelLoadButton_;
	private final JButton chooseLocationButton_;
	private final JButton startAcqButton_;
	private final JButton startScanButton_;
	private final JButton stopAcqButton_;
	private final JButton channelAddButton_;
	private final JButton channelModifyButton_;
	private final JButton channelRemoveButton_;
	private final JButton channelUpButton_;
	private final JButton channelDownButton_;
	private final JTable listChannels_;
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
	private final JLabel labelAcqWriteFps_;
	private final JProgressBar progressBar_;
	private final JProgressBar cbuffCapacity_;
	private final JSplitPane logSplitPane_;
	private final JPanel mainPanel_;
	private final LogView logView_;

	private final Studio mmstudio_;
	private final CMMCore core_;
	private final Vector<ChannelInfo> channels_;
	private final JCheckBox cbAutoFocus_;
	private String dataDir_;
	private String acqName_;
	private int timePoints_;
	private int timeIntervalMs_;
	private int flushCycle_;
	private int chunkSize_;
	private long acqStartTime_;
	private long acqFirstImage_;
	private double totalStoreTime_;
	private boolean runnerActive_;
	private boolean loadActive_;
	private boolean coreInit_;
	private AcqRunner acqWorker_;
	private LoadRunner loadWorker_;
	private RewritableDatastore currentAcq_;
	private Datastore loadedDatastore_;
	private ChannelDataModel channelsDataModel_;
	private ScanRunner scanWorker_;

	/**
	 * Class constructor
	 * @param studio Micro-manager app handle
	 */
	TargaAcqWindow(Studio studio) {
		super();
		totalStoreTime_ = 0;
		flushCycle_ = 0;
		chunkSize_ = 0;
		acqStartTime_ = 0;
		acqFirstImage_ = 0;
		coreInit_ = false;
		runnerActive_ = false;
		loadActive_ = false;
		mmstudio_ = studio;
		core_ = studio != null ? studio.getCMMCore() : null;
		channels_ = new Vector<>();
		channelsDataModel_ = new ChannelDataModel();

		// Set window properties
		super.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent e) {
				saveSettings();
			}
		});
		super.setTitle("Targa Acquisition " + TargaPlugin.VERSION_INFO);
		super.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/org/micromanager/icons/microscope.gif")));
		super.setResizable(true);
		super.setMinimumSize(new Dimension(800, 850));
		super.setPreferredSize(new Dimension(800, 1050));
		super.setBounds(400, 200, 800, 1050);
		super.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		super.pack();

		// Add window event handlers
		JFrame pframe = this;
		super.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				if(loadActive_) {
					// Just cancel load operation
					cancelLoadAcquisition();
					pframe.dispose();
				} else if(runnerActive_) {
					int res = JOptionPane.showConfirmDialog(pframe, "Are you sure that you wan't to close the window? Acquisition will be stopped", "Acquisition active", JOptionPane.YES_NO_OPTION);
					if(res == JOptionPane.YES_OPTION) {
						stopAcquisition();
						pframe.dispose();
					}
				} else
					pframe.dispose();
			}
		});

		// Set common event handlers
		FocusListener focusListener = new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) { }

			@Override
			public void focusLost(FocusEvent e) {
				applySettingsFromUI();
			}
		};

		// Set root container layout
		BorderLayout borderLayout = new BorderLayout();
		getContentPane().setLayout(borderLayout);

		// Main panel
		SpringLayout layout = new SpringLayout();
		mainPanel_ = new JPanel();
		mainPanel_.setPreferredSize(new Dimension(780, 600));
		mainPanel_.setLayout(layout);

		// Log view
		logView_ = new LogView();

		// Log view split panel
		logSplitPane_ = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainPanel_, logView_);
		logSplitPane_.setContinuousLayout(true);
		logSplitPane_.setResizeWeight(0.0);
		logSplitPane_.setOneTouchExpandable(true);
		logSplitPane_.setDividerLocation(0.8d);
		logSplitPane_.setContinuousLayout(true);
		getContentPane().add(logSplitPane_, BorderLayout.CENTER);

		// Add main panel UI components
		// Add acquisition location label
		final JLabel labelDir = new JLabel("Project Directory");
		mainPanel_.add(labelDir);
		layout.putConstraint(SpringLayout.WEST, labelDir, 20, SpringLayout.WEST, mainPanel_);
		layout.putConstraint(SpringLayout.NORTH, labelDir, 12, SpringLayout.NORTH, mainPanel_);

		// Add acquisition location field
		tbLocation_ = new JTextField();
		tbLocation_.addFocusListener(focusListener);
		mainPanel_.add(tbLocation_);
		layout.putConstraint(SpringLayout.WEST, tbLocation_, 170, SpringLayout.WEST, mainPanel_);
		layout.putConstraint(SpringLayout.EAST, tbLocation_, -50, SpringLayout.EAST, mainPanel_);
		layout.putConstraint(SpringLayout.NORTH, tbLocation_, 10, SpringLayout.NORTH, mainPanel_);

		chooseLocationButton_ = new JButton("...");
		chooseLocationButton_.setToolTipText("Browse");
		chooseLocationButton_.setMargin(new Insets(2, 5, 2, 5));
		chooseLocationButton_.setFont(new Font("Dialog", Font.PLAIN, 12));
		chooseLocationButton_.addActionListener((final ActionEvent e) -> setSaveLocation());
		mainPanel_.add(chooseLocationButton_);
		layout.putConstraint(SpringLayout.WEST, chooseLocationButton_, 5, SpringLayout.EAST, tbLocation_);
		layout.putConstraint(SpringLayout.NORTH, chooseLocationButton_, 2, SpringLayout.NORTH, tbLocation_);

		// Add acquisition name label
		final JLabel labelName = new JLabel("File Name");
		mainPanel_.add(labelName);
		layout.putConstraint(SpringLayout.WEST, labelName, 20, SpringLayout.WEST, mainPanel_);
		layout.putConstraint(SpringLayout.NORTH, labelName, 42, SpringLayout.NORTH, mainPanel_);

		// Add acquisition name field
		tbName_ = new JTextField();
		tbName_.addFocusListener(focusListener);
		mainPanel_.add(tbName_);
		layout.putConstraint(SpringLayout.WEST, tbName_, 0, SpringLayout.WEST, tbLocation_);
		layout.putConstraint(SpringLayout.EAST, tbName_, 0, SpringLayout.EAST, tbLocation_);
		layout.putConstraint(SpringLayout.NORTH, tbName_, 40, SpringLayout.NORTH, mainPanel_);

		// Add load acquisition button
		loadButton_ = new JButton("Load Acquisition");
		loadButton_.setToolTipText("Load Existing Acquisition Data");
		loadButton_.setMargin(new Insets(5, 15, 5, 15));
		loadButton_.addActionListener((final ActionEvent e) -> loadAcquisition());
		mainPanel_.add(loadButton_);
		layout.putConstraint(SpringLayout.EAST, loadButton_, 0, SpringLayout.EAST, tbName_);
		layout.putConstraint(SpringLayout.NORTH, loadButton_, 10, SpringLayout.SOUTH, tbName_);

		// Add cancel load button
		cancelLoadButton_ = new JButton("Cancel Loading");
		cancelLoadButton_.setToolTipText("Cancel Acquisition Loading");
		cancelLoadButton_.setMargin(new Insets(5, 15, 5, 15));
		cancelLoadButton_.addActionListener((final ActionEvent e) -> cancelLoadAcquisition());
		cancelLoadButton_.setVisible(false);
		mainPanel_.add(cancelLoadButton_);
		layout.putConstraint(SpringLayout.EAST, cancelLoadButton_, 0, SpringLayout.EAST, tbName_);
		layout.putConstraint(SpringLayout.NORTH, cancelLoadButton_, 10, SpringLayout.SOUTH, tbName_);

		// === FIRST ROW ========================================
		// Add chunk size label
		final JLabel labelChunkSize = new JLabel("Chunk size");
		mainPanel_.add(labelChunkSize);
		layout.putConstraint(SpringLayout.WEST, labelChunkSize, 20, SpringLayout.WEST, mainPanel_);
		layout.putConstraint(SpringLayout.NORTH, labelChunkSize, 142, SpringLayout.NORTH, mainPanel_);

		// Add chunk size text box
		tbChunkSize_ = new JSpinner(new SpinnerNumberModel(0, 0, null, 1));
		tbChunkSize_.addChangeListener((ChangeEvent e) -> applySettingsFromUI());
		mainPanel_.add(tbChunkSize_);
		layout.putConstraint(SpringLayout.WEST, tbChunkSize_, 0, SpringLayout.WEST, tbLocation_);
		layout.putConstraint(SpringLayout.EAST, tbChunkSize_, 80, SpringLayout.WEST, tbChunkSize_);
		layout.putConstraint(SpringLayout.NORTH, tbChunkSize_, -2, SpringLayout.NORTH, labelChunkSize);

		// Add Direct I/O check box
		cbDirectIo_ = new JCheckBox("Direct I/O");
		cbDirectIo_.setHorizontalTextPosition(SwingConstants.LEADING);
		cbDirectIo_.setHorizontalAlignment(SwingConstants.RIGHT);
		mainPanel_.add(cbDirectIo_);
		layout.putConstraint(SpringLayout.WEST, cbDirectIo_, 20, SpringLayout.EAST, tbChunkSize_);
		layout.putConstraint(SpringLayout.EAST, cbDirectIo_, 150, SpringLayout.WEST, cbDirectIo_);
		layout.putConstraint(SpringLayout.NORTH, cbDirectIo_, -3, SpringLayout.NORTH, labelChunkSize);

		// Add flush cycle label
		final JLabel labelFlushCycle = new JLabel("Flush Cycle");
		mainPanel_.add(labelFlushCycle);
		layout.putConstraint(SpringLayout.WEST, labelFlushCycle, 100, SpringLayout.EAST, cbDirectIo_);
		layout.putConstraint(SpringLayout.NORTH, labelFlushCycle, 0, SpringLayout.NORTH, labelChunkSize);

		// Add flush cycle text box
		tbFlushCycle_ = new JSpinner(new SpinnerNumberModel(0, 0, null, 1));
		tbFlushCycle_.addChangeListener((ChangeEvent e) -> applySettingsFromUI());
		mainPanel_.add(tbFlushCycle_);
		layout.putConstraint(SpringLayout.WEST, tbFlushCycle_, 50, SpringLayout.EAST, labelFlushCycle);
		layout.putConstraint(SpringLayout.EAST, tbFlushCycle_, 80, SpringLayout.WEST, tbFlushCycle_);
		layout.putConstraint(SpringLayout.NORTH, tbFlushCycle_, -2, SpringLayout.NORTH, labelChunkSize);

		// === SECOND ROW ========================================
		// Add timepoints label
		final JLabel labelTimepoints = new JLabel("Timepoints");
		mainPanel_.add(labelTimepoints);
		layout.putConstraint(SpringLayout.WEST, labelTimepoints, 20, SpringLayout.WEST, mainPanel_);
		layout.putConstraint(SpringLayout.NORTH, labelTimepoints, 20, SpringLayout.SOUTH, labelChunkSize);

		// Add timepoints count text box
		tbTimePoints_ = new JSpinner(new SpinnerNumberModel(5, 1, null, 1));
		tbTimePoints_.addChangeListener((ChangeEvent e) -> applySettingsFromUI());
		mainPanel_.add(tbTimePoints_);
		layout.putConstraint(SpringLayout.WEST, tbTimePoints_, 0, SpringLayout.WEST, tbLocation_);
		layout.putConstraint(SpringLayout.EAST, tbTimePoints_, 80, SpringLayout.WEST, tbTimePoints_);
		layout.putConstraint(SpringLayout.NORTH, tbTimePoints_, -2, SpringLayout.NORTH, labelTimepoints);

		// Add fast exp check box
		cbFastExp_ = new JCheckBox("Fast Exp");
		cbFastExp_.setHorizontalTextPosition(SwingConstants.LEADING);
		cbFastExp_.setHorizontalAlignment(SwingConstants.RIGHT);
		cbFastExp_.addActionListener((ActionEvent e) -> updateTimeLapseOptions());
		layout.putConstraint(SpringLayout.WEST, cbFastExp_, 0, SpringLayout.WEST, cbDirectIo_);
		layout.putConstraint(SpringLayout.EAST, cbFastExp_, 0, SpringLayout.EAST, cbDirectIo_);
		layout.putConstraint(SpringLayout.NORTH, cbFastExp_, -3, SpringLayout.NORTH, labelTimepoints);
		mainPanel_.add(cbFastExp_);

		// === THIRD ROW ========================================
		// Add time interval label
		final JLabel labelTimeInterval = new JLabel("Interval [ms]");
		mainPanel_.add(labelTimeInterval);
		layout.putConstraint(SpringLayout.WEST, labelTimeInterval, 20, SpringLayout.WEST, mainPanel_);
		layout.putConstraint(SpringLayout.NORTH, labelTimeInterval, 20, SpringLayout.SOUTH, labelTimepoints);

		// Add time interval text box
		tbTimeInterval_ = new JSpinner(new SpinnerNumberModel(10, 0, null, 1));
		tbTimeInterval_.setEditor(new JSpinner.NumberEditor(tbTimeInterval_));
		tbTimeInterval_.addChangeListener((ChangeEvent e) -> applySettingsFromUI());
		mainPanel_.add(tbTimeInterval_);
		layout.putConstraint(SpringLayout.WEST, tbTimeInterval_, 0, SpringLayout.WEST, tbLocation_);
		layout.putConstraint(SpringLayout.EAST, tbTimeInterval_, 80, SpringLayout.WEST, tbTimeInterval_);
		layout.putConstraint(SpringLayout.NORTH, tbTimeInterval_, -2, SpringLayout.NORTH, labelTimeInterval);

		// Add time lapse check box
		cbTimeLapse_ = new JCheckBox("Time Lapse");
		cbTimeLapse_.setHorizontalTextPosition(SwingConstants.LEADING);
		cbTimeLapse_.setHorizontalAlignment(SwingConstants.RIGHT);
		cbTimeLapse_.addActionListener((ActionEvent e) -> updateTimeLapseOptions());
		layout.putConstraint(SpringLayout.WEST, cbTimeLapse_, 0, SpringLayout.WEST, cbDirectIo_);
		layout.putConstraint(SpringLayout.EAST, cbTimeLapse_, 0, SpringLayout.EAST, cbDirectIo_);
		layout.putConstraint(SpringLayout.NORTH, cbTimeLapse_, -3, SpringLayout.NORTH, labelTimeInterval);
		mainPanel_.add(cbTimeLapse_);

		// === CHANNELS SECTION ========================================
		// Add channels configuration
		final JLabel labelChannels = new JLabel("Channels");
		mainPanel_.add(labelChannels);
		layout.putConstraint(SpringLayout.WEST, labelChannels, 0, SpringLayout.WEST, labelTimeInterval);
		layout.putConstraint(SpringLayout.NORTH, labelChannels, 20, SpringLayout.SOUTH, labelTimeInterval);

		// Add channels list
		listChannels_ = new JTable(channelsDataModel_);
		listChannels_.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		listChannels_.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> updateChannelCommands());
		JScrollPane listScroller = new JScrollPane(listChannels_);
		listScroller.setPreferredSize(new Dimension(250, 150));
		mainPanel_.add(listScroller);
		layout.putConstraint(SpringLayout.WEST, listScroller, 0, SpringLayout.WEST, tbTimePoints_);
		layout.putConstraint(SpringLayout.NORTH, listScroller, -2, SpringLayout.NORTH, labelChannels);

		// Add channels list command buttons -> ADD
		channelAddButton_ = new JButton("+");
		channelAddButton_.setToolTipText("Add Channel");
		channelAddButton_.setMargin(new Insets(2, 5, 2, 5));
		channelAddButton_.setPreferredSize(new Dimension(48, 24));
		channelAddButton_.addActionListener((final ActionEvent e) -> addChannel());
		mainPanel_.add(channelAddButton_);
		layout.putConstraint(SpringLayout.WEST, channelAddButton_, 5, SpringLayout.EAST, listScroller);
		layout.putConstraint(SpringLayout.NORTH, channelAddButton_, 0, SpringLayout.NORTH, listScroller);

		channelModifyButton_ = new JButton("E");
		channelModifyButton_.setToolTipText("Modify Channel");
		channelModifyButton_.setMargin(new Insets(2, 5, 2, 5));
		channelModifyButton_.setPreferredSize(new Dimension(48, 24));
		channelModifyButton_.addActionListener((final ActionEvent e) -> modifyChannel());
		channelModifyButton_.setEnabled(false);
		mainPanel_.add(channelModifyButton_);
		layout.putConstraint(SpringLayout.WEST, channelModifyButton_, 0, SpringLayout.WEST, channelAddButton_);
		layout.putConstraint(SpringLayout.NORTH, channelModifyButton_, 5, SpringLayout.SOUTH, channelAddButton_);

		// Add channels list command buttons -> REMOVE
		channelRemoveButton_ = new JButton("-");
		channelRemoveButton_.setToolTipText("Remove Channel");
		channelRemoveButton_.setMargin(new Insets(2, 5, 2, 5));
		channelRemoveButton_.setPreferredSize(new Dimension(48, 24));
		channelRemoveButton_.addActionListener((final ActionEvent e) -> removeChannel());
		channelRemoveButton_.setEnabled(false);
		mainPanel_.add(channelRemoveButton_);
		layout.putConstraint(SpringLayout.WEST, channelRemoveButton_, 0, SpringLayout.WEST, channelAddButton_);
		layout.putConstraint(SpringLayout.NORTH, channelRemoveButton_, 5, SpringLayout.SOUTH, channelModifyButton_);

		// Add channels list command buttons -> MOVE UP
		channelUpButton_ = new JButton("UP");
		channelUpButton_.setToolTipText("Move Channel UP");
		channelUpButton_.setMargin(new Insets(2, 5, 2, 5));
		channelUpButton_.setPreferredSize(new Dimension(48, 24));
		channelUpButton_.addActionListener((final ActionEvent e) -> moveChannelUp());
		channelUpButton_.setEnabled(false);
		mainPanel_.add(channelUpButton_);
		layout.putConstraint(SpringLayout.WEST, channelUpButton_, 0, SpringLayout.WEST, channelAddButton_);
		layout.putConstraint(SpringLayout.NORTH, channelUpButton_, 5, SpringLayout.SOUTH, channelRemoveButton_);

		// Add channels list command buttons -> MOVE DOWN
		channelDownButton_ = new JButton("DN");
		channelDownButton_.setToolTipText("Move Channel Down");
		channelDownButton_.setMargin(new Insets(2, 5, 2, 5));
		channelDownButton_.setPreferredSize(new Dimension(48, 24));
		channelDownButton_.addActionListener((final ActionEvent e) -> moveChannelDown());
		channelDownButton_.setEnabled(false);
		mainPanel_.add(channelDownButton_);
		layout.putConstraint(SpringLayout.WEST, channelDownButton_, 0, SpringLayout.WEST, channelAddButton_);
		layout.putConstraint(SpringLayout.NORTH, channelDownButton_, 5, SpringLayout.SOUTH, channelUpButton_);

		// === INFO SECTION ========================================
		// Add info labels
		labelDataSize_ = new JLabel("Dataset size: -");
		mainPanel_.add(labelDataSize_);
		layout.putConstraint(SpringLayout.WEST, labelDataSize_, 40, SpringLayout.EAST, channelAddButton_);
		layout.putConstraint(SpringLayout.NORTH, labelDataSize_, 0, SpringLayout.NORTH, listScroller);

		labelDuration_ = new JLabel("Dataset duration: -");
		mainPanel_.add(labelDuration_);
		layout.putConstraint(SpringLayout.WEST, labelDuration_, 0, SpringLayout.WEST, labelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelDuration_, 10, SpringLayout.SOUTH, labelDataSize_);

		labelExposure_ = new JLabel("Camera exposure: -");
		mainPanel_.add(labelExposure_);
		layout.putConstraint(SpringLayout.WEST, labelExposure_, 0, SpringLayout.WEST, labelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelExposure_, 10, SpringLayout.SOUTH, labelDuration_);

		labelFramerate_ = new JLabel("Frame rate: -");
		mainPanel_.add(labelFramerate_);
		layout.putConstraint(SpringLayout.WEST, labelFramerate_, 0, SpringLayout.WEST, labelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelFramerate_, 10, SpringLayout.SOUTH, labelExposure_);

		labelImageSize_ = new JLabel("Image size: -");
		mainPanel_.add(labelImageSize_);
		layout.putConstraint(SpringLayout.WEST, labelImageSize_, 0, SpringLayout.WEST, labelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelImageSize_, 10, SpringLayout.SOUTH, labelFramerate_);

		labelPixelDataSize_ = new JLabel("Bytes per pixel: -");
		mainPanel_.add(labelPixelDataSize_);
		layout.putConstraint(SpringLayout.WEST, labelPixelDataSize_, 0, SpringLayout.WEST, labelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelPixelDataSize_, 10, SpringLayout.SOUTH, labelImageSize_);

		labelPixelSize_ = new JLabel("Pixel size: -");
		mainPanel_.add(labelPixelSize_);
		layout.putConstraint(SpringLayout.WEST, labelPixelSize_, 0, SpringLayout.WEST, labelPixelDataSize_);
		layout.putConstraint(SpringLayout.NORTH, labelPixelSize_, 10, SpringLayout.SOUTH, labelPixelDataSize_);

		// Add start acquisition button
		startAcqButton_ = new JButton("Start Acquisition");
		startAcqButton_.setToolTipText("Start Data Acquisition");
		startAcqButton_.setMargin(new Insets(5, 15, 5, 15));
		startAcqButton_.addActionListener((final ActionEvent e) -> startAcquisition());
		mainPanel_.add(startAcqButton_);
		layout.putConstraint(SpringLayout.WEST, startAcqButton_, 300, SpringLayout.WEST, mainPanel_);
		layout.putConstraint(SpringLayout.EAST, startAcqButton_, -300, SpringLayout.EAST, mainPanel_);
		layout.putConstraint(SpringLayout.SOUTH, startAcqButton_, -5, SpringLayout.SOUTH, mainPanel_);

		// Add start scan button
		startScanButton_ = new JButton("Scan");
		startScanButton_.setToolTipText("Start Plate Scan");
		startScanButton_.setMargin(new Insets(5, 15, 5, 15));
		startScanButton_.addActionListener((final ActionEvent e) -> startScan());
		mainPanel_.add(startScanButton_);
		layout.putConstraint(SpringLayout.WEST, startScanButton_, 20, SpringLayout.WEST, mainPanel_);
		layout.putConstraint(SpringLayout.SOUTH, startScanButton_, -135, SpringLayout.SOUTH, mainPanel_);

		// add AF checkbox
		cbAutoFocus_ = new JCheckBox("Auto Focus");
		cbAutoFocus_.setMargin(new Insets(5, 15, 5, 15));
		mainPanel_.add(cbAutoFocus_);
		layout.putConstraint(SpringLayout.WEST, cbAutoFocus_, 20, SpringLayout.EAST, startScanButton_);
		layout.putConstraint(SpringLayout.SOUTH, cbAutoFocus_, -135, SpringLayout.SOUTH, mainPanel_);

		// Add stop acquisition button
		stopAcqButton_ = new JButton("Stop Acquisition");
		stopAcqButton_.setToolTipText("Stop Data Acquisition");
		stopAcqButton_.setMargin(new Insets(5, 15, 5, 15));
		stopAcqButton_.addActionListener((final ActionEvent e) -> stopAcquisition());
		stopAcqButton_.setPreferredSize(new Dimension(170, 35));
		stopAcqButton_.setVisible(false);
		mainPanel_.add(stopAcqButton_);
		layout.putConstraint(SpringLayout.EAST, stopAcqButton_, -20, SpringLayout.EAST, mainPanel_);
		layout.putConstraint(SpringLayout.SOUTH, stopAcqButton_, -5, SpringLayout.SOUTH, mainPanel_);

		// Add progress bar
		progressBar_ = new JProgressBar();
		progressBar_.setValue(0);
		progressBar_.setMinimum(0);
		progressBar_.setStringPainted(true);
		progressBar_.setString("0 / 0 (0%)");
		progressBar_.setVisible(false);
		mainPanel_.add(progressBar_);
		layout.putConstraint(SpringLayout.WEST, progressBar_, 20, SpringLayout.WEST, mainPanel_);
		layout.putConstraint(SpringLayout.EAST, progressBar_, -40, SpringLayout.WEST, stopAcqButton_);
		layout.putConstraint(SpringLayout.SOUTH, progressBar_, -8, SpringLayout.SOUTH, mainPanel_);

		// Add circular buffer status label
		labelCbuffStatus_ = new JLabel("Sequence buffer usage:");
		labelCbuffStatus_.setVisible(false);
		mainPanel_.add(labelCbuffStatus_);
		layout.putConstraint(SpringLayout.WEST, labelCbuffStatus_, 0, SpringLayout.WEST, progressBar_);
		layout.putConstraint(SpringLayout.SOUTH, labelCbuffStatus_, -10, SpringLayout.NORTH, progressBar_);

		// Add circular buffer status bar
		cbuffCapacity_ = new JProgressBar();
		cbuffCapacity_.setValue(0);
		cbuffCapacity_.setMinimum(0);
		cbuffCapacity_.setStringPainted(true);
		cbuffCapacity_.setString("0 / 0 (0%)");
		cbuffCapacity_.setVisible(false);
		mainPanel_.add(cbuffCapacity_);
		layout.putConstraint(SpringLayout.WEST, cbuffCapacity_, 10, SpringLayout.EAST, labelCbuffStatus_);
		layout.putConstraint(SpringLayout.EAST, cbuffCapacity_, 200, SpringLayout.EAST, labelCbuffStatus_);
		layout.putConstraint(SpringLayout.SOUTH, cbuffCapacity_, -8, SpringLayout.NORTH, progressBar_);

		// Add label for storage throughput frame-rate during acquisition
		labelAcqStoreFps_ = new JLabel("Storage throughput: -");
		labelAcqStoreFps_.setVisible(false);
		mainPanel_.add(labelAcqStoreFps_);
		layout.putConstraint(SpringLayout.WEST, labelAcqStoreFps_, 0, SpringLayout.WEST, labelCbuffStatus_);
		layout.putConstraint(SpringLayout.SOUTH, labelAcqStoreFps_, -15, SpringLayout.NORTH, labelCbuffStatus_);

		// Add label for storage write frame-rate during acquisition
		labelAcqWriteFps_ = new JLabel("Write frame rate: -");
		labelAcqWriteFps_.setVisible(false);
		mainPanel_.add(labelAcqWriteFps_);
		layout.putConstraint(SpringLayout.WEST, labelAcqWriteFps_, 0, SpringLayout.WEST, labelCbuffStatus_);
		layout.putConstraint(SpringLayout.SOUTH, labelAcqWriteFps_, -35, SpringLayout.NORTH, labelCbuffStatus_);

		// Add circular buffer memory footprint label
		labelCbuffMemory_ = new JLabel("0 MB");
		labelCbuffMemory_.setVisible(false);
		mainPanel_.add(labelCbuffMemory_);
		layout.putConstraint(SpringLayout.WEST, labelCbuffMemory_, 10, SpringLayout.EAST, cbuffCapacity_);
		layout.putConstraint(SpringLayout.NORTH, labelCbuffMemory_, 0, SpringLayout.NORTH, labelCbuffStatus_);

		// Add status bar
		SpringLayout statusbarlayout = new SpringLayout();
		JPanel statusPanel = new JPanel();
		statusPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		statusPanel.setPreferredSize(new Dimension(mainPanel_.getWidth(), 25));
		statusPanel.setLayout(statusbarlayout);
		getContentPane().add(statusPanel, BorderLayout.PAGE_END);

		statusInfo_ = new JLabel("Ready");
		statusInfo_.setHorizontalAlignment(SwingConstants.LEFT);
		statusPanel.add(statusInfo_);
		statusbarlayout.putConstraint(SpringLayout.WEST, statusInfo_, 10, SpringLayout.WEST, statusPanel);
		statusbarlayout.putConstraint(SpringLayout.SOUTH, statusInfo_, -1, SpringLayout.SOUTH, statusPanel);

		loadSettings();
		updateAcqInfo();
	}

	/**
	 * Release the window
	 */
	public void dispose() {
		saveSettings();
		super.dispose();
	}

	/**
	 * Save plugin settings
	 */
	protected void saveSettings() {
		Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
		if(prefs == null)
			return;
		JSONArray jarr = new JSONArray();
		try {
			for(int i = 0; i < channelsDataModel_.getRowCount(); i++) {
				JSONObject chobj = new JSONObject();
				chobj.put(CFG_CHNAME, (String)channelsDataModel_.getValueAt(i, 0));
				chobj.put(CFG_CHEXP, (double)channelsDataModel_.getValueAt(i, 1));
				chobj.put(CFG_CHINT, (int)channelsDataModel_.getValueAt(i, 2));
				jarr.put(chobj);
			}
		} catch(Exception e) {
			jarr = new JSONArray();
			mmstudio_.getLogManager().logError(e);
		}

		prefs.put(CFG_LOCATION, dataDir_);
		prefs.put(CFG_NAME, acqName_);
		prefs.putInt(CFG_TIMEPOINTS, timePoints_);
		prefs.putBoolean(CFG_TIMELAPSE, cbTimeLapse_.isSelected());
		prefs.putInt(CFG_TIMEINTERVAL, timeIntervalMs_);
		prefs.putInt(CFG_CHUNKSIZE, chunkSize_);
		prefs.putInt(CFG_FLUSHCYCLE, flushCycle_);
		prefs.putBoolean(CFG_DIRECTIO, cbDirectIo_.isSelected());
		prefs.putBoolean(CFG_FASTEXP, cbFastExp_.isSelected());
		prefs.put(CFG_CHANNELS, jarr.toString());
		prefs.putInt(CFG_WNDX, super.getBounds().x);
		prefs.putInt(CFG_WNDY, super.getBounds().y);
		prefs.putInt(CFG_WNDW, super.getBounds().width);
		prefs.putInt(CFG_WNDH, super.getBounds().height);
		prefs.putInt(CFG_LOGVIEWDIVIDER, logSplitPane_.getDividerLocation());
		prefs.putBoolean(CFG_AUTOFOCUS, cbAutoFocus_.isSelected());
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
		chunkSize_ = prefs.getInt(CFG_CHUNKSIZE, 0);
		flushCycle_ = prefs.getInt(CFG_FLUSHCYCLE, 0);
		boolean tl = prefs.getBoolean(CFG_TIMELAPSE, false);
		boolean dio = prefs.getBoolean(CFG_DIRECTIO, false);
		cbAutoFocus_.setSelected(prefs.getBoolean(CFG_AUTOFOCUS, false));
		boolean fastexp = prefs.getBoolean(CFG_FASTEXP, false);
		int wndx = prefs.getInt(CFG_WNDX, super.getBounds().x);
		int wndy = prefs.getInt(CFG_WNDY, super.getBounds().y);
		int wndw = prefs.getInt(CFG_WNDW, super.getBounds().width);
		int wndh = prefs.getInt(CFG_WNDH, super.getBounds().height);
		int logdiv = prefs.getInt(CFG_LOGVIEWDIVIDER, 0);

		// Load channels configuration
		String chlist = prefs.get(CFG_CHANNELS, "");
		channels_.clear();
		channelsDataModel_.clear();
		if(!chlist.isEmpty()) {
			try {
				JSONArray jarr = new JSONArray(chlist);
				for(int i = 0; i < jarr.length(); i++) {
					JSONObject chobj = jarr.getJSONObject(i);
					if(chobj == null || !chobj.has(CFG_CHNAME))
						continue;
					double defaultTTLexp = 10.0;
					int defaultTTLint = 500;
					channelsDataModel_.add(chobj.getString(CFG_CHNAME),
							chobj.optDouble(CFG_CHEXP, defaultTTLexp),
							chobj.optInt(CFG_CHINT, defaultTTLint));
					channels_.add(new ChannelInfo(chobj.getString(CFG_CHNAME),
							chobj.optDouble(CFG_CHEXP, defaultTTLexp),
							chobj.optInt(CFG_CHINT, defaultTTLint)));
				}
			} catch(Exception e) {
				mmstudio_.getLogManager().logError(e);
			}
		}

		// Update UI
		tbLocation_.setText(dataDir_);
		tbName_.setText(acqName_);
		tbTimePoints_.setValue(timePoints_);
		cbTimeLapse_.setSelected(tl);
		cbDirectIo_.setSelected(dio);
		cbFastExp_.setSelected(fastexp);
		tbTimeInterval_.setValue(timeIntervalMs_);
		tbTimeInterval_.setEnabled(tl);
		if(logdiv > 0)
			logSplitPane_.setDividerLocation(logdiv);
		super.setBounds(wndx, wndy, wndw, wndh);
	}

	/**
	 * Load existing data acquisition
	 */
	protected void loadAcquisition() {
		File result = FileDialogs.openFile(this, "Please choose a valid dataset", FileDialogs.MM_DATA_SET);
		if(result == null)
			return;

		loadActive_ = true;
		loadWorker_ = new LoadRunner(mmstudio_, result.getAbsolutePath());
		loadWorker_.addListener(this);
		loadWorker_.start();

		if (result.isDirectory())
			statusInfo_.setText(String.format("Loading: %s...", result.getAbsolutePath()));
		else
			statusInfo_.setText(String.format("Loading: %s...", result.getParent()));
		loadedDatastore_ = mmstudio_.data().createRAMDatastore();
		DisplayWindow display = mmstudio_.displays().createDisplay(loadedDatastore_);
		display.setCustomTitle(result.getAbsolutePath());
		updateFormState();
		updateChannelCommands();
	}

	/**
	 * Cancel dataset loading
	 */
	protected void cancelLoadAcquisition() {
		loadActive_ = false;
		loadWorker_.cancel();
		try {
			if(loadWorker_.isAlive())
				loadWorker_.join();
		}catch(InterruptedException e) {
			mmstudio_.getLogManager().logError(e);
		}
		loadWorker_ = null;
		statusInfo_.setText("Loading cancelled by the user");
		updateFormState();
		updateChannelCommands();
	}

	/**
	 * Start data acquisition
	 */
	protected void startAcquisition() {
		runnerActive_ = true;
		totalStoreTime_ = 0;
		acqStartTime_ = 0;
		acqFirstImage_ = 0;
		acqWorker_ = new AcqRunner(core_, dataDir_, acqName_, cbTimeLapse_.isSelected(), timePoints_, channels_, timeIntervalMs_, chunkSize_, cbDirectIo_.isSelected(), flushCycle_, cbFastExp_.isSelected());
		acqWorker_.addListener(this);
		acqWorker_.start();

		Thread timerThread_ = new Thread(this::updateAcqTime);
		timerThread_.start();

		statusInfo_.setText("Starting acquisition...");
		updateFormState();
		updateChannelCommands();

		// Show image view
		if(currentAcq_ != null) {
			try {
				currentAcq_.deleteAllImages();
			} catch(IOException e) {
				mmstudio_.getLogManager().logError(e);
			}
		} else
			currentAcq_ = mmstudio_.getDataManager().createRewritableRAMDatastore();
		if(mmstudio_.getDisplayManager().getActiveDataViewer() == null)
			mmstudio_.getDisplayManager().createDisplay(currentAcq_);
	}

	private void startScan() {
		runnerActive_ = true;
		totalStoreTime_ = 0;
		acqStartTime_ = 0;
		acqFirstImage_ = 0;
		scanWorker_ = new ScanRunner(mmstudio_, dataDir_, acqName_, channels_, chunkSize_, cbDirectIo_.isSelected(), flushCycle_, cbAutoFocus_.isSelected());
		scanWorker_.addListener(this);
		scanWorker_.start();
		statusInfo_.setText("Starting plate scan...");
		updateFormState();
		updateChannelCommands();
	}

	/**
	 * Stop / cancel data acquisition
	 */
	protected void stopAcquisition() {
		runnerActive_ = false;
		if (acqWorker_ != null) {
			acqWorker_.cancel();
			try {
				if(acqWorker_.isAlive())
					acqWorker_.join();
			} catch(InterruptedException e) {
				mmstudio_.getLogManager().logError(e);
			}
			acqWorker_ = null;
		}
		if (scanWorker_ != null) {
			scanWorker_.cancel();
			try {
				if(scanWorker_.isAlive())
					scanWorker_.join();
			} catch(InterruptedException e) {
				mmstudio_.getLogManager().logError(e);
			}
			acqWorker_ = null;
		}

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
		ChannelSelectionDlg wnd = new ChannelSelectionDlg(this, allchannels, null, null, null);
		wnd.setVisible(true);

		String channel = wnd.getSelectedChannel();
		if(channel == null || channel.isEmpty()) {
			statusInfo_.setText("No channel selected");
			return;
		}
		channels_.add(new ChannelInfo(channel, wnd.getExposure(), wnd.getIntensity()));
		channelsDataModel_.add(channel, wnd.getExposure(), wnd.getIntensity());
		updateAcqInfo();
	}

	/**
	 * Modify selected channel
	 */
	protected void modifyChannel() {
		if(listChannels_.getSelectedRow() < 0 || listChannels_.getSelectedRow() >= channels_.size())
			return;

		int ind = listChannels_.getSelectedRow();
		String chname = channelsDataModel_.getValueAt(ind, 0).toString();
		double exp = (double)channelsDataModel_.getValueAt(ind, 1);
		int intensity = (int)channelsDataModel_.getValueAt(ind, 2);

		// Show channel selection dialog
		ChannelSelectionDlg wnd = new ChannelSelectionDlg(this, null, chname, exp, intensity);
		wnd.setVisible(true);

		String channel = wnd.getSelectedChannel();
		if(!Objects.equals(channel, chname)) {
			statusInfo_.setText("No channel selected");
			return;
		}
		channelsDataModel_.update(ind, chname, wnd.getExposure(), wnd.getIntensity());
		channels_.get(ind).ttlExposureMs = exp;
		channels_.get(ind).intensity = intensity;
	}

	/**
	 * Remove selected channel
	 */
	protected void removeChannel() {
		if(listChannels_.getSelectedRow() < 0 || listChannels_.getSelectedRow() >= channels_.size())
			return;

		channels_.remove(listChannels_.getSelectedRow());
		channelsDataModel_.remove(listChannels_.getSelectedRow());
		updateAcqInfo();
	}

	/**
	 * Move selected channel up
	 */
	protected void moveChannelUp() {
		int ind = listChannels_.getSelectedRow();
		if(ind < 1 || ind >= channels_.size())
			return;
		channelsDataModel_.moveUp(ind);
		SwingUtilities.invokeLater(() -> listChannels_.setRowSelectionInterval(ind - 1, ind - 1));
	}

	/**
	 * Move selected channel down
	 */
	protected void moveChannelDown() {
		int ind = listChannels_.getSelectedRow();
		if(ind < 0 || ind >= channels_.size() - 1)
			return;
		channelsDataModel_.moveDown(ind);
		SwingUtilities.invokeLater(() -> listChannels_.setRowSelectionInterval(ind + 1, ind + 1));
	}

	/**
	 * Update channel list command buttons
	 */
	protected void updateChannelCommands() {
		int ind = listChannels_.getSelectedRow();
		channelAddButton_.setEnabled(!runnerActive_ && !loadActive_);
		channelModifyButton_.setEnabled(!runnerActive_ && !loadActive_ && listChannels_.getSelectedRow() >= 0 && !channels_.isEmpty());
		channelRemoveButton_.setEnabled(!runnerActive_ && !loadActive_ && listChannels_.getSelectedRow() >= 0 && !channels_.isEmpty());
		channelUpButton_.setEnabled(!runnerActive_ && !loadActive_ && listChannels_.getSelectedRow() > 0 && !channels_.isEmpty());
		channelDownButton_.setEnabled(!runnerActive_ && !loadActive_ && listChannels_.getSelectedRow() >= 0 && listChannels_.getSelectedRow() < channels_.size() - 1);
	}

	/**
	 * Update UI state based on the acquisition status
	 */
	protected void updateFormState() {
		startAcqButton_.setVisible(!runnerActive_);
		startAcqButton_.setEnabled(!loadActive_);
		stopAcqButton_.setVisible(runnerActive_);
		progressBar_.setVisible(runnerActive_);
		tbChunkSize_.setEnabled(!runnerActive_ && !loadActive_);
		tbFlushCycle_.setEnabled(!runnerActive_ && !loadActive_);
		cbDirectIo_.setEnabled(!runnerActive_ && !loadActive_);
		cbFastExp_.setEnabled(!runnerActive_ && !loadActive_);
		tbTimePoints_.setEnabled(!runnerActive_ && !loadActive_);
		tbTimeInterval_.setEnabled(!runnerActive_&& !loadActive_ && cbTimeLapse_.isSelected());
		cbTimeLapse_.setEnabled(!runnerActive_&& !loadActive_);
		tbLocation_.setEnabled(!runnerActive_&& !loadActive_);
		tbName_.setEnabled(!runnerActive_&& !loadActive_);
		loadButton_.setVisible(!loadActive_);
		loadButton_.setEnabled(!runnerActive_);
		cancelLoadButton_.setVisible(loadActive_);
		chooseLocationButton_.setEnabled(!runnerActive_&& !loadActive_);
		listChannels_.setEnabled(!runnerActive_&& !loadActive_);
		labelCbuffStatus_.setVisible(runnerActive_);
		cbuffCapacity_.setVisible(runnerActive_);
		labelCbuffMemory_.setVisible(runnerActive_);
		labelAcqStoreFps_.setVisible(runnerActive_);
		labelAcqWriteFps_.setVisible(runnerActive_);
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
			timeIntervalMs_ = (int)tbTimeInterval_.getValue();
		} catch(NumberFormatException e) {
			tbTimeInterval_.setValue(timeIntervalMs_);
		}
		try {
			flushCycle_ = (int)tbFlushCycle_.getValue();
		} catch(NumberFormatException e) {
			tbFlushCycle_.setValue(flushCycle_);
		}
		try {
			chunkSize_ = (int)tbChunkSize_.getValue();
		} catch(NumberFormatException e) {
			tbChunkSize_.setValue(chunkSize_);
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
		long acqRunTime = System.nanoTime();
		while(runnerActive_) {
			try {
				long currtime = System.nanoTime();
				double tottimesec = (currtime - acqRunTime) / 1000000000.0;
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
	public void notifyAcqCompleted() {
		runnerActive_ = false;
		statusInfo_.setText("Acquisition completed successfully");
		updateFormState();
		updateChannelCommands();
	}

	/**
	 * Acquisition started event handler
	 */
	@Override
	public void notifyAcqStarted() {
		acqStartTime_ = System.nanoTime();
	}

	/**
	 * Acquisition failed event handler
	 * @param msg Error message
	 */
	@Override
	public void notifyAcqFailed(String msg) {
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
	public void notifyAcqStatusUpdate(int curr, int total, Image image, int bufffree, int bufftotal, double storetimems) {
		// Update start time after the first image is acquired
		if(curr == 1)
			acqFirstImage_ = System.nanoTime();

		int perc = total == 0 ? 0 : (int)Math.ceil(100.0 * (double)curr / total);
		progressBar_.setMaximum(total);
		progressBar_.setValue(curr);
		progressBar_.setString(String.format("%d / %d (%d%%)", curr, total, perc));

		int bused = bufftotal >= bufffree ? bufftotal - bufffree : 0;
		int buffperc = bufftotal == 0 ? 0 : (int)Math.ceil(100.0 * (double)bused / bufftotal);
		cbuffCapacity_.setMaximum(bufftotal);
		cbuffCapacity_.setValue(bused);
		cbuffCapacity_.setString(String.format("%d / %d (%d%%)", bused, bufftotal, buffperc));

		double cbuffmb = core_.getCircularBufferMemoryFootprint();
		if(cbuffmb > 1024.0)
			labelCbuffMemory_.setText(String.format("%.2f GB", cbuffmb / 1024.0));
		else
			labelCbuffMemory_.setText(String.format("%.1f MB", cbuffmb));
		//statusInfo_.setText(String.format("Running: %d / %d", curr, total));

		// Calculate storage driver statistics
		totalStoreTime_ += storetimems;
		double avgwritetime = curr == 0 ? 0 : totalStoreTime_ / curr;
		double avgwritefps = avgwritetime == 0 ? 0 : 1000.0 / avgwritetime;
		labelAcqWriteFps_.setText(String.format("Write frame rate: %.1f FPS", avgwritefps));

		double totalTime = (System.nanoTime() - acqFirstImage_) / 1000000000.0;
		double avgstorefps = totalTime == 0 ? 0 : (double)curr / totalTime;
		labelAcqStoreFps_.setText(String.format("Storage throughput: %.1f FPS", avgstorefps));

		// Update current image
		try {
			currentAcq_.putImage(image);
		} catch(IOException e) {
			mmstudio_.getLogManager().logError(e);
		}
	}

	@Override
	public void logMessage(String msg) {
		mmstudio_.logs().logMessage(msg);
		logView_.logMessage(msg, false);
	}

	@Override
	public void logError(String msg) {
		mmstudio_.logs().logError(msg);
		logView_.logMessage(msg, true);
	}

	/**
	 * Dataset loading completed event handler
	 * @param w Image width
	 * @param h Image height
	 * @param imgcnt Number of images
	 * @param type Pixel data type
	 */
	@Override
	public void notifyLoadCompleted(int w, int h, int imgcnt, mmcorej.StorageDataType type) {
		loadActive_ = false;
		statusInfo_.setText(String.format("Dataset loaded successfully: %d x %d, images %d, type %s", w, h, imgcnt, type));
		updateFormState();
		updateChannelCommands();
	}

	/**
	 * Dataset loading failed event handler
	 * @param msg Error message
	 */
	@Override
	public void notifyLoadFailed(String msg) {
		loadActive_ = false;
		statusInfo_.setText("Dataset load failed. " + msg);
		updateFormState();
		updateChannelCommands();
	}

	/**
	 * Dataset loading image loaded event handler
	 * @param img Image handle
	 */
	@Override
	public void notifyLoadImage(Image img) {
		try {
			loadedDatastore_.putImage(img);
		} catch(IOException e) {
			mmstudio_.getLogManager().logError(e);
		}
	}

	/**
	 * Dataset loading summary metadata loaded event handler
	 * @param meta Metadata
	 */
	public void notifyLoadSummaryMetadata(SummaryMetadata meta) {
		try {
			loadedDatastore_.setSummaryMetadata(meta);
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
		boolean wndonly = args.length > 0 && Objects.equals(args[0], "wndonly");
		if(wndonly) {
			TargaAcqWindow window = new TargaAcqWindow(null);
			window.setVisible(true);
			return;
		}

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
