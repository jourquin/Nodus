/*
 * Copyright (c) 1991-2023 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.compute.virtual;

import com.bbn.openmap.omGraphics.OMPoint;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.rules.NodeRule;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

/**
 * List of virtual nodes associates to an entry of the virtual network structure.
 *
 * @author Bart Jourquin
 */
public class VirtualNodeList {

  /** Contains the demands for a given loading virtual node. */
  private class Demands {
    public HashMap<Integer, LinkedList<ODCell>> destinations = new HashMap<>();
    public int loadingVirtualNodeId = -1;
    public int unloadingVirtualNodeId = -1;
  }

  private class GroupExclusions {
    int group;
    LinkedList<NodeRule> exclusions;

    public GroupExclusions(int group) {
      this.group = group;
      exclusions = new LinkedList<NodeRule>();
    }
  }

  /*private class ScenarioExclusions {
    int scenario;
    LinkedList<GroupExclusions> groupExclusions;

    public ScenarioExclusions(int scenario) {
      this.scenario = scenario;
      groupExclusions = new LinkedList<GroupExclusions>();
    }
  }*/

  /** Virtual node number that represent the loading node. */
  private Vector<Demands> demands = null;

  /** List of prohibited movements at the associated real node. */
  private LinkedList<GroupExclusions>[] scenarioExclusions = null;

  /** OMGraphic that represents this node. */
  private OMPoint graphic;

  /** True if the associated real node is a possible Change Line node. */
  private boolean isChangingService;

  /** True if the associated real node is a possible loading/unloading node. */
  private boolean isLoadingUnloading;

  /** True if the associated real node is a possible transhipment node. */
  private boolean isTranshipment;

  /** True if transit operations are allowed. */
  private boolean allowTransit;

  /** Real node number associated to this virtual node list. */
  private int realNodeId;

  /** List of virtual nodes generated from the associated real node. */
  private LinkedList<VirtualNode> virtualNodeList;

  /**
   * Creates a new virtual node list for a given real node.
   *
   * @param realNodeId The ID of the real node this list refers to.
   * @param handling The type of operation(weights) that are possible at this real node. Can be
   *     HANDLING_ALL, HANDLING_TRANSHIP, HANDLING_LOAD_UNLOAD or HANDLING_SERVICE.
   * @param graphic The OMPoint that represents the real node associated to this list.
   */
  public VirtualNodeList(int realNodeId, int handling, OMPoint graphic) {
    this.realNodeId = realNodeId;

    // Handling operation with an ID >= 5 don't allow transit virtual links
    allowTransit = true;
    if (handling > 4) {
      handling -= 5;
      allowTransit = false;
    }

    if (handling == NodusC.HANDLING_NONE) {
      isTranshipment = isLoadingUnloading = false;
    } else {
      if (handling == NodusC.HANDLING_ALL || handling == NodusC.HANDLING_TRANSHIP) {
        isTranshipment = true;
      }
      if (handling == NodusC.HANDLING_ALL || handling == NodusC.HANDLING_LOAD_UNLOAD) {
        isLoadingUnloading = true;
      }

      if (handling == NodusC.HANDLING_ALL || handling == NodusC.SERVICE_CHANGE) {
        isChangingService = true;
      }
    }
    this.graphic = graphic;
    virtualNodeList = new LinkedList<>();

    if (isLoadingUnloading) {
      demands = new Vector<>(1, 1);
      demands.add(new Demands());
      // allExclusions = new ScenarioExclusions;
      // } else {
      // if (isTranshipment) {
      //  allExclusions = new LinkedList<ScenarioExclusions>();
      // }
    }
  }

