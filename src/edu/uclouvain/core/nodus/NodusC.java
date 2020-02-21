/**
 * Copyright (c) 1991-2020 Université catholique de Louvain
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

package edu.uclouvain.core.nodus;

/**
 * This class contains all the constants defined in Nodus.
 *
 * @author Bart Jourquin
 */
public class NodusC {

  /*
   * *********************************************************************************************
   * Application name, version and copyright
   * *********************************************************************************************
   */

  /** Application version. */
  public static final String VERSION = "7.2";

  /** Application name. */
  public static final String APPNAME = "Nodus " + VERSION;

  /** Copyright. */
  public static final String COPYRIGHT = "(c) Université catholique de Louvain, 1991-2020";

  /*
   * *********************************************************************************************
   * Default values for various aspects of Nodus
   * *********************************************************************************************
   */

  /** Default latitude of the center of the MapBean. */
  public static final int MAPBEAN_CENTER_X = 15;

  /** Default longitude of the center of the MapBean. */
  public static final int MAPBEAN_CENTER_Y = -30;

  /** Default scale of the MapBean. */
  public static final int SCALE_FACTOR = 80000000;

  /** Max number of modes and means. */
  public static final byte MAXMM = 100;

  /** Max number of scenarios. */
  public static final int MAXSCENARIOS = 1000;

  /** Max number of services. */
  public static final short MAXSERVICE = 10000;

  /** Maximum radius that can be used to display nodes. Can be changed here only. */
  public static final int MAX_RADIUS = 50;

  /** Maximum width that can be used to draw links. Can be changed here only. */
  public static final int MAX_WIDTH = 10;

  /** Default width of the main Nodus window. */
  public static final int DEFAULT_SCREEN_WIDTH = 800;

  /** Quality ratio used for the generated JPEG images from displayed map. */
  public static final float JPEG_QUALITY = 0.8f;

  /** Maximum size of the SQL batch for the path writer. */
  public static final int MAXBATCHSIZE = 1000;
  /*
   * *********************************************************************************************
   * Indexes and names of database fields
   * *********************************************************************************************
   */

  /** Index of the "num" field in the dbf tables. */
  public static final int DBF_IDX_NUM = 0;

  /** Index of the "style" field in the dbf tables. */
  public static final int DBF_IDX_STYLE = 1;

  /** Index of the "tranship" field in the dbf tables. */
  public static final int DBF_IDX_TRANSHIP = 2;

  /** Index of the "enabled" field in the dbf tables. */
  public static final int DBF_IDX_ENABLED = 2;

  /** Index of the "node1" field in the dbf tables. */
  public static final int DBF_IDX_NODE1 = 3;

  /** Index of the "node2" field in the dbf tables. */
  public static final int DBF_IDX_NODE2 = 4;

  /** Index of the "mode" field in the dbf tables. */
  public static final int DBF_IDX_MODE = 5;

  /** Index of the "means" field in the dbf tables. */
  public static final int DBF_IDX_MEANS = 6;

  /** Index of the "capacity" field in the dbf tables. */
  public static final int DBF_IDX_CAPACITY = 7;

  /** Index of the "speed" field in the dbf tables. */
  public static final int DBF_IDX_SPEED = 8;

  /** Name of the "num" field in the database tables. */
  public static final String DBF_NUM = "num";

  /** Name of the "style" field in the database tables. */
  public static final String DBF_STYLE = "style";

  /** Name of the "tranship" field in the database tables. */
  public static final String DBF_TRANSHIP = "tranship";

  /** Name of the "mode" field in the database tables. */
  public static final String DBF_MODE = "mode";

  /** Name of the "means" field in the database tables. */
  public static final String DBF_MEANS = "means";

  /** Name of the "loading mode" field in the database tables. */
  public static final String DBF_LDMODE = "ldmode";

  /** Name of the "loading means" field in the database tables. */
  public static final String DBF_LDMEANS = "ldmeans";

