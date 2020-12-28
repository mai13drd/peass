package de.peass.measurement.rca.analyzer;

import java.util.LinkedList;
import java.util.List;

import de.peass.measurement.rca.data.CallTreeNode;
import de.peass.measurement.rca.data.CauseSearchData;
import de.peass.measurement.rca.treeanalysis.TreeUtil;

public class CompleteTreeAnalyzer implements TreeAnalyzer {
   private final List<CallTreeNode> treeStructureDiffering = new LinkedList<>();
   private final List<CallTreeNode> allNodesPredecessor = new LinkedList<>();

   public CompleteTreeAnalyzer(final CallTreeNode root, final CallTreeNode rootPredecessor) {
      root.setOtherVersionNode(rootPredecessor);
      rootPredecessor.setOtherVersionNode(root);

      allNodesPredecessor.add(rootPredecessor);

      mapAllNodes(root, rootPredecessor);
   }

   private void mapAllNodes(final CallTreeNode current, final CallTreeNode currentPredecessor) {
      TreeUtil.findChildMapping(current, currentPredecessor);
      findTreeStructureDiffering(current, currentPredecessor);

      for (CallTreeNode currentChild : current.getChildren()) {
         mapAllNodes(currentChild, currentChild.getOtherVersionNode());

         allNodesPredecessor.add(currentChild.getOtherVersionNode());
      }
   }

   private void findTreeStructureDiffering(final CallTreeNode current, final CallTreeNode currentPredecessor) {
      current.getChildren().forEach(child -> {
         if (child.getCall().equals(CauseSearchData.ADDED)) {
            treeStructureDiffering.add(child.getOtherVersionNode());
         }
      });
      currentPredecessor.getChildren().forEach(child -> {
         if (child.getCall().equals(CauseSearchData.ADDED)) {
            treeStructureDiffering.add(child.getOtherVersionNode());
         }
      });
   }

   public List<CallTreeNode> getTreeStructureDiffering() {
      return treeStructureDiffering;
   }

   public List<CallTreeNode> getMeasurementNodesPredecessor() {
      return allNodesPredecessor;
   }
}