  /**
   * Stores a demand in the relevant demand list, designated by its index. Called by addDemand(...)
   * and relocateDemand(...).
   */
  private void storeDemand(int listIndex, ODCell odCell) {

    // Is there already a demand for this destination ?
    LinkedList<ODCell> dest = null;
    dest = demands.get(listIndex).destinations.get(odCell.getDestinationNodeId());

    if (dest == null) { // No destination was already associated with this origin
      LinkedList<ODCell> cells = new LinkedList<>();
      cells.add(odCell);
      demands.get(listIndex).destinations.put(odCell.getDestinationNodeId(), cells);
    } else {
      // Search if this OD pair already exists in the list
      boolean alreadyExists = false;

      Iterator<ODCell> it = dest.iterator();
      while (it.hasNext()) {
        ODCell cell = it.next();

        if (odCell.getStartingTime() != -1) {
          if (cell.getDestinationNodeId() == odCell.getDestinationNodeId()
              && cell.getGroup() == odCell.getGroup()
              && cell.getStartingTime() == odCell.getStartingTime()) {
            cell.addQuantity(odCell.getQuantity());
            alreadyExists = true;
            break;
          }
        } else {
          if (cell.getDestinationNodeId() == odCell.getDestinationNodeId()
              && cell.getGroup() == odCell.getGroup()) {
            cell.addQuantity(odCell.getQuantity());
            alreadyExists = true;
            break;
          }
        }
      }

      if (!alreadyExists) {
        dest.add(odCell);
      }
    }
  }

  /**
   * Adds a demand to the real node associated to this list.
   *
   * @param odCell OD cell
   */
  public void addDemand(ODCell odCell) {
    storeDemand(0, odCell);
  }

  /**
   * Relocates a demand to a another real node during a dynamic assignment.
   *
   * @param odCell The OD cell to relocate.
   * @param newVirtualNodeId The ID of the virtual node the demand must be reallocated to.
   */
  public void relocateDemand(ODCell odCell, int newVirtualNodeId) {
    // Is there already a list associated to the given starting virtual node ?
    if (demands != null) {
      for (int i = 0; i < demands.size(); i++) {
        Demands d = demands.get(i);
        if (d.loadingVirtualNodeId == newVirtualNodeId) {
          storeDemand(i, odCell);
          return;
        }
      }
    } else {
      demands = new Vector<>(1, 1);
    }
  }

  /**
   * Adds an exclude operation to the real node associated to this list. The exclusions are stored
   * in lists, per scenario and group of commodities. the scenario independent (-1) and group
   * independent (-1) exclusions are stored at the first place of their respective lists.
   *
   * @param exclusion The NodeRule to store.
   */
  @SuppressWarnings("unchecked")
  public void addExclusion(NodeRule exclusion) {

    // Generic exclusions are stored ate index 0, scenario specific exclusions at index 1

    if (scenarioExclusions == null) {
      scenarioExclusions = new LinkedList[2];
    }

    int scenario = exclusion.getScenario();
    int group = exclusion.getGroup();

    if (scenario == -1 && scenarioExclusions[0] == null) {
      scenarioExclusions[0] = new LinkedList<GroupExclusions>();
    }

    if (scenario != -1 && scenarioExclusions[1] == null) {
      scenarioExclusions[1] = new LinkedList<GroupExclusions>();
    }

    int scenarioIndex = 0;
    if (scenario != -1) {
      scenarioIndex = 1;
    }

    // Is there already a list of exclusions for this group in the scenario ?
    LinkedList<GroupExclusions> groupExclusions = scenarioExclusions[scenarioIndex];
    Iterator<GroupExclusions> it = groupExclusions.iterator();
    boolean found = false;
    while (it.hasNext()) {
      GroupExclusions ge = it.next();
      if (ge.group == group) {
        ge.exclusions.add(exclusion);
        found = true;
        break;
      }
    }

    if (!found) {
      // Create a list for this group and add the exclusion
      GroupExclusions ge = new GroupExclusions(group);
      ge.exclusions.add(exclusion);
      // The generic group (-1) must be first in the list
      if (group == -1) {
        scenarioExclusions[scenarioIndex].addFirst(ge);
      } else {
        scenarioExclusions[scenarioIndex].add(ge);
      }
    }
  }

  /**
   * Adds a new virtual node to the list.
   *
   * @param virtualNode The virtual node to add.
   */
  public void addVirtualNode(VirtualNode virtualNode) {
    virtualNodeList.add(virtualNode);
  }

