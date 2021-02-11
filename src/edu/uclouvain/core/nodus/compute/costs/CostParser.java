/*
 * Copyright (c) 1991-2021 Université catholique de Louvain
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
 * not, see http://www.gnu.org/licenses/.
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
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Vector;
import parsii.eval.Expression;
import parsii.eval.Function;
import parsii.eval.Parser;
import parsii.eval.Scope;
import parsii.tokenizer.ParseException;

// TODO (services) Add the cost functions for stops and switch in example.
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
 * # Personal Car Units ratios (can also be overridden for scenarios/groups <br>
 * PCU.2,1 = 1 <br>
 * PCU.4,1 = 2 <br>
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
 * # time/duration functions can also be defined using the same schema <br>
 * # These functions are not mandatory, and, if used, they must not be defined <br>
 * # for all the types of movements. <br>
 * # A time/duration function uses the '@' separator instead of a dot. <br>
 * # Example : 'ld.4,1=' is a loading cost function, while 'ld@4,1=' is a loading <br>
 * # duration function. <br>
 * # The computed values are stored in the PATHxx_HEADER tables, meaning that <br>
 * # 'save paths' must be checked for the assignment. <br>
 * # <br>
 * # For dynamic time dependent assignments, it is also possible to define specific <br>
 * # functions for each time slice. Therefore, 'txx.', 'x' being the time slice must be added <br>
 * # before de regular function. <br>
 * # Example = 't0.ld.4,1=' is a loading cost function for time slice 0. <br>
 * <br>
 * # Since Nodus 8 it is also possible to set non numeric variables, which names must start with a
 * '@'. <br>
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
    value =
        PropUtils.doubleFromProperties(
            costFunctions, scenario + "." + varName + "." + group + "-" + odClass, Double.NaN);
    if (!Double.isNaN(value)) {
      return value;
    }

    // Is there a specific value for this scenario and group?
    value =
        PropUtils.doubleFromProperties(
            costFunctions, scenario + "." + varName + "." + group, Double.NaN);
    if (!Double.isNaN(value)) {
      return value;
    }

    // Is there a specific value for this scenario and od class?
    value =
        PropUtils.doubleFromProperties(
            costFunctions, scenario + "." + varName + "-" + odClass, Double.NaN);
    if (!Double.isNaN(value)) {
      return value;
    }

    // Is there a specific value for this scenario?
    value = PropUtils.doubleFromProperties(costFunctions, scenario + "." + varName, Double.NaN);
    if (!Double.isNaN(value)) {
      return value;
    }

    // Is there a specific value for this group and od class?
    value =
        PropUtils.doubleFromProperties(
            costFunctions, varName + "." + group + "-" + odClass, Double.NaN);
    if (!Double.isNaN(value)) {
      return value;
    }

    // Is there a specific value for this od class?
    value = PropUtils.doubleFromProperties(costFunctions, varName + "-" + odClass, Double.NaN);
    if (!Double.isNaN(value)) {
      return value;
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
  private byte scenario;

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
      byte scenario,
      byte groupNum,
      byte classNum,
      byte timeSlice) {
    this.costFunctions = costFunctions;
    this.scenario = scenario;
    this.classNum = classNum;
    this.groupNum = groupNum;
    this.timeSlice = timeSlice;
    this.project = project;

    // Tell Parsii that it must be strict with unknown variable names
    scope.withStrictLookup(true);

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

    if (initialiseVariables()) {
      initialized = true;
    }

    registerFunctions();
  }

  /** Some user defined functions, such as volume-delay functions. */
  private void registerFunctions() {

    // Average of a series of numeric value
    // Example call ; AVG(1,3,8), that returns 4
    Parser.registerFunction(
        "AVG",
        new Function() {
          @Override
          public int getNumberOfArguments() {
            return -1;
          }

          @Override
          public double eval(List<Expression> args) {
            double avg = 0;
            if (args.isEmpty()) {
              return avg;
            }
            for (Expression e : args) {
              avg += e.evaluate();
            }
            return avg / args.size();
          }

          @Override
          public boolean isNaturalFunction() {
            return true;
          }
        });

    // BPR Volume delay function : 1 + beta * (VOLUME / CAPACITY)^alpha
    // Call BPR(VOLUME, CAPACITY, alpha, beta)
    Parser.registerFunction(
        "BPR",
        new Function() {
          @Override
          public int getNumberOfArguments() {
            return 4;
          }

          @Override
          public double eval(List<Expression> args) {

            double volume = args.get(0).evaluate();
            double capacity = args.get(1).evaluate();
            double alpha = args.get(2).evaluate();
            double beta = args.get(3).evaluate();

            double delay = 1 + beta * Math.pow(volume / capacity, alpha);
            return delay;
          }

          @Override
          public boolean isNaturalFunction() {
            return true;
          }
        });

    // Conical (Spiess) Volume delay function :
    // 2 + sqrt(alpha^2 * (1-(VOLUME/CAPACITY))^2 + beta^2) - alpha*(1-(VOLUME/CAPACITY)) - beta)
    // with beta = (2*alpha-1)/(2*alpha-2)
    // Call CONICAL(VOLUME, CAPACITY, alpha)
    Parser.registerFunction(
        "CONICAL",
        new Function() {
          @Override
          public int getNumberOfArguments() {
            return 3;
          }

          @Override
          public double eval(List<Expression> args) {

            double volume = args.get(0).evaluate();
            double capacity = args.get(1).evaluate();
            double alpha = args.get(2).evaluate();
            double beta = (2 * alpha - 1) / (2 * alpha - 2);
            double x = volume / capacity;

            double delay =
                2
                    + Math.sqrt(Math.pow(alpha, 2) * Math.pow(1 - x, 2) + Math.pow(beta, 2))
                    - alpha * (1 - x)
                    - beta;
            return delay;
          }

          @Override
          public boolean isNaturalFunction() {
            return true;
          }
        });
  }

  /**
   * Returns the cost computed for a given virtual link.
   *
   * @param vl A virtual link.
   * @param computeDuration If true, compute transit time, else compute cost.
   * @return The computed cost. This function also can return Parse_ERROR if and error occurred
   *     during parsing or UNDEFINED_FUNCTION if no cost function was defined for this virtual link.
   */
  public double compute(VirtualLink vl, boolean computeDuration) {

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

    char separator = '.';
    if (computeDuration) {
      separator = '@';
    }

    // Build cost function
    String costFunctionName = "";

    switch (type) {
      case VirtualLink.TYPE_LOAD:
        costFunctionName =
            "ld"
                + separator
                + vl.getEndVirtualNode().getMode()
                + ","
                + vl.getEndVirtualNode().getMeans();

        break;

      case VirtualLink.TYPE_UNLOAD:
        costFunctionName =
            "ul"
                + separator
                + vl.getBeginVirtualNode().getMode()
                + ","
                + vl.getBeginVirtualNode().getMeans();

        break;

      case VirtualLink.TYPE_MOVE:
        costFunctionName =
            "mv"
                + separator
                + vl.getBeginVirtualNode().getMode()
                + ","
                + vl.getBeginVirtualNode().getMeans();

        break;

      case VirtualLink.TYPE_TRANSHIP:
        costFunctionName =
            "tp"
                + separator
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
            "sw"
                + separator
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
            "tr"
                + separator
                + vl.getBeginVirtualNode().getMode()
                + ","
                + vl.getBeginVirtualNode().getMeans();

        break;

      case VirtualLink.TYPE_STOP:
        costFunctionName =
            "stp"
                + separator
                + vl.getBeginVirtualNode().getMode()
                + ","
                + vl.getBeginVirtualNode().getMeans();

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

        // Get length
        setVariable(NodusC.VARNAME_LENGTH, rl.getLength());
        // setVariable(NodusC.VARNAME_DURATION, rl.getDuration());

        // Remove the tranship variable if exists
        if (!hasMoveVariables) {
          scope.remove(NodusC.VARNAME_TRANSHIP);
        }

        hasMoveVariables = true;

        /*
         * Get (oriented) flow for the real link associated to this virtual link.
         * The flow is a number of passenger car units
         */
        setVariable(NodusC.VARNAME_VOLUME, rl.getCurrentPassengerCarUnits(vl));

      } else {
        fieldName = nodeFieldName[layerIndex];
        values = nodesDbf[layerIndex].getRecord(indexInLayer);

        // Get Tranship field
        setVariable(NodusC.VARNAME_TRANSHIP, (double) values.get(NodusC.DBF_IDX_TRANSHIP));

        // No flow or Length variable for nodes
        if (hasMoveVariables) {
          scope.remove(NodusC.VARNAME_VOLUME);
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
    double value = -1.0;
    value = expression.evaluate();

    if (Double.isNaN(value)) {
      errorMessage = vl.toString() + ": " + costFunctionFormula + " =  NaN\n";
      return PARSER_ERROR;
    }

    if (Double.isInfinite(value)) {
      errorMessage = vl.toString() + ": " + costFunctionFormula + " =  Infinity\n";
      return PARSER_ERROR;
    }

    // The cost must be positive
    if (value < 0) {
      errorMessage =
          costFunctionFormula
              + '\n'
              + i18n.get(CostParser.class, "Result_is_negative", "Result is negative");
      return PARSER_ERROR;
    }

    // A correct cost or duration was computed
    return value;
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
   * Looks for a specific scenario, group and/or class value of a variable.
   *
   * @return The "most specific" vaule for a variable.
   */
  private String getMostSpecificValue(String varName) {
    String propName;
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

    // Get the generic value of the variable
    if (propValue == null) {
      propName = varName;
      propValue = costFunctions.getProperty(propName);
    }

    return propValue;
  }

  /**
   * Returns the variable name of a property without its group, class or scenario attributes.
   *
   * @param propertyName The name stored in the costs file.
   * @return The variable name without its attributes or null if something was wrong.
   */
  private String getVarName(String propertyName) {

    // Split into tokens that can be separated by "-" or "." delimiters
    String[] tokens = propertyName.split("[-.]");

    // The token that doesn't represent an integer (scenario, class or group) is the variable name
    for (int i = 0; i < tokens.length; i++) {
      try {
        Integer.parseInt(tokens[i]);
      } catch (NumberFormatException e) {
        return tokens[i];
      }
    }

    // The costs functions file may contain other entries that are not recognized as valid
    // variables.
    return null;
  }

  /**
   * Reads the cost function files to initialize all the variables. Returns false on error.
   *
   * @return boolean True on success
   */
  private boolean initialiseVariables() {

    HashMap<String, String> hm = new HashMap<String, String>();

    Enumeration<?> enumerator = costFunctions.propertyNames();

    // Browse through the costs and identify all the variables
    for (; enumerator.hasMoreElements(); ) {
      // Get property name
      String propName = (String) enumerator.nextElement();

      // Non numeric variables can be set with a '@' prefix
      if (propName.startsWith("@")) {
        continue;
      }

      // Cost functions are not parsed now
      if (isCostFunction(propName)) {
        continue;
      }

      // Get the name of the variable without attributes
      String varName = getVarName(propName);

      // Is this variable already set ?
      if (hm.get(varName) != null) {
        continue;
      }

      // Get specific value for this variable, if any
      String value = getMostSpecificValue(varName);

      // Store this variable if valid
      if (value != null) {
        hm.put(varName, value);
      }
    }

    // At this point, all the variables are identified, but their value may reference another
    // variable. Try to parse all the variables to valid double values.
    boolean foundAValidValue = true;
    Expression expression;
    while (foundAValidValue) {

      // Any variable to parse ?
      if (hm.isEmpty()) {
        break;
      }

      foundAValidValue = false;

      Iterator<Entry<String, String>> it = hm.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<String, String> pair = (Map.Entry<String, String>) it.next();

        // Try to parse the value
        try {
          expression = Parser.parse(pair.getValue(), scope);
        } catch (ParseException e) {
          // This value probably makes reference to another variable

          continue;
        }

        // Store the variable if it could be parsed
        setVariable((String) pair.getKey(), expression.evaluate());

        // Remove this variable from the hashmap as it is resolved
        it.remove();

        // A next iteration may be needed
        foundAValidValue = true;
      }
    }

    // If at least one variable could not be parsed...
    if (!hm.isEmpty()) {
      Iterator<Entry<String, String>> it = hm.entrySet().iterator();
      Map.Entry<String, String> pair = (Map.Entry<String, String>) it.next();

      errorMessage =
          MessageFormat.format(
              i18n.get(CostParser.class, "is_not_a_valid_number", "\"{0}\" cannot be parsed"),
              pair.getKey() + " = " + pair.getValue());
      return false;
    }

    return true;
  }

  /**
   * Tests if a property name is a cost or transit time function.
   *
   * @param propertyName Property name.
   * @return True if the property name is a cost function.
   */
  private boolean isCostFunction(String propertyName) {
    // Costs
    if (propertyName.startsWith("ld.")
        || propertyName.startsWith("ul.")
        || propertyName.startsWith("tr.")
        || propertyName.startsWith("tp.")
        || propertyName.startsWith("mv.")
        || propertyName.startsWith("sw.")
        || propertyName.startsWith("stp.")) {
      return true;
    }

    // Transit times
    if (propertyName.startsWith("ld@")
        || propertyName.startsWith("ul@")
        || propertyName.startsWith("tr@")
        || propertyName.startsWith("tp@")
        || propertyName.startsWith("mv@")
        || propertyName.startsWith("sw@")
        || propertyName.startsWith("stp@")) {
      return true;
    }

    return false;
  }

  /**
   * Tests if the parser is initialized.
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