  /** Name of the "unloading mode" field in the database tables. */
  public static final String DBF_ULMODE = "ulmode";

  /** Name of the "unloading means" field in the database tables. */
  public static final String DBF_ULMEANS = "ulmeans";

  /** Name of the "capacity" field in the database tables. */
  public static final String DBF_CAPACITY = "capacity";

  /** Name of the "speed" field in the database tables. */
  public static final String DBF_SPEED = "speed";

  /** Name of the "active" field in the database tables. */
  public static final String DBF_ENABLED = "enabled";

  /** Name of the "group" field in the database tables. */
  public static final String DBF_GROUP = "grp";

  /** Name of the "origin" field in the database tables. */
  public static final String DBF_ORIGIN = "org";

  /** Name of the "destination" field in database dbf tables. */
  public static final String DBF_DESTINATION = "dst";

  /** Name of the "quantity" field in the database tables. */
  public static final String DBF_QUANTITY = "qty";

  /** Name of the "unit cost" field in the database tables. */
  public static final String DBF_UNITCOST = "ucost";

  /** Name of the "vehicles" field in tha database tables. */
  public static final String DBF_VEHICLES = "veh";

  /** Name of the "vehicles" field in tha database tables. */
  public static final String DBF_RESULT = "result";

  /** Name of the "origin node" field in the database tables. */
  public static final String DBF_NODE1 = "node1";

  /** Name of the "origin link" field in the database tables. */
  public static final String DBF_LINK1 = "link1";

  /** Name of the "origin mode" field in the database tables. */
  public static final String DBF_MODE1 = "mode1";

  /** Name of the "origin means" field in the database tables. */
  public static final String DBF_MEANS1 = "means1";

  /** Name of the "destination node" field in the database tables. */
  public static final String DBF_NODE2 = "node2";

  /** Name of the "destination link" field in the database tables. */
  public static final String DBF_LINK2 = "link2";

  /** Name of the "destination mode" field in the database tables. */
  public static final String DBF_MODE2 = "mode2";

  /** Name of the "origin service" field in the database tables. */
  public static final String DBF_SERVICE1 = "service1";

  /** Name of the "destination service" field in the database tables. */
  public static final String DBF_SERVICE2 = "service2";

  /** Name of the "destination means" field in the database tables. */
  public static final String DBF_MEANS2 = "means2";

  /** Name of the "path index" field in the database tables. */
  public static final String DBF_PATH_INDEX = "pathidx";

  /** Name of the "service index" field in the database tables. */
  public static final String DBF_SERVICE_INDEX = "serviceidx";

  /** Name of the "nbtrans" field in the database tables. */
  public static final String DBF_NBTRANS = "nbtrans";

  /** Name of the "link" field in the database tables. */
  public static final String DBF_LINK = "link";

  /** Name of the "service" field in the database tables. */
  public static final String DBF_SERVICE = "service";

  /** Name of the "frequency" field in the database tables. */
  public static final String DBF_FREQUENCY = "frequency";

  /** Name of the "type" field in the database tables. */
  public static final String DBF_TYPE = "type";

  /** Name of the "stop" field in the database tables. */
  public static final String DBF_STOP = "stop";

  /** Name of the "iteration" field in the database tables. */
  public static final String DBF_ITERATION = "iteration";

  /** Name of the "length" field in the database tables. */
  public static final String DBF_LENGTH = "length";

  /** Name of the "duration" field in the database tables. */
  public static final String DBF_DURATION = "duration";

  /** Name of the "length" field in the database tables. */
  public static final String DBF_CLASS = "class";

  /** Name of the "time" field in the database tables. */
  public static final String DBF_TIME = "time";

  /** Name of the loading cost field in the database tables. */
  public static final String DBF_LDCOST = "ldcost";

  /** Name of the unloading cost field in the database tables. */
  public static final String DBF_ULCOST = "ulcost";

  /** Name of the transit cost field in the database tables. */
  public static final String DBF_TRCOST = "trcost";

