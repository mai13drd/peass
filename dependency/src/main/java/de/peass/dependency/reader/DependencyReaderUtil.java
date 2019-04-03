/**
 *     This file is part of PerAn.
 *
 *     PerAn is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     PerAn is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with PerAn.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.peass.dependency.reader;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;

import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.javaparser.printer.lexicalpreservation.changes.Change;

import de.peass.dependency.analysis.data.CalledMethods;
import de.peass.dependency.analysis.data.ChangeTestMapping;
import de.peass.dependency.analysis.data.ChangedEntity;
import de.peass.dependency.analysis.data.ClazzChangeData;
import de.peass.dependency.analysis.data.TestCase;
import de.peass.dependency.analysis.data.TestDependencies;
import de.peass.dependency.analysis.data.TestExistenceChanges;
import de.peass.dependency.analysis.data.TestSet;
import de.peass.dependency.persistence.Dependencies;
import de.peass.dependency.persistence.Version;
import de.peass.dependencyprocessors.VersionComparator;
import de.peass.statistics.DependencyStatisticAnalyzer;
import de.peass.utils.Constants;
import de.peass.utils.OptionConstants;

/**
 * Utility function for reading dependencies
 * 
 * @author reichelt
 *
 */
public class DependencyReaderUtil {

   private static final Logger LOG = LogManager.getLogger(DependencyReaderUtil.class);

   static void removeDeletedTestcases(final Version newVersionInfo, final TestExistenceChanges testExistenceChanges) {
      LOG.debug("Removed Tests: {}", testExistenceChanges.getRemovedTests());
      for (final TestCase removedTest : testExistenceChanges.getRemovedTests()) {
         LOG.debug("Remove: {}", removedTest);
         for (final Entry<ChangedEntity, TestSet> dependency : newVersionInfo.getChangedClazzes().entrySet()) {
            final TestSet testSet = dependency.getValue();
            if (removedTest.getMethod().length() > 0) {
               for (final Entry<ChangedEntity, Set<String>> testcase : testSet.getTestcases().entrySet()) {
                  if (testcase.getKey().getJavaClazzName().equals(removedTest.getClazz())) {
                     testcase.getValue().remove(removedTest.getMethod());
                  }
               }
            } else {
               ChangedEntity removeTestcase = null;
               for (final Entry<ChangedEntity, Set<String>> testcase : testSet.getTestcases().entrySet()) {
                  if (testcase.getKey().getClazz().equals(removedTest.getClazz())) {
                     removeTestcase = testcase.getKey();
                  }
               }
               // Tests may not be changed by a class change - so a test needs only to be removed, if he is there
               if (removeTestcase != null) {
                  testSet.removeTest(removeTestcase);
               }
            }
         }
      }
   }

   static void addNewTestcases(final Version newVersionInfo, final Map<ChangedEntity, Set<ChangedEntity>> newTestcases) {
      for (final Map.Entry<ChangedEntity, Set<ChangedEntity>> newTestcase : newTestcases.entrySet()) {
         final ChangedEntity changedClazz = newTestcase.getKey();
         TestSet testsetForChange = null;
         for (final Entry<ChangedEntity, TestSet> dependency : newVersionInfo.getChangedClazzes().entrySet()) {
            ChangedEntity dependencyChangedClazz = dependency.getKey();
            if (dependencyChangedClazz.equals(changedClazz)) {
               testsetForChange = dependency.getValue();
            }
         }
         if (testsetForChange == null) {
            testsetForChange = new TestSet();
            newVersionInfo.getChangedClazzes().put(changedClazz, testsetForChange);
         }
         for (final ChangedEntity testcase : newTestcase.getValue()) {
            testsetForChange.addTest(testcase.onlyClazz(), testcase.getMethod());
         }
      }
   }

