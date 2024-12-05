package org.lumencor.targa;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.*;

/**
 * Channel data model
 * @author Milos Jovanovic <milos@tehnocad.rs>
 */
public class ChannelDataModel extends AbstractTableModel {
	private List<List<Object>> data_ = new LinkedList<>();
	private final String[] columns_ = { "Channel", "TTL [ms]", "INT" };

	/**
	 * Get row count
	 * @return Row count
	 */
	@Override
	public int getRowCount() {
		return data_.size();
	}

	/**
	 * Get column count
	 * @return Column count
	 */
	@Override
	public int getColumnCount() {
		return 3;
	}

	/**
	 * Get column name
	 * @param columnIndex Column index
	 * @return Column name
	 */
	@Override
	public String getColumnName(int columnIndex) {
		if(columnIndex < 0 || columnIndex >= columns_.length)
			throw new IndexOutOfBoundsException();
		return columns_[columnIndex];
	}

	/**
	 * Get cell value
	 * @param rowIndex Row index
	 * @param columnIndex Column index
	 * @return Cell value
	 */
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		List<Object> entry = data_.get(rowIndex);
		if(columnIndex < 0 || columnIndex >= columns_.length)
			throw new IndexOutOfBoundsException();
		if(columnIndex >= entry.size())
			return null;
		return entry.get(columnIndex);
	}

	/**
	 * Add row
	 * @param channel Channel name
	 * @param exp TTL exposure
	 * @param intensity Channel intensity
	 */
	public void add(String channel, double exp, int intensity) {
		List<Object> row = new ArrayList<>();
		row.add(channel);
		row.add(exp);
		row.add(intensity);
		data_.add(row);
		SwingUtilities.invokeLater(this::fireTableDataChanged);
	}

	/**
	 * Remove row
	 * @param rowIndex Row index
	 */
	public void remove(int rowIndex) {
		if(rowIndex < 0 || rowIndex >= data_.size())
			return;
		data_.remove(rowIndex);
		SwingUtilities.invokeLater(this::fireTableDataChanged);
	}

	/**
	 * Update row
	 * @param rowIndex Row index
	 * @param channel Channel name
	 * @param exp TTL exposure
	 * @param intensity Channel intensity
	 */
	public void update(int rowIndex, String channel, double exp, int intensity) {
		if(rowIndex < 0 || rowIndex >= data_.size())
			return;
		List<Object> row = data_.get(rowIndex);
		if(row == null)
			return;
		if(row.size() < 1)
			row.add(channel);
		else
			row.set(0, channel);
		if(row.size() < 2)
			row.add(exp);
		else
			row.set(1, exp);
		if(row.size() < 3)
			row.add(intensity);
		else
			row.set(2, intensity);
		SwingUtilities.invokeLater(this::fireTableDataChanged);
	}

	/**
	 * Clear rows
	 */
	public void clear() {
		data_.clear();
		SwingUtilities.invokeLater(this::fireTableDataChanged);
	}

	/**
	 * Move selected row up and hold selection
	 * @param rowIndex Row index
	 */
	public void moveUp(int rowIndex) {
		List<Object> tmp = new ArrayList<>(data_.get(rowIndex));
		data_.set(rowIndex, new ArrayList<>(data_.get(rowIndex - 1)));
		data_.set(rowIndex - 1, tmp);

		// Update table
		SwingUtilities.invokeLater(this::fireTableDataChanged);
	}

	/**
	 * Move selected row down and hold selection
	 * @param rowIndex Row index
	 */
	public void moveDown(int rowIndex) {
		List<Object> tmp = new ArrayList<>(data_.get(rowIndex));
		data_.set(rowIndex, new ArrayList<>(data_.get(rowIndex + 1)));
		data_.set(rowIndex + 1, tmp);

		// Update table
		SwingUtilities.invokeLater(this::fireTableDataChanged);
	}
}
