package edu.uclouvain.core.nodus.services.gui;

import edu.uclouvain.core.nodus.swing.EscapeDialog;

import java.awt.EventQueue;

import javax.swing.JDialog;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JButton;

public class ServiceEditorDlg extends EscapeDialog {
	private JTextField idTextField;
	private JTextField descriptionTextField;

  /**
   * Launch the application.
   */
  public static void main(String[] args) {
  	EventQueue.invokeLater(new Runnable() {
  		public void run() {
  			try {
  				ServiceEditorDlg dialog = new ServiceEditorDlg();
  				dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
  				dialog.setVisible(true);
  			} catch (Exception e) {
  				e.printStackTrace();
  			}
  		}
  	});
  }

/**
 * Create the dialog.
 */
public ServiceEditorDlg() {
	setTitle("Service info");
	setBounds(100, 100, 450, 300);
	GridBagLayout gridBagLayout = new GridBagLayout();
	gridBagLayout.columnWidths = new int[]{0, 0, 0, 0, 0};
	gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0, 0};
	gridBagLayout.columnWeights = new double[]{0.0, 1.0, 0.0, 1.0, Double.MIN_VALUE};
	gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
	getContentPane().setLayout(gridBagLayout);
	
	JLabel idLabel = new JLabel("ID");
	GridBagConstraints gbc_idLabel = new GridBagConstraints();
	gbc_idLabel.insets = new Insets(5, 5, 5, 5);
	gbc_idLabel.anchor = GridBagConstraints.EAST;
	gbc_idLabel.gridx = 0;
	gbc_idLabel.gridy = 0;
	getContentPane().add(idLabel, gbc_idLabel);
	
	idTextField = new JTextField();
	GridBagConstraints gbc_idTextField = new GridBagConstraints();
	gbc_idTextField.gridwidth = 3;
	gbc_idTextField.insets = new Insets(0, 0, 5, 5);
	gbc_idTextField.fill = GridBagConstraints.HORIZONTAL;
	gbc_idTextField.gridx = 1;
	gbc_idTextField.gridy = 0;
	getContentPane().add(idTextField, gbc_idTextField);
	idTextField.setColumns(10);
	
	JLabel meansLabel = new JLabel("Means");
	GridBagConstraints gbc_meansLabel = new GridBagConstraints();
	gbc_meansLabel.anchor = GridBagConstraints.EAST;
	gbc_meansLabel.insets = new Insets(5, 5, 5, 5);
	gbc_meansLabel.gridx = 0;
	gbc_meansLabel.gridy = 1;
	getContentPane().add(meansLabel, gbc_meansLabel);
	
	JComboBox meansComboBox = new JComboBox();
	GridBagConstraints gbc_meansComboBox = new GridBagConstraints();
	gbc_meansComboBox.insets = new Insets(0, 0, 5, 5);
	gbc_meansComboBox.fill = GridBagConstraints.HORIZONTAL;
	gbc_meansComboBox.gridx = 1;
	gbc_meansComboBox.gridy = 1;
	getContentPane().add(meansComboBox, gbc_meansComboBox);
	
	JLabel frequencyLabel = new JLabel("Frequency");
	GridBagConstraints gbc_frequencyLabel = new GridBagConstraints();
	gbc_frequencyLabel.anchor = GridBagConstraints.EAST;
	gbc_frequencyLabel.insets = new Insets(5, 5, 5, 5);
	gbc_frequencyLabel.gridx = 0;
	gbc_frequencyLabel.gridy = 2;
	getContentPane().add(frequencyLabel, gbc_frequencyLabel);
	
	JComboBox comboBox = new JComboBox();
	GridBagConstraints gbc_comboBox = new GridBagConstraints();
	gbc_comboBox.fill = GridBagConstraints.HORIZONTAL;
	gbc_comboBox.insets = new Insets(5, 5, 5, 5);
	gbc_comboBox.anchor = GridBagConstraints.WEST;
	gbc_comboBox.gridx = 1;
	gbc_comboBox.gridy = 2;
	getContentPane().add(comboBox, gbc_comboBox);
	
	JLabel perLabel = new JLabel("per");
	GridBagConstraints gbc_perLabel = new GridBagConstraints();
	gbc_perLabel.anchor = GridBagConstraints.EAST;
	gbc_perLabel.insets = new Insets(5, 5, 5, 5);
	gbc_perLabel.gridx = 2;
	gbc_perLabel.gridy = 2;
	getContentPane().add(perLabel, gbc_perLabel);
	
	JComboBox timeUnitComboBox = new JComboBox();
	GridBagConstraints gbc_timeUnitComboBox = new GridBagConstraints();
	gbc_timeUnitComboBox.insets = new Insets(5, 5, 5, 5);
	gbc_timeUnitComboBox.fill = GridBagConstraints.HORIZONTAL;
	gbc_timeUnitComboBox.gridx = 3;
	gbc_timeUnitComboBox.gridy = 2;
	getContentPane().add(timeUnitComboBox, gbc_timeUnitComboBox);
	
	JLabel descriptionLabel = new JLabel("Description");
	GridBagConstraints gbc_descriptionLabel = new GridBagConstraints();
	gbc_descriptionLabel.anchor = GridBagConstraints.EAST;
	gbc_descriptionLabel.insets = new Insets(5, 5, 5, 5);
	gbc_descriptionLabel.gridx = 0;
	gbc_descriptionLabel.gridy = 3;
	getContentPane().add(descriptionLabel, gbc_descriptionLabel);
	
	descriptionTextField = new JTextField();
	GridBagConstraints gbc_textField = new GridBagConstraints();
	gbc_textField.gridwidth = 4;
	gbc_textField.insets = new Insets(5, 5, 5, 0);
	gbc_textField.fill = GridBagConstraints.HORIZONTAL;
	gbc_textField.gridx = 1;
	gbc_textField.gridy = 3;
	getContentPane().add(descriptionTextField, gbc_textField);
	descriptionTextField.setColumns(10);
	
	JButton cancelButton = new JButton("Cancel");
	GridBagConstraints gbc_cancelButton = new GridBagConstraints();
	gbc_cancelButton.insets = new Insets(0, 0, 0, 5);
	gbc_cancelButton.gridx = 1;
	gbc_cancelButton.gridy = 4;
	getContentPane().add(cancelButton, gbc_cancelButton);
	
	JButton saveButton = new JButton("Save");
	GridBagConstraints gbc_saveButton = new GridBagConstraints();
	gbc_saveButton.insets = new Insets(0, 0, 0, 5);
	gbc_saveButton.gridx = 3;
	gbc_saveButton.gridy = 4;
	getContentPane().add(saveButton, gbc_saveButton);
	
}

}