   static Version createVersionFromChangeMap(final String revision, final Map<ChangedEntity, ClazzChangeData> changedClassNames, final ChangeTestMapping changeTestMap) {
      final Version newVersionInfo = new Version();
      newVersionInfo.setRunning(true);
      LOG.debug("Beginne schreiben");
      // changeTestMap.keySet ist fast wie changedClassNames, bloß dass
      // Klassen ohne Abhängigkeit drin sind
      for (final Map.Entry<ChangedEntity, ClazzChangeData> changedClassName : changedClassNames.entrySet()) {
         ClazzChangeData changedClazzInsideFile = changedClassName.getValue();
         if (!changedClazzInsideFile.isOnlyMethodChange()) { // class changed as a whole
            handleWholeClassChange(changeTestMap, newVersionInfo, changedClazzInsideFile);
         } else {
            handleMethodChange(changeTestMap, newVersionInfo, changedClazzInsideFile);
         }
      }
      LOG.debug("Testrevision: " + revision);
      return newVersionInfo;

   }

   private static void handleMethodChange(final ChangeTestMapping changeTestMap, final Version version, final ClazzChangeData changedClassName) {
      for (ChangedEntity underminedChange : changedClassName.getChanges()) {
         boolean contained = false;

         final ChangedEntity changedEntryFullName = new ChangedEntity(underminedChange.getJavaClazzName(), underminedChange.getModule(), underminedChange.getMethod());
         for (final Entry<ChangedEntity, TestSet> currentDependency : version.getChangedClazzes().entrySet()) {
            if (currentDependency.getKey().equals(changedEntryFullName)) {
               contained = true;
            }
         }
         if (!contained) {
            final TestSet tests = new TestSet();
            if (changeTestMap.getChanges().containsKey(underminedChange)) {
               for (final ChangedEntity testClass : changeTestMap.getChanges().get(underminedChange)) {
                  tests.addTest(testClass.onlyClazz(), testClass.getMethod());
               }
            }
            version.getChangedClazzes().put(changedEntryFullName, tests);
         }
      }
   }

   private static void handleWholeClassChange(final ChangeTestMapping changeTestMap, final Version version, final ClazzChangeData changedClassName) {
      for (ChangedEntity underminedChange : changedClassName.getUniqueChanges()) {
         final TestSet tests = new TestSet();
         ChangedEntity realChange = underminedChange.onlyClazz();
         Set<ChangedEntity> testEntities = changeTestMap.getTests(realChange);
         if (testEntities != null) {
            for (final ChangedEntity testcase : testEntities) {
               if (testcase.getMethod() != null) {
                  tests.addTest(testcase.onlyClazz(), testcase.getMethod());
               } else {
                  throw new RuntimeException("Testcase without method detected: " + testcase + " Dependency: " + tests);
               }
            }
         }
         if (version.getChangedClazzes().containsKey(realChange)) {
            throw new RuntimeException("Clazz FQNs are unique in Java, but " + realChange.getJavaClazzName() + " was added twice!");
         }
         version.getChangedClazzes().put(realChange, tests);
      }
   }

   private static void addChangeEntry(final ChangedEntity changedFullname, final ChangedEntity currentTestcase, final ChangeTestMapping changeTestMap) {
      Set<ChangedEntity> changedClasses = changeTestMap.getChanges().get(changedFullname);
      if (changedClasses == null) {
         changedClasses = new HashSet<>();
         changeTestMap.getChanges().put(changedFullname, changedClasses);
         // TODO: Statt einfach die Klasse nehmen prüfen, ob die Methode genutzt wird
      }
      LOG.debug("Füge {} zu {} hinzu", currentTestcase, changedFullname);
      changedClasses.add(currentTestcase);
   }

