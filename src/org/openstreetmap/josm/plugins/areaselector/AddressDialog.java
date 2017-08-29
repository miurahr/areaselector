// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.areaselector;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompletingComboBox;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompletionListItem;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompletionManager;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;

/**
 * based on AdressDialog from building_tools.<br>
 * http://wiki.openstreetmap.org/wiki/JOSM/Plugins/BuildingsTools
 * @author Paul Woelfel (paul@woelfel.at)
 */
public class AddressDialog extends ExtendedDialog implements ChangeListener {

	public static final String TAG_HOUSENAME = "name";
	public static final String TAG_HOUSENUM = "addr:housenumber";
	public static final String TAG_STREETNAME = "addr:street";
	public static final String TAG_CITY = "addr:city";
	public static final String TAG_POSTCODE = "addr:postcode";
	public static final String TAG_COUNTRY = "addr:country";
	public static final String TAG_BUILDING = "building";
	public static final String TAG_SOURCE = "source";
	public static final String[] TAGS = {TAG_HOUSENAME, TAG_HOUSENUM, TAG_STREETNAME, TAG_CITY, TAG_POSTCODE, TAG_COUNTRY, TAG_BUILDING, TAG_SOURCE};

	public static final String PREF = AreaSelectorAction.PLUGIN_NAME+".last.";

	public static final String PREF_HOUSENUM = PREF+"housenum",
			PREF_STREETNAME = PREF+"streetname",
			PREF_CITY = PREF+"street",
			PREF_POSTCODE = PREF+"postcode",
			PREF_COUNTRY = PREF+"country",
			PREF_BUILDING = PREF+"building",
			PREF_TAGS = PREF+"tags",
			PREF_HOUSENUM_CHANGE = PREF+"housenum.change",
			PREF_SOURCE = PREF + "source",
			PREF_DIALOG_X = PREF+"dialog.x",
			PREF_DIALOG_Y = PREF+"dialog.y";

	protected JTextField houseNumField;
	protected ButtonGroup houseNumChange;

	protected AutoCompletingComboBox streetNameField, cityField, postCodeField, countryField, houseNameField, buildingField, tagsField, sourceField;

	protected static final String[] BUTTON_TEXTS = new String[] {tr("OK"), tr("Cancel")};
	protected static final String[] BUTTON_ICONS = new String[] {"ok.png", "cancel.png"};

	protected final JPanel panel = new JPanel(new GridBagLayout());

	protected OsmPrimitive originalOsmObject, osmObject;

	protected static Collection<AutoCompletionListItem> aciTags;

	protected int changeNum = 0;

	protected Vector<Component> fields;

	protected static Logger log = LogManager.getLogger(AddressDialog.class);

	protected final void addLabelled(String str, Component c) {
		JLabel label = new JLabel(str);
		panel.add(label, GBC.std());
		label.setLabelFor(c);
		panel.add(c, GBC.eol().fill(GBC.HORIZONTAL));
	}

	public AddressDialog(OsmPrimitive selectedOsmObject){
		this(selectedOsmObject, null);
	}