  /** Name of the transhipment cost field in the database tables. */
  public static final String DBF_TPCOST = "tpcost";

  /** Name of the moving cost field in the database tables. */
  public static final String DBF_MVCOST = "mvcost";

  /*
   * *********************************************************************************************
   * Mandatory fields for Node and Link layers
   * *********************************************************************************************
   */

  /** Names of the mandatory fields in the node layers. */
  public static final String[] NODES_MANDATORY_NAMES = {DBF_NUM, DBF_STYLE, DBF_TRANSHIP};

  /** Mandatory field types in the node layer. */
  public static final char[] NODES_MANDATORY_TYPES = {'N', 'N', 'N'};

  /** Mandatory field lengths in the node layers. */
  public static final int[] NODES_MANDATORY_LENGTHS = {10, 2, 1};

  /** Mandatory field precisions in the node layers. */
  public static final int[] NODES_MANDATORY_DECIMAL_COUNTS = {0, 0, 0};

  /** Names of the mandatory fields in the link layers. */
  public static final String[] LINKS_MANDATORY_NAMES = {
    DBF_NUM,
    DBF_STYLE,
    DBF_ENABLED,
    DBF_NODE1,
    DBF_NODE2,
    DBF_MODE,
    DBF_MEANS,
    DBF_CAPACITY,
    DBF_SPEED
  };

  /** Mandatory field types in the link layer. */
  public static final char[] LINKS_MANDATORY_TYPES = {'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N'};

  /** Mandatory field lengths in the link layer. */
  public static final int[] LINKS_MANDATORY_LENGTHS = {10, 2, 1, 10, 10, 2, 2, 10, 7};

  /** Mandatory field precisions in the link layer. */
  public static final int[] LINKS_MANDATORY_DECIMAL_COUNTS = {0, 0, 0, 0, 0, 0, 0, 0, 2};

  /*
   * *********************************************************************************************
   * Types of handling operations
   * *********************************************************************************************
   */

  /** No handling is possible at the node. */
  public static final byte HANDLING_NONE = 0;

  /** Loading, unloading and transhipment are possible at the node. */
  public static final byte HANDLING_ALL = 1;

  /** Transhipment is possible at the node, but not loading or unloading. */
  public static final byte HANDLING_TRANSHIP = 2;

  /** Loading and unloading are possible at the node, but not transhipment. */
  public static final byte HANDLING_LOAD_UNLOAD = 3;

  /** Transfer to another service is possible at the node. */
  public static final byte HANDLING_CHANGE_SERVICE = 4;

  /*
   * *********************************************************************************************
   * Variable names that are understood by Nodus
   * *********************************************************************************************
   */

  /**
   * Name of the variable that must be used in cost functions to use the current flow on a link.
   * Used in flow related cost functions (Equilibrium assignment techniques).
   */
  public static final String VARNAME_FLOW = "FLOW";

  /**
   * Name of the variable that must be used in cost functions to use the value of the tranship field
   * of a node.
   */
  public static final String VARNAME_TRANSHIP = "TRANSHIP";

  /** Name of the variable that must be used in cost functions to use the length on a link. */
  public static final String VARNAME_LENGTH = "LENGTH";

  /**
   * Name of the variable that must be used in cost functions to use the duration (in seconds) on a
   * link.
   */
  public static final String VARNAME_DURATION = "DURATION";

  /** Name of the variable that must be used in cost functions to use the frequency of a service. */
  public static final String VARNAME_FREQUENCY = "FREQUENCY";

  /** Name of the variable that must be used in cost functions to use the speed on a link. */
  public static final String VARNAME_SPEED = "SPEED";

  /** Name of the variable that must be used in cost functions to use the capacity on a link. */
  public static final String VARNAME_CAPACITY = "CAPACITY";

  /**
   * Name of the variable that must be used in cost functions to use the average load of a vehicle.
   */
  public static final String VARNAME_AVERAGELOAD = "AVGLOAD";

