package de.peass.dependency.execution;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class AOPXMLHelper {

   public static final String OPERATIONEXECUTION = "kieker.monitoring.probe.aspectj.operationExecution.OperationExecutionAspectFull";
   public static final String REDUCED_OPERATIONEXECUTION = "kieker.monitoring.probe.aspectj.operationExecution.ReducedOperationExecutionAspectFull";

   public static void writeAOPXMLToFile(final List<String> allClasses, final File goalFile, String aspect) throws IOException {
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(goalFile))) {
         writer.write("<!DOCTYPE aspectj PUBLIC \"-//AspectJ//DTD//EN\" \"http://www.aspectj.org/dtd/aspectj_1_5_0.dtd\">\n");
         writer.write("<aspectj>\n");
         writer.write(" <weaver options=\"-verbose\">\n");
         writer.write("   <include within=\"de.peass.generated.GeneratedTest\" />\n");
         for (final String clazz : allClasses) {
            if (!clazz.contains("$")) { // Fix: Kieker 1.12 is not able to read inner-class-entries
               writer.write("   <include within=\"" + clazz + "\" />\n");
            }
         }
         writer.write(" </weaver>\n");
         writer.write(" <aspects>");
         writer.write("    <aspect ");
         writer.write("name=\"" + aspect + "\" />");
         writer.write(" </aspects>\n");
         writer.write("</aspectj>");
         writer.flush();
      }
   }

   public static void writeKiekerMonitoringProperties(final File goalFile, boolean useFastMeasurement) throws IOException {
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(goalFile))) {
         writer.write("kieker.monitoring.name=KIEKER-KoPeMe\n");
         if (useFastMeasurement) {
            writer.write("kieker.monitoring.core.controller.WriterController.RecordQueueFQN=org.apache.commons.collections4.queue.CircularFifoQueue\n");
            writer.write("kieker.monitoring.core.controller.WriterController.QueuePutStrategy=kieker.monitoring.queue.putstrategy.YieldPutStrategy\n");
            writer.write("kieker.monitoring.core.controller.WriterController.QueueTakeStrategy=kieker.monitoring.queue.takestrategy.YieldTakeStrategy\n");
         } else {
            writer.write("kieker.monitoring.core.controller.WriterController.RecordQueueFQN=java.util.concurrent.LinkedBlockingQueue\n");
         }
         writer.write("kieker.monitoring.writer=kieker.monitoring.writer.filesystem.ChangeableFolderWriter\n");
         writer.write("kieker.monitoring.writer.filesystem.ChangeableFolderWriter.realwriter=FileWriter\n");
         final int queueSize = 10000000;
         writer.write("kieker.monitoring.core.controller.WriterController.RecordQueueSize=" + queueSize + "\n");
         writer.write("kieker.monitoring.writer.filesystem.ChangeableFolderWriter.flush=false\n");
         writer.write("kieker.monitoring.writer.filesystem.FileWriter.logStreamHandler=kieker.monitoring.writer.filesystem.BinaryLogStreamHandler\n");
         writer.flush();
      }
   }
}