	public AddressDialog(OsmPrimitive selectedOsmObject, OsmPrimitive originalOsmObject) {
		super(Main.parent, tr("Building address"), BUTTON_TEXTS, true);

		this.originalOsmObject = originalOsmObject != null ? originalOsmObject : selectedOsmObject;
		this.osmObject = selectedOsmObject;
		//		this.osmObject = selectedOsmObject instanceof Node ? new Node(((Node) selectedOsmObject)) : selectedOsmObject instanceof Way ? new Way((Way) selectedOsmObject) : selectedOsmObject instanceof Relation ? new Relation((Relation) selectedOsmObject): selectedOsmObject;

		contentInsets = new Insets(15, 15, 5, 15);
		setButtonIcons(BUTTON_ICONS);

		setContent(panel);
		setDefaultButton(1);

		AutoCompletionManager acm = MainApplication.getLayerManager().getEditDataSet().getAutoCompletionManager();

		houseNameField = new AutoCompletingComboBox();
		houseNameField.setPossibleACItems(acm.getValues(TAG_HOUSENAME));
		houseNameField.setEditable(true);

		houseNumField = new JTextField();
		houseNumField.setPreferredSize(new Dimension(100, 24));

		String numChange = Main.pref.get(PREF_HOUSENUM_CHANGE, "0");
		try {
			changeNum = Integer.parseInt(numChange);
			if (changeNum != 0) {
				String hn = Main.pref.get(PREF_HOUSENUM, "").replaceAll("^([0-9]*).*$", "$1");
				houseNumField.setText(Integer.toString(Integer.parseInt(hn)+changeNum));
			}

		} catch (NumberFormatException e) {
			Logging.debug(e);
		}

		JPanel houseNumPanel = new JPanel();
		houseNumPanel.add(houseNumField);

		houseNumChange = new ButtonGroup();

		for (int i = -2; i <= 2; i++) {
			JRadioButton radio = new JRadioButton((i == 0 ? tr("empty") : ((i > 0 ? "+" : "")+Integer.toString(i))));
			radio.setActionCommand(Integer.toString(i));

			if (changeNum == i) {
				radio.setSelected(true);
			}
			radio.addChangeListener(this);
			houseNumChange.add(radio);
			houseNumPanel.add(radio);
		}

		JButton skip = new JButton(tr("skip"));
		skip.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (changeNum != 0) {
					try {
						houseNumField.setText(Integer.toString(Integer.parseInt(houseNumField.getText())+changeNum));
					} catch (NumberFormatException ex) {
						Logging.debug(ex);
					}
				}
			}
		});
		houseNumPanel.add(skip);

		streetNameField = new AutoCompletingComboBox();
		streetNameField.setPossibleACItems(acm.getValues(TAG_STREETNAME));
		streetNameField.setEditable(true);
		streetNameField.setSelectedItem(Main.pref.get(PREF_STREETNAME));

		cityField = new AutoCompletingComboBox();
		cityField.setPossibleACItems(acm.getValues(TAG_CITY));
		cityField.setEditable(true);
		cityField.setSelectedItem(Main.pref.get(PREF_CITY));

		postCodeField = new AutoCompletingComboBox();
		postCodeField.setPossibleACItems(acm.getValues(TAG_POSTCODE));
		postCodeField.setEditable(true);
		postCodeField.setSelectedItem(Main.pref.get(PREF_POSTCODE));

		countryField = new AutoCompletingComboBox();
		countryField.setPossibleACItems(acm.getValues(TAG_COUNTRY));
		countryField.setEditable(true);
		countryField.setSelectedItem(Main.pref.get(PREF_COUNTRY));


		buildingField = new AutoCompletingComboBox();
		buildingField.setPossibleACItems(acm.getValues(TAG_BUILDING));
		buildingField.setEditable(true);
		buildingField.setSelectedItem(Main.pref.get(PREF_BUILDING));


		if (aciTags == null) {
			aciTags = new ArrayList<>();
		}
		aciTags.add(new AutoCompletionListItem(Main.pref.get(PREF_TAGS)));

		StringBuilder tagsSB = new StringBuilder();

		List<String> fieldTags = Arrays.asList(TAGS);
		for (String key : selectedOsmObject.keySet()){
			if (!fieldTags.contains(key)){
				tagsSB.append(key);
				tagsSB.append("=");
				tagsSB.append(selectedOsmObject.get(key));
				tagsSB.append(";");
			}
		}
		String otherTags = tagsSB.toString();
		if (!otherTags.isEmpty()) {
			aciTags.add(new AutoCompletionListItem(otherTags));
		}


		tagsField = new AutoCompletingComboBox();
		tagsField.setPossibleACItems(aciTags);
		tagsField.setEditable(true);
		tagsField.setSelectedItem(otherTags.length() > 0 ? otherTags : Main.pref.get(PREF_TAGS));

		sourceField = new AutoCompletingComboBox();
		List<AutoCompletionListItem> sourceValues = acm.getValues(TAG_SOURCE);

		ArrayList<String> sources = new ArrayList<>();
		for (Layer layer : MainApplication.getLayerManager().getVisibleLayersInZOrder()) {
			if (layer.isVisible() && layer.isBackgroundLayer()) {
				sources.add(layer.getName());
			}
		}
		Collections.reverse(sources);
		String source = sources.stream().map(Object::toString).collect(Collectors.joining("; ")).toString();
		if (!source.isEmpty()) {
			sourceValues.add(0, new AutoCompletionListItem(source));
		}

		sourceField.setPossibleACItems(sourceValues);
		sourceField.setEditable(true);
		sourceField.setPreferredSize(new Dimension(400, 24));
		sourceField.setSelectedItem(Main.pref.get(PREF_SOURCE));

		JLabel houseNumLabel = new JLabel(tr("House number:"));
		houseNumLabel.setLabelFor(houseNumField);
		panel.add(houseNumLabel, GBC.std());
		panel.add(houseNumPanel, GBC.eol().fill(GBC.HORIZONTAL));

		addLabelled(tr("Street:"), streetNameField);
		addLabelled(tr("City:"), cityField);
		addLabelled(tr("Post code:"), postCodeField);
		addLabelled(tr("Country:"), countryField);
		addLabelled(tr("Building:"), buildingField);
		addLabelled(tr("Tags:"), tagsField);
		addLabelled(tr("Source:"), sourceField);

		addLabelled(tr("Name:"), houseNameField);
		houseNumField.setName(TAG_HOUSENUM);
		streetNameField.setName(TAG_STREETNAME);
		cityField.setName(TAG_CITY);
		postCodeField.setName(TAG_POSTCODE);
		buildingField.setName(TAG_BUILDING);
		tagsField.setName("tags");
		houseNameField.setName(TAG_HOUSENAME);
		sourceField.setName(TAG_SOURCE);

		fields = new Vector<>();
		Component[] fieldArr = {
				houseNumField, streetNameField, cityField, postCodeField,
				countryField, buildingField, tagsField, sourceField, houseNameField};
		fields.addAll(Arrays.asList(fieldArr));

		for (Component field: fields){
			if (field instanceof AutoCompletingComboBox){
				AutoCompletingComboBox combox = (AutoCompletingComboBox) field;
				if (selectedOsmObject.hasKey(combox.getName())){
					combox.setSelectedItem(selectedOsmObject.get(combox.getName()));
				}
			}else if (field instanceof JTextField){
				JTextField textField = (JTextField) field;
				if (selectedOsmObject.hasKey(textField.getName())){
					textField.setText(selectedOsmObject.get(textField.getName()));
				}
			}
		}

		this.setFocusTraversalPolicy(new FocusTraversalPolicy() {

			@Override
			public Component getLastComponent(Container aContainer) {
				return fields.get(fields.size()-1);
			}

			@Override
			public Component getFirstComponent(Container aContainer) {
				return fields.get(0);
			}

			@Override
			public Component getDefaultComponent(Container aContainer) {
				return fields.get(0);
			}

			@Override
			public Component getComponentBefore(Container aContainer, Component aComponent) {
				int ix = getIndex(aComponent);
				return fields.get(ix-1 >= 0 ? ix-1 : fields.size()-1);
			}

			@Override
			public Component getComponentAfter(Container aContainer, Component aComponent) {
				int ix = getIndex(aComponent);
				return fields.get(ix+1 < fields.size() ? ix+1 : 0);
			}

			public int getIndex(Component c) {
				for (int i = 0; i < fields.size(); i++) {

					if (fields.get(i).equals(c)) {
						return i;
					} else if (fields.get(i) instanceof AutoCompletingComboBox) {
						AutoCompletingComboBox ac = (AutoCompletingComboBox) fields.get(i);
						if (Arrays.asList(ac.getComponents()).contains(c)) {
							return i;
						}
					}
				}
				return -1;
			}
		});

		setContent(panel);
		setupDialog();
		this.setSize(700, 400);

		try {
			this.setLocation(Integer.parseInt(Main.pref.get(PREF_DIALOG_X)), Integer.parseInt(Main.pref.get(PREF_DIALOG_Y)));
		} catch (NumberFormatException e) {
			Logging.debug(e);
		}
	}

	protected String getAutoCompletingComboBoxValue(AutoCompletingComboBox box) {
		Object item = box.getSelectedItem();
		if (item != null) {
			if (item instanceof String) {
				return (String) item;
			}
			if (item instanceof AutoCompletionListItem) {
				return ((AutoCompletionListItem) item).getValue();
			}
			return item.toString();
		} else {
			return "";
		}
	}

	public final void saveValues() {
		String tags = getAutoCompletingComboBoxValue(tagsField);

		Main.pref.put(PREF_HOUSENUM, houseNumField.getText());
		Main.pref.put(PREF_STREETNAME, getAutoCompletingComboBoxValue(streetNameField));
		Main.pref.put(PREF_CITY, getAutoCompletingComboBoxValue(cityField));
		Main.pref.put(PREF_POSTCODE, getAutoCompletingComboBoxValue(postCodeField));
		Main.pref.put(PREF_COUNTRY, getAutoCompletingComboBoxValue(countryField));
		Main.pref.put(PREF_BUILDING, getAutoCompletingComboBoxValue(buildingField));
		Main.pref.put(PREF_SOURCE, getAutoCompletingComboBoxValue(sourceField));
		Main.pref.put(PREF_TAGS, tags);
		Main.pref.put(PREF_HOUSENUM_CHANGE, houseNumChange.getSelection().getActionCommand());


		updateTag(TAG_HOUSENAME, getAutoCompletingComboBoxValue(houseNameField));
		updateTag(TAG_HOUSENUM, houseNumField.getText());
		updateTag(TAG_STREETNAME, getAutoCompletingComboBoxValue(streetNameField));
		updateTag(TAG_CITY, getAutoCompletingComboBoxValue(cityField));
		updateTag(TAG_POSTCODE, getAutoCompletingComboBoxValue(postCodeField));
		updateTag(TAG_COUNTRY, getAutoCompletingComboBoxValue(countryField));
		updateTag(TAG_BUILDING, getAutoCompletingComboBoxValue(buildingField));
		updateTag(TAG_SOURCE, getAutoCompletingComboBoxValue(sourceField));

		if (!tags.isEmpty()) {
			AutoCompletionListItem aci = new AutoCompletionListItem(tags);
			if (!aciTags.contains(aci)) {
				aciTags.add(aci);
			}

			String[] alltags = tags.split(" *; *");
			for (int i = 0; i < alltags.length; i++) {
				String[] kv = alltags[i].split(" *= *");
				if (kv.length >= 2) {
					updateTag(kv[0], kv[1]);
				}
			}
		}
	}

	public void setHouseNumChange(int num) {
		Main.pref.put(PREF_HOUSENUM_CHANGE, Integer.toString(num));
	}

	public void updateTag(String tag, String value) {
		if (value == null || value.isEmpty()) {
			if (osmObject.get(tag) != null) {
				osmObject.remove(tag);
			}
		} else {
			osmObject.put(tag, value);
		}
	}

	public OsmPrimitive showAndSave() {
		this.showDialog();
		if (this.getValue() == 1) {
			this.saveValues();
			Collection<Command> cmds = new LinkedList<>();
			log.info("updated properties "+osmObject + "\n" + osmObject.getKeys());
			cmds.add(new ChangeCommand(originalOsmObject, osmObject));
			Command c = new SequenceCommand(tr("update building info"), cmds);
			MainApplication.undoRedo.add(c);
			MainApplication.getLayerManager().getEditDataSet().setSelected(osmObject);
		}

		Main.pref.put(PREF_DIALOG_X, Integer.toString(this.getLocation().x));
		Main.pref.put(PREF_DIALOG_Y, Integer.toString(this.getLocation().y));

		return osmObject;
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		try {
			JRadioButton rb = (JRadioButton) e.getSource();
			changeNum = Integer.parseInt(rb.getActionCommand());

		} catch (NumberFormatException ex) {
			Logging.debug(ex);
		}
	}
}
