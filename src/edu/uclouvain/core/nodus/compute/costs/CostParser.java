/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
 *
 * <p>Center for Operations Research and Econometrics (CORE)
 *
 * <p>http://www.uclouvain.be
 *
 * <p>This file is part of Nodus.
 *
 * <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.compute.costs;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.utils.StringUtils;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import parsii.eval.Expression;
import parsii.eval.Parser;
import parsii.eval.Scope;
import parsii.eval.Variable;
import parsii.tokenizer.ParseException;

/**
 * This cost parser is able to compute the cost of a virtual link given the cost functions written
 * in a "properties like" file. A cost parser is initialized for each group of commodities, OD class
 * and time slice and put in a queue. A series of CostParserWorkers, which number is equal to the
 * number of threads defined by the user at the assignment time, will run the cost parsers. <br>
 * The evaluation itself is performed using the "Parsii" mathematical expressions parser
 * (https://github.com/scireum/parsii). Example of a valid cost functions file: <br>
 * <br>
 * <br>
 * #----------------------------------------------------- <br>
 * # Cost functions <br>
 * #----------------------------------------------------- <br>
 * <br>
 * # Capacities of the vehicles <br>
 * AVGLOAD.2,1 = 100 <br>
 * AVGLOAD.4,1 = 20 <br>
 * # Can be overridden for a given group <br>
 * AVGLOAD.4,1.2 = 18 <br>
 * # Can be overridden of a scenario and a group <br>
 * 1.AVGLOAD.4,1.2 = 15 <br>
 * 1.AVGLOAD.4,1 = 25 <br>
 * <br>
 * # Equivalents to standard vehicles ratios (can also be overridden for scenarios/groups <br>
 * ESV.2,1 = 1 <br>
 * ESV.4,1 = 2 <br>
 * <br>
 * # Variables <br>
 * speed = 15 <br>
 * a = 10 <br>
 * b = a <br>
 * <br>
 * # Group overridden variables <br>
 * speed.2 = 10 <br>
 * # Scenario overridden variables <br>
 * 2.speed = 11 <br>
 * # Scenario and group overridden variables <br>
 * 2.speed.1 = 12 <br>
 * <br>
 * #- Functions ----------------------------------------- <br>
 * <br>
 * # load.ModeMeans <br>
 * ld.2,1 = 100 <br>
 * ld.4,1 = 120 <br>
 * <br>
 * # unload.ModeMeans <br>
 * ul.2,1 = 100 <br>
 * ul.4,1 = 120 <br>
 * <br>
 * # transit.ModeMeans <br>
 * tr.1,1 = 0 <br>
 * tr.4,1 = 0 <br>
 * <br>
 * # tranship.FromModeMeans-ToModeMeans <br>
 * tp.2,1-4,1 = 10 <br>
 * tp.4,1-2,1 = 10 <br>
 * <br>
 * # moving.modeMeans <br>
 * mv.2,1 = speed*length <br>
 * mv.4,1 = speed*length+5 <br>
 * <br>
 * # Group overridden functions <br>
 * <br>
 * # removes the mv.2,1 for group 2 <br>
 * mv.2,1.2 = null <br>
 * # new function for ld.4,1 for group 2 <br>
 * ld.4,1.2 = 125 <br>
 * # new function for ld.4,1 for group 2 and scenario 3 <br>
 * 3.ld.4,1.2 = 135 <br>
 * <br>
 *
 * @author Bart Jourquin
 */
public class CostParser {

  private static I18n i18n = Environment.getI18n();

  /**
   * Return value used when the cost function formula cannot be parsed due to an error. In such a
   * case, the cause of the error is displayed.
   */
  public static final int PARSER_ERROR = -2;

  /** Return value used when no cost function was defined for a virtual link. */
  public static final int UNDEFINED_FUNCTION = -1;