  /**
   * Returns a list of Demands for a given group and OD class. Returns null if no demand exists for
   * the group.
   *
   * @param group The group ID.
   * @param odClass The OD class ID to which the demand must belongs to.
   * @return LinkedList containing the Demands associated to this groups.
   */
  public LinkedList<ODCell> getDemandForGroup(int group, byte odClass) {

    if (demands == null) {
      return null;
    }

    LinkedList<ODCell> listForGroup = new LinkedList<>();
    Collection<LinkedList<ODCell>> values = demands.get(0).destinations.values();
    Iterator<LinkedList<ODCell>> it = values.iterator();
    while (it.hasNext()) {
      LinkedList<ODCell> demandList = it.next();
      synchronized (demandList) {
        Iterator<ODCell> it2 = demandList.iterator();
        while (it2.hasNext()) {
          ODCell cell = it2.next();
          if (cell.getGroup() == group && cell.getODClass() == odClass) {
            listForGroup.add(cell);
          }
        }
      }
    }
    if (listForGroup.size() == 0) {
      listForGroup = null;
    }

    return listForGroup;
  }

  /**
   * Returns the list of excluded movements associated to the real node for a given scenario and
   * group. If no scenario specific exclusions are defined, the exclusions are searched for the
   * generic (-1) scenario. Once the scenario (or generic) exclusions identified, one looks to
   * group, specific exclusions. If no group specific exclusions are defined, one looks for group
   * independent (-1) exclusions.
   *
   * @param scenario The scenario for which the exclusions are searched for.
   * @param group The group for which the exclusions are searched for.
   * @return A list of exclusions or null if none are found.
   */
  public LinkedList<NodeRule> getExclusions(int scenario, int group) {

    if (scenarioExclusions == null) {
      return null;
    }

    // Test scenario specific exclusions
    LinkedList<GroupExclusions> groupExclusions = scenarioExclusions[1];
    if (groupExclusions == null) {
      // Look for generic exclusions
      groupExclusions = scenarioExclusions[0];
    }

    if (groupExclusions == null) {
      return null;
    }

    // Are group specific exclusions present ?
    Iterator<GroupExclusions> it = groupExclusions.iterator();
    while (it.hasNext()) {
      GroupExclusions ge = it.next();
      if (ge.group == group) {
        return ge.exclusions;
      }
    }

    // Generic exclusions ?
    GroupExclusions ge = groupExclusions.getFirst();
    if (ge.group == -1) {
      return ge.exclusions;
    }

    return null;
  }

  /**
   * Returns the graphic representing to the real node associated to this list.
   *
   * @return The OMPoint representing the real node.
   */
  public OMPoint getGraphic() {
    return graphic;
  }

  /**
   * Returns the list of Demands or null if no demand exists from this node.
   *
   * @return LinkedList containing the Demands associated to this node
   */
  public LinkedList<ODCell> getInitialDemandList() {

    if (demands == null) {
      return null;
    } else {
      LinkedList<ODCell> allDestinationsList = new LinkedList<>();
      Collection<LinkedList<ODCell>> values = demands.get(0).destinations.values();

      Iterator<LinkedList<ODCell>> it = values.iterator();
      while (it.hasNext()) {
        LinkedList<ODCell> ll = it.next();
        Iterator<ODCell> it2 = ll.iterator();
        while (it2.hasNext()) {
          allDestinationsList.add(it2.next());
        }
      }

      return allDestinationsList;
    }
  }

  /**
   * Returns the ID of the loading virtual node associated to the real node.
   *
   * @return The ID of the loading virtual node.
   */
  public int getLoadingVirtualNodeId() {
    return demands.get(0).loadingVirtualNodeId;
  }

  /**
   * Returns the number of demand lists associated to the real node.
   *
   * @return The number of demand lists.
   */
  public int getNbDemandLists() {
    if (demands == null) {
      return 0;
    } else {
      return demands.size();
    }
  }

  /**
   * Returns the ID of the associated real node.
   *
   * @return The ID of the real node.
   */
  public int getRealNodeId() {
    return realNodeId;
  }

  /**
   * Returns the ID of the unloading virtual node associated to the real node.
   *
   * @return The ID of the unloading virtual node.
   */
  public int getUnloadingVirtualNodeId() {
    return demands.get(0).unloadingVirtualNodeId;
  }