   /**
    * Returns a list of all tests that changed based on given changed classes and the dependencies of the current version. So the result mapping is changedclass to a set of tests,
    * that could have been changed by this changed class.
    * 
    * @param dependencies
    * @param changes
    * @return Map from changed class to the influenced tests
    */
   static ChangeTestMapping getChangeTestMap(final TestDependencies dependencies, final Map<ChangedEntity, ClazzChangeData> changes) {
      final ChangeTestMapping changeTestMap = new ChangeTestMapping();
      for (final Entry<ChangedEntity, CalledMethods> dependencyEntry : dependencies.getDependencyMap().entrySet()) {
         final ChangedEntity currentTestcase = dependencyEntry.getKey();
         final CalledMethods currentTestDependencies = dependencyEntry.getValue();
         for (ClazzChangeData changedEntry : changes.values()) {
            for (ChangedEntity change : changedEntry.getChanges()) {
               final ChangedEntity changedClass = change.onlyClazz();
               final Set<ChangedEntity> calledClasses = currentTestDependencies.getCalledClasses();
               if (calledClasses.contains(changedClass)) {
                  if (!changedEntry.isOnlyMethodChange()) {
                     addChangeEntry(changedClass, currentTestcase, changeTestMap);
                  } else {
                     String method = change.getMethod();
                     final Map<ChangedEntity, Set<String>> calledMethods = currentTestDependencies.getCalledMethods();
                     final Set<String> calledMethodsInChangeClass = calledMethods.get(changedClass);
                     final int parameterIndex = method.indexOf("("); // TODO Parameter korrekt prüfen
                     final String methodWithoutParameters = parameterIndex != -1 ? method.substring(0, parameterIndex) : method;
                     if (calledMethodsInChangeClass.contains(methodWithoutParameters)) {
                        final ChangedEntity classWithMethod = new ChangedEntity(changedClass.getClazz(), changedClass.getModule(), method);
                        addChangeEntry(classWithMethod, currentTestcase, changeTestMap);
                     }
                  }
               }
            }
         }
      }
      for (final Map.Entry<ChangedEntity, Set<ChangedEntity>> element : changeTestMap.getChanges().entrySet()) {
         LOG.debug("Element: {} Dependencies: {} {}", element.getKey(), element.getValue().size(), element.getValue());
      }

      return changeTestMap;
   }

   public static void write(final Dependencies deps, final File file) {
      LOG.debug("Schreibe in: {}", file);
      try {
         Constants.OBJECTMAPPER.writeValue(file, deps);
      } catch (final IOException e) {
         e.printStackTrace();
      }
   }

   public static void loadDependencies(final CommandLine line) throws JAXBException {
      if (line.hasOption(OptionConstants.DEPENDENCYFILE.getName())) {
         final File dependencyFile = new File(line.getOptionValue(OptionConstants.DEPENDENCYFILE.getName()));
         final Dependencies dependencies = DependencyStatisticAnalyzer.readVersions(dependencyFile);
         VersionComparator.setDependencies(dependencies);
      } else {
         LOG.error("No dependencyfile information passed.");
         throw new RuntimeException("No dependencyfile information passed.");
      }
   }

   public static Dependencies mergeDependencies(final Dependencies deps1, final Dependencies deps2) {
      final Dependencies merged;
      final Dependencies newer;
      if (VersionComparator.isBefore(deps1.getInitialversion().getVersion(), deps2.getInitialversion().getVersion())) {
         merged = deps1;
         newer = deps2;
      } else {
         newer = deps1;
         merged = deps2;
      }
      LOG.debug("Merging: {}", merged.getVersions().size());

      final List<String> removableVersion = new LinkedList<>();
      String mergeVersion = null;
      final Iterator<String> iterator = newer.getVersions().keySet().iterator();
      if (iterator.hasNext()) {
         final String firstOtherVersion = iterator.next();
         for (final String version : merged.getVersions().keySet()) {
            if (merged == null && version.equals(firstOtherVersion) || VersionComparator.isBefore(firstOtherVersion, version)) {
               mergeVersion = version;
            }
            if (mergeVersion != null) {
               removableVersion.add(version);
            }
         }
      } else {
         return merged;
      }

      // if (mergeVersion == null) {
      // LOG.error("Version {} was newer than newest version of old dependencies - merging not possible", firstOtherVersion);
      // return null;
      // }
      LOG.debug("Removable: " + removableVersion.size());
      for (final String version : removableVersion) {
         merged.getVersions().remove(version);
      }
      int add = 0;
      for (final Map.Entry<String, Version> newerVersion : newer.getVersions().entrySet()) {
         // LOG.debug("Add: {}", newerVersion.getKey());
         add++;
         merged.getVersions().put(newerVersion.getKey(), newerVersion.getValue());
      }
      LOG.debug("Added: {} Size: {}", add, merged.getVersions().size());
      return merged;
   }
}