  /**
   * Returns the value of a given variable.
   *
   * @param costFunctions The properties file that contains the variable.
   * @param varName Name of the variable to retrieve
   * @param scenario The scenario for which the variable must be retrieved.
   * @param group Group of goods for which the variable has to be retrieved
   * @param odClass OD class for which the variable has to be retrieved
   * @return Value of the variable, for the current scenario, group of goods and OD class, or NaN if
   *     not found.
   */
  public static double getValue(
      Properties costFunctions, String varName, int scenario, byte group, byte odClass) {
    double value = Double.NaN;

    // Is there a specific value for this scenario, group and od class?
    if (odClass != -1) {
      value =
          PropUtils.doubleFromProperties(
              costFunctions, scenario + "." + varName + "." + group + "-" + odClass, Double.NaN);

      if (!Double.isNaN(value)) {
        return value;
      }
    }

    // Is there a specific value for this scenario and group?
    value =
        PropUtils.doubleFromProperties(
            costFunctions, scenario + "." + varName + "." + group, Double.NaN);

    if (!Double.isNaN(value)) {
      return value;
    }

    // Is there a specific value for this scenario and od class?
    if (odClass != -1) {
      value =
          PropUtils.doubleFromProperties(
              costFunctions, scenario + "." + varName + "-" + odClass, Double.NaN);

      if (!Double.isNaN(value)) {
        return value;
      }
    }

    // Is there a specific value for this scenario?
    value = PropUtils.doubleFromProperties(costFunctions, scenario + "." + varName, Double.NaN);

    if (!Double.isNaN(value)) {
      return value;
    }

    // Is there a specific value for this group and od class?
    if (odClass != -1) {
      value =
          PropUtils.doubleFromProperties(
              costFunctions, varName + "." + group + "-" + odClass, Double.NaN);

      if (!Double.isNaN(value)) {
        return value;
      }
    }

    // Is there a specific value for this od class?
    if (odClass != -1) {
      value = PropUtils.doubleFromProperties(costFunctions, varName + "-" + odClass, Double.NaN);

      if (!Double.isNaN(value)) {
        return value;
      }
    }

    // Is there a specific value for this group?
    value = PropUtils.doubleFromProperties(costFunctions, varName + "." + group, Double.NaN);

    if (!Double.isNaN(value)) {
      return value;
    }

    // Is there a generic value ?
    value = PropUtils.doubleFromProperties(costFunctions, varName, Double.NaN);

    return value;
  }

  private byte classNum;

  /** Cost functions files. Normally a property file which extension is ".cost" */
  private Properties costFunctions;

  private int currentLink = -1;

  private int currentNode = -1;

  private int currentType = VirtualLink.TYPE_MOVE;

  private String errorMessage = null;

  /** For Parsii. */
  private Expression expression;

  private byte groupNum;

  private boolean hasMoveVariables = false;

  private boolean initialized = false;

  /**
   * Vector containing all the link field names (columns names in the corresponding dbfTableModel).
   */
  private Vector<String>[] linkFieldName;

  /** Links graphic lists used to store/retrieve/update flows. */
  private EsriGraphicList[] links;

  /**
   * DbfTableModel (see OpenMap documentation for more details) containing the .dbf data of all the
   * link layers.
   */
  private DbfTableModel[] linksDbf = null;

  /** Nodus Link layers. */
  private NodusEsriLayer[] linksEsriLayer;

  /**
   * Vector containing all the node field names (columns names in the corresponding dbfTableModel).
   */
  private Vector<String>[] nodeFieldName;

  /**
   * DbfTableModel (see OpenMap documentation for more details) containing the .dbf data of all the
   * node layers.
   */
  private DbfTableModel[] nodesDbf = null;

  /** Nodus Node layers. */
  private NodusEsriLayer[] nodesEsriLayer;

  private String previousLayerVariableName;

  private NodusProject project = null;

  /** Scenario for which the costs must be computed. */
  private int scenario;

  /** For Parsii. */
  Scope scope = new Scope();

  private byte timeSlice;

