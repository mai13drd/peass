package de.peass.measurement.analysis.statistics;

import java.util.List;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.peass.measurement.rca.data.CallTreeNode;
import de.peass.measurement.rca.data.OneVMResult;

public class OutlierRemoverVMResults {

   private static final Logger LOG = LogManager.getLogger(OutlierRemoverVMResults.class);

   public static void getValuesWithoutOutliers(List<OneVMResult> results, SummaryStatistics statistics) {
      SummaryStatistics fullStatistic = new SummaryStatistics();
      for (final OneVMResult result : results) {
         final double average = result.getAverage();
         fullStatistic.addValue(average);
      }

      double min = fullStatistic.getMean() - OutlierRemover.Z_SCORE * fullStatistic.getStandardDeviation();
      double max = fullStatistic.getMean() + OutlierRemover.Z_SCORE * fullStatistic.getStandardDeviation();

      LOG.debug("Removing outliers between {} and {} - Old vm count: {}", min, max, results.size());
      for (final OneVMResult result : results) {
         final double average = result.getAverage();
         if (average >= min && average <= max) {
            statistics.addValue(average);
            LOG.trace("Adding value: {}", average);
         } else {
            LOG.debug("Not adding outlier: {}", average);
         }
      }
      LOG.debug("VM count after removal: {}", statistics.getN());
   }
}
