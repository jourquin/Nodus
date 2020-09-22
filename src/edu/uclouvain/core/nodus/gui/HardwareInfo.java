package edu.uclouvain.core.nodus.gui;

import edu.uclouvain.core.nodus.utils.HardwareUtils;

public class HardwareInfo {

  public HardwareInfo() {

    String computerInfo = HardwareUtils.getComputerInfo();
    System.out.println(computerInfo);

    System.out.println();
    String processorInfo = HardwareUtils.getProcessorInfo();
    System.out.println(processorInfo);

    System.out.println();
    String displayInfo = HardwareUtils.getDisplayInfo();
    System.out.println(displayInfo);
    
    System.out.println();
    String graphicCardsInfo = HardwareUtils.getGraphicsCardInfo();
    System.out.println(graphicCardsInfo);
    
    

    System.out.println();
    String osInfo = HardwareUtils.getOsInfo();
    System.out.println(osInfo);

    System.out.println();
    String totalMemoryInfo = HardwareUtils.getTotalMemoryInfo();
    System.out.println(totalMemoryInfo);
    
    System.out.println();
    String availableMemoryInfo = HardwareUtils.getAvailableMemoryInfo();
    System.out.println(availableMemoryInfo);
    
    //HardwareUtils.getFileSystemInfo();
  }

  public static void main(String[] args) {
    new HardwareInfo();
  }
}