  /**
   * Initializes a new cost parser for a given group, class and time slice.
   *
   * @param costFunctions Cost functions that will be parsed.
   * @param project The Nodus project.
   * @param groupNum The group of goods.
   * @param classNum The OD class.
   * @param timeSlice The time slice.
   */
  @SuppressWarnings("unchecked")
  public CostParser(
      Properties costFunctions,
      NodusProject project,
      byte groupNum,
      byte classNum,
      byte timeSlice) {
    this.costFunctions = costFunctions;
    this.classNum = classNum;
    this.groupNum = groupNum;
    this.timeSlice = timeSlice;
    this.project = project;

    // Tell Parsii that it must be strict with unknown variable names
    scope.withStrictLookup(true);

    // Get the current scenario
    scenario = project.getLocalProperty(NodusC.PROP_SCENARIO, 0);

    // Get the nodes and link layers
    nodesEsriLayer = project.getNodeLayers();
    linksEsriLayer = project.getLinkLayers();
    previousLayerVariableName = nodesEsriLayer[0].getLayerVariableName();

    // Get the table models for nodes and links
    nodesDbf = new DbfTableModel[nodesEsriLayer.length];

    for (int i = 0; i < nodesEsriLayer.length; i++) {
      nodesDbf[i] = nodesEsriLayer[i].getModel();
    }

    linksDbf = new DbfTableModel[linksEsriLayer.length];

    for (int i = 0; i < linksEsriLayer.length; i++) {
      linksDbf[i] = linksEsriLayer[i].getModel();
    }

    /* Get the links graphic lists, because each link contains a RealLink object,
     * used to store/retrieve/update flows
     */
    links = new EsriGraphicList[linksEsriLayer.length];

    for (int i = 0; i < linksEsriLayer.length; i++) {
      links[i] = linksEsriLayer[i].getEsriGraphicList();
    }

    /* All the numeric fields in the DbfTableModels are potential variables.
     * Create tables with the names of these variables
     */
    nodeFieldName = new Vector[nodesDbf.length];

    for (int i = 0; i < nodesDbf.length; i++) {
      nodeFieldName[i] = new Vector<>(nodesDbf[i].getColumnCount());

      for (int j = 0; j < nodesDbf[i].getColumnCount(); j++) {
        if (nodesDbf[i].getType(j) == DbfTableModel.TYPE_NUMERIC) {
          nodeFieldName[i].add(j, nodesDbf[i].getColumnName(j).toUpperCase());
        } else {
          nodeFieldName[i].add(j, null);
        }
      }
    }

    linkFieldName = new Vector[linksDbf.length];

    for (int i = 0; i < linksDbf.length; i++) {
      linkFieldName[i] = new Vector<>(linksDbf[i].getColumnCount());

      for (int j = 0; j < linksDbf[i].getColumnCount(); j++) {
        if (linksDbf[i].getType(j) == DbfTableModel.TYPE_NUMERIC) {
          linkFieldName[i].add(j, linksDbf[i].getColumnName(j).toUpperCase());
        } else {
          linkFieldName[i].add(j, null);
        }
      }
    }

    // Initialize the variables that represent the layers
    for (NodusEsriLayer element : nodesEsriLayer) {
      setVariable(element.getLayerVariableName(), 0.0);
    }

    for (NodusEsriLayer element : linksEsriLayer) {
      setVariable(element.getLayerVariableName(), 0.0);
    }

    if (initialiseVariableForGroup(groupNum, classNum)) {
      initialized = true;
    }
  }

