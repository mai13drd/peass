package de.peass;

import java.io.IOException;

import javax.xml.bind.JAXBException;

import de.peass.dependency.execution.MeasurementConfiguration;
import de.peass.dependencyprocessors.AdaptiveTester;
import de.peass.testtransformation.JUnitTestTransformer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Executes test and skips those where results clearly indicate a performance change
 * 
 * @author reichelt
 *
 */
@Command(description = "Measures the defined tests and versions until agnostic t-test makes a non-agnostic decission", name = "measure")
public class AdaptiveTestStarter extends DependencyTestPairStarter {

   @Option(names = { "-type1error", "--type1error" }, description = "Type 1 error of agnostic-t-test, i.e. probability of considering measurements equal when they are unequal")
   public double type1error = 0.05;

   @Option(names = { "-type2error", "--type2error" }, description = "Type 2 error of agnostic-t-test, i.e. probability of considering measurements unequal when they are equal")
   protected double type2error = 0.01;
   
   @Option(names = { "-skipEarlyStop", "--skipEarlyStop" }, description = "Whether to skip early stop (i.e. execute all VMs even if decision can already be made)")
   protected boolean skipEarlyStop = false;

   public AdaptiveTestStarter() throws JAXBException, IOException {

   }

   public static void main(final String[] args) throws JAXBException, IOException {
      final AdaptiveTestStarter command = new AdaptiveTestStarter();
      final CommandLine commandLine = new CommandLine(command);
      commandLine.execute(args);

   }

   @Override
   public Void call() throws Exception {
      super.call();
      final MeasurementConfiguration measurementConfiguration = new MeasurementConfiguration(timeout, vms, type1error, type2error);
      measurementConfiguration.setEarlyStop(!skipEarlyStop);
      final JUnitTestTransformer testgenerator = getTestTransformer(measurementConfiguration);

      tester = new AdaptiveTester(folders, testgenerator);

      processCommandline();
      return null;
   }

}