  /**
   * Name of the variable that must be used in cost functions to store the loading duration of a
   * vehicle.
   */
  public static final String VARNAME_LOADING_DURATION = "LD_DURATION";

  /**
   * Name of the variable that must be used in cost functions to store the unloading duration of a
   * vehicle.
   */
  public static final String VARNAME_UNLOADING_DURATION = "UL_DURATION";

  /**
   * Name of the variable that must be used in cost functions to store the transhipment duration
   * between vehicles.
   */
  public static final String VARNAME_TRANSHIP_DURATION = "TP_DURATION";

  /**
   * Name of the variable that must be used in cost functions to differentiate Up and DownStream
   * flows.
   */
  public static final String VARNAME_UPSTREAM = "UPSTREAM";

  /**
   * Name of the variable that must be used in cost functions to generate service lines for a
   * mode/means combination.
   */
  public static final String VARNAME_SERVICELINES = "SERVICELINES";

  /**
   * Name of the variable that must be used in cost functions to define the exponent used in the
   * Abraham model used in multi-flow assignments.
   */
  public static final String VARNAME_ABRAHAM = "ABRAHAM";

  /**
   * Name of the variable that must be used in cost functions to use the equivalent standard vehicle
   * ratio to be used for a vehicle.
   */
  public static final String VARNAME_ESV = "ESV";

  /**
   * Name of the variable that specifies the start time of a time dependent assignments, expressed
   * in minutes after midnight.
   */
  public static final String VARNAME_STARTTIME = "STARTTIME";

  /**
   * Name of the variable that specifies the end time of a time dependent assignments, expressed in
   * minutes after midnight.
   */
  public static final String VARNAME_ENDTIME = "ENDTIME";

  /**
   * Name of the variable that specifies the duration of a time window (time slice) for a time
   * dependent assignments, expressed in minutes.
   */
  public static final String VARNAME_TIMESLICE = "TIMESLICE";

  /*
   * *********************************************************************************************
   * File extensions
   * *********************************************************************************************
   */

  /** Extension used for shape files. */
  public static final String TYPE_SHP = ".shp";

  /** Extension used for shape index files. */
  public static final String TYPE_SHX = ".shx";

  /** Extension used for shape dbf files. */
  public static final String TYPE_DBF = ".dbf";

  /** Extension used for cost functions files. */
  public static final String TYPE_COSTS = ".costs";

  /** Extension used for csv files. */
  public static final String TYPE_CSV = ".csv";

  /** Extension used for xls files. */
  public static final String TYPE_XLS = ".xls";

  /** Extension used for xlsx files. */
  public static final String TYPE_XLSX = ".xlsx";

  /** Extension used for SQL query files. */
  public static final String TYPE_SQL = ".sql";

  /** Extension used for text files. */
  public static final String TYPE_TXT = ".txt";

  /** Extension used for Nodus project properties files. */
  public static final String TYPE_NODUS = ".nodus";

  /** Extension used for lock files. */
  public static final String TYPE_LOCK = ".lck";

  /** Extension used for Nodus local properties files. */
  public static final String TYPE_LOCAL = ".local";

  /** Extension used for Openmap additional layers. */
  public static final String TYPE_OPENMAP = ".openmap";

  /** Extension used for Groovy files. */
  public static final String TYPE_GROOVY = ".groovy";

  /*
   * *********************************************************************************************
   * Database table suffixes
   * *********************************************************************************************
   */

  /** Default table name extension used for exclusions. */
  public static final String SUFFIX_EXC = "_exc";

  /** Default table name extension used for the virtual network. */
  public static final String SUFFIX_VNET = "_vnet";

  /** Default table name extension used for paths. */
  public static final String SUFFIX_PATH = "_path";

  /** Default table name extension used for services. */
  public static final String SUFFIX_SERVICE = "_service";

  /** Default table name extension used for results output files. */
  public static final String SUFFIX_RESULTS = "_results";