  /**
   * Returns the cost computed for a given virtual link.
   *
   * @param vl A virtual link.
   * @return The computed cost. This function also can return Parse_ERROR if and error occurred
   *     during parsing or UNDEFINED_FUNCTION if no cost function was defined for this virtual link.
   */
  public double compute(VirtualLink vl) {

    int type = vl.getType();

    // Load the layer variable name (set to 1 for current layer)
    String layerVariableName;

    if (type == VirtualLink.TYPE_MOVE) {
      layerVariableName = linksEsriLayer[vl.getLayerIndex()].getLayerVariableName();
    } else {
      layerVariableName = nodesEsriLayer[vl.getLayerIndex()].getLayerVariableName();
    }

    if (!layerVariableName.equals(previousLayerVariableName)) {
      setVariable(previousLayerVariableName, 0.0);
      setVariable(layerVariableName, 1.0);
      previousLayerVariableName = layerVariableName;
    }

    // Build cost function
    String costFunctionName = "";

    switch (type) {
      case VirtualLink.TYPE_LOAD:
        costFunctionName =
            "ld." + vl.getEndVirtualNode().getMode() + "," + vl.getEndVirtualNode().getMeans();

        break;

      case VirtualLink.TYPE_UNLOAD:
        costFunctionName =
            "ul." + vl.getBeginVirtualNode().getMode() + "," + vl.getBeginVirtualNode().getMeans();

        break;

      case VirtualLink.TYPE_MOVE:
        costFunctionName =
            "mv." + vl.getBeginVirtualNode().getMode() + "," + vl.getBeginVirtualNode().getMeans();

        break;

      case VirtualLink.TYPE_TRANSHIP:
        costFunctionName =
            "tp."
                + vl.getBeginVirtualNode().getMode()
                + ","
                + vl.getBeginVirtualNode().getMeans()
                + '-'
                + vl.getEndVirtualNode().getMode()
                + ","
                + vl.getEndVirtualNode().getMeans();

        break;

      case VirtualLink.TYPE_SWITCH:
        costFunctionName =
            "sw."
                + vl.getBeginVirtualNode().getMode()
                + ","
                + vl.getBeginVirtualNode().getMeans()
                + '-'
                + vl.getEndVirtualNode().getMode()
                + ","
                + vl.getEndVirtualNode().getMeans();
        break;

      case VirtualLink.TYPE_TRANSIT:
        costFunctionName =
            "tr." + vl.getBeginVirtualNode().getMode() + "," + vl.getBeginVirtualNode().getMeans();

        break;

      case VirtualLink.TYPE_STOP:
        costFunctionName =
            "stp." + vl.getBeginVirtualNode().getMode() + "," + vl.getBeginVirtualNode().getMeans();

        break;
      default:
        break;
    }

    String defaultFormula;

    // At least the generic function must exists
    // Test the existence of time specific cost function
    if (timeSlice != -1) {
      String s = "t" + timeSlice + "." + new String(costFunctionName);
      defaultFormula = (String) costFunctions.get(s);
      if (defaultFormula == null) {
        defaultFormula = (String) costFunctions.get(costFunctionName);
      }
    } else {
      defaultFormula = (String) costFunctions.get(costFunctionName);
    }

    if (defaultFormula == null) {
      return UNDEFINED_FUNCTION;
    }

    int layerIndex = vl.getLayerIndex();
    int indexInLayer = vl.getIndexInLayer();

    // Set shp/dbf related variables in parser
    Vector<?> fieldName = null;
    List<Object> values = null;

    // Try to minimize the number of variables to update in the parser
    boolean reloadVariables = false;

    // Up or Downstream ?
    setVariable(NodusC.VARNAME_UPSTREAM, vl.getUpStream());

    if (currentType != type) {
      reloadVariables = true;
      currentLink = vl.getBeginVirtualNode().getRealLinkId();
      currentNode = vl.getBeginVirtualNode().getRealNodeId(false);
      currentType = type;
    } else {
      if (type == VirtualLink.TYPE_MOVE) {
        if (vl.getBeginVirtualNode().getRealLinkId() != currentLink) {
          reloadVariables = true;
          currentLink = vl.getBeginVirtualNode().getRealLinkId();
        } else {
          if (vl.getBeginVirtualNode().getRealNodeId(false) != currentNode) {
            reloadVariables = true;
            currentNode = vl.getBeginVirtualNode().getRealNodeId(false);
          }
        }
      }
    }
    if (vl.getBeginVirtualNode().getService() != vl.getEndVirtualNode().getService()) {
      setVariable(
          NodusC.VARNAME_FREQUENCY,
          project.getServiceEditor().frequencyByService(vl.getEndVirtualNode().getService()));
    } else {
      setVariable(NodusC.VARNAME_FREQUENCY, 0);
    }

    if (reloadVariables) {
      if (type == VirtualLink.TYPE_MOVE) {
        fieldName = linkFieldName[layerIndex];
        values = linksDbf[layerIndex].getRecord(indexInLayer);

        // Get RealLink object
        OMGraphic omg = links[layerIndex].getOMGraphicAt(indexInLayer);
        RealLink rl = (RealLink) omg.getAttribute(0);

        // Get length and duration
        setVariable(NodusC.VARNAME_LENGTH, rl.getLength());
        setVariable(NodusC.VARNAME_DURATION, rl.getDuration());

        // Remove the tranship variable if exists
        if (!hasMoveVariables) {
          scope.remove(NodusC.VARNAME_TRANSHIP);
        }

        hasMoveVariables = true;

        /*
         * Get (oriented) flow for the real link associated to this virtual link.
         * The flow must here be computed as a number of vehicles
         */

        setVariable(NodusC.VARNAME_FLOW, rl.getCurrentStandardVehicles(vl));

      } else {
        fieldName = nodeFieldName[layerIndex];
        values = nodesDbf[layerIndex].getRecord(indexInLayer);

        // Get Tranship field
        setVariable(NodusC.VARNAME_TRANSHIP, (double) values.get(NodusC.DBF_IDX_TRANSHIP));

        // No flow or Length variable for nodes
        if (hasMoveVariables) {
          scope.remove(NodusC.VARNAME_FLOW);
          scope.remove(NodusC.VARNAME_LENGTH);
          hasMoveVariables = false;
        }
      }

      // dbf fields
      for (int i = 0; i < fieldName.size(); i++) {
        String name = (String) fieldName.get(i);

        if (name != null) {
          double d = JDBCUtils.getDouble(values.get(i));
          setVariable(name, d);
        }
      }
    } // if reloadVariables

    /* Cost functions can exist for a given scenario, group and class.
     * Test the different possibilities
     */
    String specificCostFunctionName = null;
    String costFunctionFormula = null;

    // A scenario, group and od class specific function?
    specificCostFunctionName = scenario + "." + costFunctionName + "." + groupNum + '-' + classNum;
    costFunctionFormula = (String) costFunctions.get(specificCostFunctionName);

    // A scenario and od class specific function?
    if (costFunctionFormula == null) {
      specificCostFunctionName = scenario + "." + costFunctionName + '-' + classNum;
      costFunctionFormula = (String) costFunctions.get(specificCostFunctionName);
    }

    // A scenario and group specific function?
    if (costFunctionFormula == null) {
      specificCostFunctionName = scenario + "." + costFunctionName + "." + groupNum;
      costFunctionFormula = (String) costFunctions.get(specificCostFunctionName);
    }

    // A scenario specific function?
    if (costFunctionFormula == null) {
      specificCostFunctionName = scenario + "." + costFunctionName;
      costFunctionFormula = (String) costFunctions.get(specificCostFunctionName);
    }

    // A group and od class specific function?
    if (costFunctionFormula == null) {
      specificCostFunctionName = costFunctionName + "." + groupNum + '-' + classNum;
      costFunctionFormula = (String) costFunctions.get(specificCostFunctionName);
    }

    // A od class specific function?
    if (costFunctionFormula == null) {
      specificCostFunctionName = costFunctionName + '-' + classNum;
      costFunctionFormula = (String) costFunctions.get(specificCostFunctionName);
    }

    // A group specific function?
    if (costFunctionFormula == null) {
      specificCostFunctionName = costFunctionName + "." + groupNum;
      costFunctionFormula = (String) costFunctions.get(specificCostFunctionName);
    }

    // No specific formula was found
    if (costFunctionFormula == null) {
      costFunctionFormula = defaultFormula;
    }

    if (costFunctionFormula.compareTo("null") == 0) {
      return UNDEFINED_FUNCTION;
    }

    // Now we have a cost function to parse
    try {
      expression = Parser.parse(costFunctionFormula, scope);
    } catch (ParseException e) {
      errorMessage = vl.toString() + ": " + costFunctionFormula + '\n' + e.toString();
      return PARSER_ERROR;
    }
    double cost = -1.0;
    cost = expression.evaluate();

    if (Double.isNaN(cost)) {
      errorMessage = vl.toString() + ": " + costFunctionFormula + " =  NaN\n";
      return PARSER_ERROR;
    }

    if (Double.isInfinite(cost)) {
      errorMessage = vl.toString() + ": " + costFunctionFormula + " =  Infinity\n";
      return PARSER_ERROR;
    }

    // The cost must be positive
    if (cost < 0) {
      errorMessage =
          costFunctionFormula
              + '\n'
              + i18n.get(CostParser.class, "Result_is_negative", "Result is negative");
      return PARSER_ERROR;
    }

    // A correct cost was computed
    return cost;
  }