  /**
   * Returns the list of virtual nodes associated to the real node.
   *
   * @return The list of virtual nodes.
   */
  public LinkedList<VirtualNode> getVirtualNodeList() {
    return virtualNodeList;
  }

  /**
   * Returns true if the real node has at least one non empty OD pair for the given group and class.
   *
   * @param group The ID of the group of commodities.
   * @param odClass The ID of the OD class.
   * @return True if a demand exists.
   */
  public boolean hasDemandForGroup(int group, byte odClass) {

    if (demands == null) {
      return false;
    }

    for (int i = 0; i < demands.size(); i++) {
      Collection<LinkedList<ODCell>> values = demands.get(i).destinations.values();
      Iterator<LinkedList<ODCell>> it = values.iterator();
      while (it.hasNext()) {
        LinkedList<ODCell> demandList = it.next();
        synchronized (demandList) {
          Iterator<ODCell> it2 = demandList.iterator();
          while (it2.hasNext()) {
            ODCell cell = it2.next();
            if (cell.getGroup() == group && cell.getODClass() == odClass) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  /**
   * Returns true if the associated real node is a potential location for changing service.
   *
   * @return True if a service change is possible at this node.
   */
  public boolean isChangingServiceNode() {
    return isChangingService;
  }

  /**
   * Returns true if the associated real node is a potential Loading/Unloading node.
   *
   * @return True if loading/unloading is possible at this node.
   */
  public boolean isLoadingUnloadingNode() {
    return isLoadingUnloading;
  }

  /**
   * Returns true if the associated real node is a potential transhipment node.
   *
   * @return True if transhipments are possible at this node.
   */
  public boolean isTranshipmentNode() {
    return isTranshipment;
  }

  /**
   * Returns true if transit operations are allowed for this node.
   *
   * @return True if transit is allowed.
   */
  public boolean isTransitAllowed() {
    return allowTransit;
  }

  /**
   * Removes a demand cell from the list.
   *
   * @param listIndex Index of the demand list the demand must be deleted from.
   * @param odCell The OD cell to remove.
   */
  public void removeDemand(int listIndex, ODCell odCell) {
    HashMap<Integer, LinkedList<ODCell>> destinations = demands.get(listIndex).destinations;

    LinkedList<ODCell> ll = destinations.get(odCell.getDestinationNodeId());
    synchronized (ll) {
      ll.remove(odCell);
    }
  }

  /**
   * Sets the loading virtual node associated to the real node.
   *
   * @param loadingVirtualNodeId The ID of the loading virtual node.
   */
  public void setLoadingVirtualNodeNum(int loadingVirtualNodeId) {
    if (demands == null) {
      System.err.println("Should never happen");
    } else {
      if (demands.size() == 1) {
        demands.get(0).loadingVirtualNodeId = loadingVirtualNodeId;
      } else {
        System.err.println("To be written (setLoadingVirtualNodeNum)");
      }
    }
  }

  /**
   * Sets the unloading virtual node associated to the real node.
   *
   * @param unloadingVirtualNodeId The ID of the unloading virtual node.
   */
  public void setUnloadingVirtualNodeId(int unloadingVirtualNodeId) {
    if (demands == null) {
      System.err.println("Should never happen");
    } else {
      if (demands.size() == 1) {
        demands.get(0).unloadingVirtualNodeId = unloadingVirtualNodeId;
      } else {
        System.err.println("To be written (setUnLoadingVirtualNodeNum)");
      }
    }
  }

  /** Returns a string representing the characteristics of the virtual node list. */
  @Override
  public String toString() {
    String s = "";
    s += "isLoadingUnloading " + isLoadingUnloading + "\n";
    s += "isTranshipment " + isTranshipment + "\n";
    s += "realNodeNum " + realNodeId + "\n";
    s += "loadingVirtualNodeId " + demands.get(0).loadingVirtualNodeId + "\n";
    s += "unloadingVirtualNodeId " + demands.get(0).unloadingVirtualNodeId + "\n";
    return s;
  }
}