  /** Default table name extension used for path headers. */
  public static final String SUFFIX_HEADER = "_header";

  /** Default table name extension used for path details. */
  public static final String SUFFIX_DETAIL = "_detail";

  /** Default table name extension used for path details. */
  public static final String SUFFIX_LINK_DETAIL = "_link_detail";

  /** Default table name extension used for path details. */
  public static final String SUFFIX_STOP_DETAIL = "_stop_detail";

  /** Default table name extension used for O-D matrixes. */
  public static final String SUFFIX_OD = "_od";

  /*
   * *********************************************************************************************
   * Properties keys that can be used in a Nodus project file
   * *********************************************************************************************
   */

  /** List of (space separated) Nodus compatible Node layers. This property is mandatory. */
  public static final String PROP_NETWORK_NODES = "network.nodes";

  /** List of (space separated) Nodus compatible Link layers. This property is mandatory. */
  public static final String PROP_NETWORK_LINKS = "network.links";

  /** Pretty name used to identify a layer in the GUI. */
  public static final String PROP_PRETTY_NAME = ".prettyName";
  
  /** Max batch size for SQL batches. */
  public static final String PROP_MAX_SQL_BATCH_SIZE = "maxSqlBatchSize";

  /**
   * List of (space separated) DBF tables that must be imported with the project (OD tables for
   * instance).
   */
  public static final String PROP_IMPORT_TABLES = "importTables";

  /** Name of the file that contains the settings of additional OpenMap layers. */
  public static final String PROP_OPENMAP_LAYERS = "openmap.layers";

  /** The name of the variable that is associated to a layer. */
  public static final String PROP_VARIABLE_NAME = ".variableName";

  /** Username to pass to the JDBC driver of the chosen external DBMS. */
  public static final String PROP_JDBC_USERNAME = "jdbc.user";

  /** Password to pass to the JDBC driver of the chosen external DBMS. */
  public static final String PROP_JDBC_PASSWORD = "jdbc.password";

  /** java class name of the JDBC driver of the chosen external DBMS. */
  public static final String PROP_JDBC_DRIVER = "jdbc.driver";

  /** URL connection string to pass to the JDBC driver of the chosen external DBMS. */
  public static final String PROP_JDBC_URL = "jdbc.url";