  /**
   * Returns the error message, or null if no error.
   *
   * @return Error message
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * Reads the cost function files to initialize all the variables that are defined for a given
   * group. Returns false on error.
   *
   * @param groupNum Group for which the variables must be initialized
   * @param classNum Class for which the variables must be initialized
   * @return boolean True on success
   */
  private boolean initialiseVariableForGroup(byte groupNum, byte classNum) {

    // Initialize (or reset) the main variables
    Enumeration<?> enumerator = costFunctions.propertyNames();

    for (; enumerator.hasMoreElements(); ) {
      // Get property name
      String propName = (String) enumerator.nextElement();

      // Must be a variable
      // TODO Allow scenario/group/class specific variables without a need to define the generic one
      if (propName.indexOf(".") < 0 && propName.indexOf('-') < 0) {
        // Get property value
        String propValue = costFunctions.getProperty(propName);

        // Is this a reference to another variable (recursive approach)?
        boolean found = false;
        String initialVariableName = propValue;

        while (!found) {
          if (!StringUtils.isNumeric(propValue)) {
            propValue = costFunctions.getProperty(propValue);

            if (propValue == null) {
              errorMessage =
                  MessageFormat.format(
                      i18n.get(
                          CostParser.class,
                          "is_not_a_valid_number",
                          "\"{0}\" is not a valid number"),
                      initialVariableName);
              return false;
            }
          } else {
            found = true;
          }
        }

        // If we are here, we have a valid number
        try {
          setVariable(propName, Double.parseDouble(propValue));
        } catch (NumberFormatException e) {
          e.printStackTrace();
        }
      }
    }

    // Initialize the overridden variables
    Collection<Variable> variables = scope.getVariables();
    Iterator<Variable> it = variables.iterator();

    while (it.hasNext()) {
      String varName = it.next().getName();

      String propName = null;
      String propValue = null;

      // Is there a scenario, group and od class specific variable?
      if (classNum != -1) {
        propName = scenario + "." + varName + "." + groupNum + "-" + classNum;
        propValue = costFunctions.getProperty(propName);
      }

      // Is there a scenario and od class specific variable?
      if (propValue == null && classNum != -1) {
        propName = scenario + "." + varName + "-" + classNum;
        propValue = costFunctions.getProperty(propName);
      }

      // Is there a scenario and group specific variable?
      if (propValue == null) {
        propName = scenario + "." + varName + "." + groupNum;
        propValue = costFunctions.getProperty(propName);
      }

      // Is there a scenario specific variable?
      if (propValue == null) {
        propName = scenario + "." + varName;
        propValue = costFunctions.getProperty(propName);
      }

      // Is there a group and od class specific variable?
      if (propValue == null && classNum != -1) {
        propName = varName + "." + groupNum + "-" + classNum;
        propValue = costFunctions.getProperty(propName);
      }

      // Is there od class specific variable?
      if (propValue == null && classNum != -1) {
        propName = varName + "-" + classNum;
        propValue = costFunctions.getProperty(propName);
      }

      // Is there a group specific variable?
      if (propValue == null) {

        propName = varName + "." + groupNum;
        propValue = costFunctions.getProperty(propName);
      }

      if (propValue != null) {
        // Is this a reference to another variable (recursive approach)?
        boolean found = false;

        String initialVariableName = propValue;

        while (!found) {
          if (!StringUtils.isNumeric(propValue)) {
            propValue = costFunctions.getProperty(propValue);

            if (propValue == null) {
              errorMessage =
                  MessageFormat.format(
                      i18n.get(
                          CostParser.class,
                          "is_not_a_valid_number",
                          "\"{0}\" is not a valid number"),
                      initialVariableName);
              return false;
            }
          } else {
            found = true;
          }
        }

        try {
          setVariable(varName, Double.parseDouble(propValue));
        } catch (NumberFormatException e) {
          e.printStackTrace();
        }
      }
    }

    return true;
  }

  /**
   * Tests if the parser is initialized
   *
   * @return True if the parser is initialized.
   */
  public boolean isInitialized() {
    return initialized;
  }

  /** Create or update a variable in the parser. */
  private void setVariable(String name, boolean upStream) {
    double value = 0;
    if (upStream) {
      value = 1;
    }
    scope.create(name).setValue(value);
  }

  /** Create or update a variable in the parser. */
  private void setVariable(String name, double value) {
    scope.create(name).setValue(value);
  }
}
