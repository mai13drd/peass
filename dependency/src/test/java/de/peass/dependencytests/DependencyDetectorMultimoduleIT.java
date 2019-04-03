package de.peass.dependencytests;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import co.unruly.matchers.StreamMatchers;
import de.peass.dependency.ChangeManager;
import de.peass.dependency.analysis.data.ChangedEntity;
import de.peass.dependency.analysis.data.ClazzChangeData;
import de.peass.dependency.analysis.data.TestCase;
import de.peass.dependency.analysis.data.TestSet;
import de.peass.dependency.persistence.InitialDependency;
import de.peass.dependency.reader.DependencyReader;
import de.peass.dependencytests.helper.FakeFileIterator;
import de.peass.vcs.VersionIterator;

public class DependencyDetectorMultimoduleIT {

   private static final Logger LOG = LogManager.getLogger(DependencyDetectorMultimoduleIT.class);

   private static final File VERSIONS_FOLDER = new File("src/test/resources/dependencyIT_multimodule");
   private static final File CURRENT = new File(new File("target"), "current");
   private static final File BASIC_STATE = new File(VERSIONS_FOLDER, "basic_state");

   // private DependencyManager handler;

   @Before
   public void initialize() throws IOException, InterruptedException {
      Assert.assertTrue(VERSIONS_FOLDER.exists());

      FileUtils.deleteDirectory(CURRENT);
      FileUtils.copyDirectory(BASIC_STATE, CURRENT);

      // handler = new DependencyManager(CURRENT);
      // final boolean success = handler.initialyGetTraces();
      //
      // Assert.assertTrue(success);
   }

   // @org.junit.After
   // public void cleanAfterwards() throws IOException {
   // FileUtils.deleteDirectory(CURRENT);
   // // be aware: maven does not compile if a .class-file is still in the resources, since it gets identified as test
   // }

   @Test
   public void testNormalChange() throws IOException, InterruptedException, XmlPullParserException {
      final File secondVersion = new File(VERSIONS_FOLDER, "normal_change");

      final VersionIterator fakeIterator = new FakeFileIterator(CURRENT, Arrays.asList(secondVersion));

      final Map<ChangedEntity, ClazzChangeData> changes = new TreeMap<>();
      DependencyDetectorIT.addChange(changes, "base-module", "de.dagere.base.BaseChangeable", "doSomething");

      ChangeManager changeManager = Mockito.mock(ChangeManager.class);
      Mockito.when(changeManager.getChanges(Mockito.any())).thenReturn(changes);
      final DependencyReader reader = new DependencyReader(CURRENT, new File("/dev/null"), null, fakeIterator, 5000, changeManager);

      final boolean success = reader.readInitialVersion();
      Assert.assertTrue(success);

      LOG.debug(reader.getDependencies().getInitialversion().getInitialDependencies());
      final InitialDependency dependency = reader.getDependencies().getInitialversion().getInitialDependencies()
            .get(new ChangedEntity("de.AnotherTest", "using-module", "testMeAlso"));
      LOG.debug(dependency.getEntities());
      Assert.assertThat(dependency.getEntities(), IsCollectionContaining.hasItem(new ChangedEntity("de.dagere.base.BaseChangeable", "base-module", "doSomething")));

      fakeIterator.goToNextCommit();
      reader.analyseVersion(changeManager);

      final TestSet foundDependency = DependencyDetectorIT.findDependency(reader.getDependencies(), "base-module§de.dagere.base.BaseChangeable#doSomething",
            DependencyDetectorIT.VERSION_1);
      testBaseChangeEffect(foundDependency);
   }

   @Test
   public void testTwoChanges()
         throws IOException, XmlPullParserException, InterruptedException {
      final File thirdVersion = new File(VERSIONS_FOLDER, "another_change");

      final VersionIterator fakeIterator = new FakeFileIterator(CURRENT, Arrays.asList(thirdVersion));

      final Map<ChangedEntity, ClazzChangeData> changes = new TreeMap<>();
      DependencyDetectorIT.addChange(changes, "base-module", "de.dagere.base.BaseChangeable", "doSomething");
      DependencyDetectorIT.addChange(changes, "base-module", "de.dagere.base.NextBaseChangeable", "doSomething");

      ChangeManager changeManager = Mockito.mock(ChangeManager.class);
      Mockito.when(changeManager.getChanges(Mockito.any())).thenReturn(changes);
      final DependencyReader reader = new DependencyReader(CURRENT, new File("/dev/null"), null, fakeIterator, 5000, changeManager);
      final boolean success = reader.readInitialVersion();
      Assert.assertTrue(success);

      fakeIterator.goToNextCommit();
      reader.analyseVersion(changeManager);

      final TestSet foundDependency2 = DependencyDetectorIT.findDependency(reader.getDependencies(), "base-module§de.dagere.base.BaseChangeable#doSomething",
            DependencyDetectorIT.VERSION_1);
      testBaseChangeEffect(foundDependency2);

      final TestSet foundDependency3 = DependencyDetectorIT.findDependency(reader.getDependencies(), "base-module§de.dagere.base.NextBaseChangeable#doSomething",
            DependencyDetectorIT.VERSION_1);
      Assert.assertThat(foundDependency3.getTests().stream(), StreamMatchers.anyMatch(
            Matchers.allOf(Matchers.hasProperty("clazz", Matchers.is("de.NextTest")),
                  Matchers.hasProperty("method", Matchers.isOneOf("nextTestMe", "nextTestMeAlso")))));
   }

   

   private void testBaseChangeEffect(final TestSet foundDependency) {

      System.out.println(foundDependency.getTestcases());

      Assert.assertThat(foundDependency.getTests().stream(), StreamMatchers.anyMatch(
            Matchers.allOf(Matchers.hasProperty("clazz", Matchers.is("de.dagere.base.BaseTest")),
                  Matchers.hasProperty("method", Matchers.isOneOf("testMe", "testMeAlso")))));

      Assert.assertThat(foundDependency.getTests().stream(), StreamMatchers.anyMatch(
            Matchers.allOf(Matchers.hasProperty("clazz", Matchers.is("de.NextTest")),
                  Matchers.hasProperty("method", Matchers.is("nextTestMe")))));

      Assert.assertThat(foundDependency.getTests().stream(), StreamMatchers.anyMatch(
            Matchers.allOf(Matchers.hasProperty("clazz", Matchers.is("de.AnotherTest")),
                  Matchers.hasProperty("method", Matchers.isOneOf("testMeAlso")))));
   }
}