  /*
   * *********************************************************************************************
   * The following properties keys are used in the local properties only, and are therefore not
   * documented by JavaDoc
   * *********************************************************************************************
   */

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_EMBEDDED_DB = "embedded_db";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_VIRTUAL_NETWORK_VERSION = "virtual_network_version";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_NAME = ".name";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_LOCATION_FIELD_INDEX = ".locationFieldIndex";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_LOCATION_WHERESTMT = ".locationWhereStmt";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_VISIBLE = ".visible";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_WHERESTMT = ".whereStmt";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_SHOW_NAMES = ".showNames";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_SHOW_LOCATIONS = ".showLocations";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_DBF_TOOLTIPS = ".displayDbfToolTips";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_RENDER_STYLES = ".renderStyles";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_FONT = ".font";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_DOTCOLOR = ".color";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_COLOR = "color";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_AVAILABLE_LAYERS = ".availableLayers";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_DOTLAYERS = ".layers";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_EXC_TABLE = "exctable";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_OD_TABLE = "odtable";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_PATH_TABLE_PREFIX = "pathtableprefix";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_SERVICE_TABLE_PREFIX = "servicetableprefix";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_COST_FUNCTIONS = "costfunctions";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_SAVE_ALL_VN = "vn.saveall";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_ASSIGNMENT_DESCRIPTION = "assignmentdescription";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_VNET_TABLE = "vnettable";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_PROJECT_DOTPATH = "project.path";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_PROJECT_DOTNAME = "project.name";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_PROJECT_CANONICAL_NAME = "project.canonical.name";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_SCENARIO = "scenario";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_DOTLASTMODIFIED = ".lastmodified";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_LAST_PATH = "lastpath";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_LAST_PROJECT = "lastproject";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_FRAME_WIDTH = "frame.width";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_FRAME_HEIGTH = "frame.height";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_FRAME_X = "frame.x";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_GC_INTERVAL = "gc.interval";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_SUBFRAMES_ALWAYS_ON_TOP = "subframes.alwaysontop";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_NAV_MOUSE_MODE = "navmousemode";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_REOPEN_LATST_PROJECT = "reloadlastproject";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_STICKY_DRAWING_TOOL = "stickydrawingtool";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_MAX_SQL_ROWS = "maxsqlrows";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_DISPLAY_FULL_PATH = "displayfullpath";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_FRAME_Y = "frame.y";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_OVERVIEW_DATA = "overviewdata";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_ADD_POLITICAL_BOUNDARIES = "addPoliticalBoundaries";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_SHUTDOWN_COMPACT = "shutdownCompact";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_DISPLAY_POLITICAL_BOUNDARIES = "displayPoliticalBoundaries";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_ADD_HIGHLIGHTED_AREA = "addHighlightedArea";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_DISPLAY_HIGHLIGHTED_AREA = "displayHighlightedArea";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_OPENMAP_STARTUPLAYERS = "openmap.startUpLayers";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_MAP_BACKGROUNDCOLOR = "map.backgroundcolor";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_MAP_SCALE = "map.scale";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_MAP_ORDER = "map.order";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_MAP_LATITUDE = "map.latitude";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_MAP_LONGITUDE = "map.longitude";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_LOOK_AND_FEEL = "look&feel";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_SOUND = "sound";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_USE_SYSTEM_BROWSER = "use.external.browser";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_USE_GROOVY_CONSOLE = "use.groovyconsole";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_THREADS = "threads";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_LOCALE = "locale";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_ASSIGNMENT_TAB = "assignmenttab";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_ASSIGNMENT_METHOD = "assignmentmethod";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_ASSIGNMENT_NB_ITERATIONS = "assignmentnbiterations";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_ASSIGNMENT_PRECISION = "assignmentprecision";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static String PROP_ASSIGNMENT_SAVE_PATHS = "assignmentsavepaths";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static String PROP_ASSIGNMENT_SAVE_DETAILED_PATHS = "assignmentsavedetailedpaths";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static String PROP_ASSIGNMENT_RUN_POST_ASSIGNMENT_SCRIPT = "runpostassignmentscript";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static String PROP_ASSIGNMENT_LOG_LOST_PATHS = "loglostpaths";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static String PROP_ASSIGNMENT_MODAL_SPLIT_METHOD = "modalsplitmethod";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static String PROP_ASSIGNMENT_LIMIT_TO_HIGHLIGHTED_AREA = "limittohighlightedarea";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static String PROP_ASSIGNMENT_POST_ASSIGNMENT_SCRIPT = "postassignmentscript";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_MAX_RADIUS = "maxradius";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_MAX_WIDTH = "maxwidth";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_ASSIGNMENT_QUERY = "assignmentquery";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_NODES_FLOW_QUERY = "nodesflowquery";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_LINKS_FLOW_QUERY = "linksflowquery";

  
  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_DYNAMIC_FLOW_QUERY = "dynamicflowquery";

  
  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_LINKS_VEHICLES_QUERY = "linksvehiclesquery";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_PATH_QUERY = "pathquery";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_PROJECTION = "projection";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_COST_MARKUP = "costmarkup";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_MAX_DETOUR = "maxdetour";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_KEEP_CHEAPEST_INTERMODAL_PATH_ONLY = "keepcheapestonly";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_HIGHLIGHTED_AREA_COORDINATES = "highlightedareacoordinates";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_ACTIVE_MOUSE_MODE = "activemousemode";

  /**
   * Properties strings used in project and/or project local properties.
   *
   * @exclude
   */
  public static final String PROP_RENDERING_SCALE_THRESHOLD = "renderingscalethreshold";
}
