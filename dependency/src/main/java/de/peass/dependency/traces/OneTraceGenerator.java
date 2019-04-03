package de.peass.dependency.traces;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import de.peass.dependency.PeASSFolders;
import de.peass.dependency.analysis.CalledMethodLoader;
import de.peass.dependency.analysis.ModuleClassMapping;
import de.peass.dependency.analysis.data.ChangedEntity;
import de.peass.dependency.analysis.data.TestCase;
import de.peass.dependency.analysis.data.TraceElement;
import de.peass.dependency.traces.requitur.content.RuleContent;
import de.peass.dependencyprocessors.ViewNotFoundException;

public class OneTraceGenerator {

   private static final int MAX_SIZE_MB = 100;
   static final String METHOD = "_method";
   static final String METHOD_EXPANDED = "_method_expanded";
   public static final String NOCOMMENT = "_nocomment";

   private static final Logger LOG = LogManager.getLogger(OneTraceGenerator.class);

   private final File viewFolder;
   private final PeASSFolders folders;
   private final TestCase testcase;
   private final Map<String, List<File>> traceFileMap;
   private final String version;
   private final File resultsFolder;
   private final List<File> modules;

   public OneTraceGenerator(final File viewFolder, final PeASSFolders folders, final TestCase testcase, final Map<String, List<File>> traceFileMap, final String version,
         final File resultsFolder, final List<File> modules) {
      super();
      this.viewFolder = viewFolder;
      this.folders = folders;
      this.testcase = testcase;
      this.traceFileMap = traceFileMap;
      this.version = version;
      this.resultsFolder = resultsFolder;
      this.modules = modules;
   }

   private File getClazzDir(final String version, final TestCase testcase) {
      final File viewResultsFolder = new File(viewFolder, "view_" + version);
      if (!viewResultsFolder.exists()) {
         viewResultsFolder.mkdir();
      }
      final File clazzDir = new File(viewResultsFolder, testcase.getClazz());
      if (!clazzDir.exists()) {
         clazzDir.mkdir();
      }
      return clazzDir;
   }

   public static File getClazzMethodFolder(final TestCase testcase, final File resultsFolder) throws ViewNotFoundException {
      final File projectResultFolder = new File(resultsFolder, testcase.getClazz());
      final File[] listFiles = projectResultFolder.listFiles(new FileFilter() {
         @Override
         public boolean accept(final File pathname) {
            return pathname.getName().matches("[0-9]*");
         }
      });
      if (listFiles == null) {
         throw new ViewNotFoundException("Result folder: " + Arrays.toString(listFiles) + " ("
               + (listFiles != null ? listFiles.length : "null") + ") in " + projectResultFolder.getAbsolutePath() + " should exist!");
      }

      File methodResult = getMethodFolder(testcase, listFiles);

      LOG.debug("Searching for: {}", methodResult);

      if (methodResult.exists() && methodResult.isDirectory()) {
         return methodResult;
      } else {
         throw new RuntimeException("Folder " + methodResult + " is no Kieker result folder!");
      }
   }

   private static File getMethodFolder(final TestCase testcase, final File[] listFiles) {
      File methodResult = new File(listFiles[0], testcase.getMethod());
      for (final File test : listFiles) {
         final File candidate = new File(test, testcase.getMethod());
         if (candidate.exists()) {
            methodResult = candidate;
         }
      }
      return methodResult;
   }

   public boolean generateTrace(final String versionCurrent)
         throws com.github.javaparser.ParseException, IOException, ViewNotFoundException, XmlPullParserException {
      boolean success = false;
      try {
         File methodResult = getClazzMethodFolder(testcase, resultsFolder);
         LOG.debug("Searching for: {}", methodResult);
         if (methodResult.exists() && methodResult.isDirectory()) {
            success = generateTraceFiles(versionCurrent, methodResult);
         } else {
            LOG.error("Error: {} does not produce {}", versionCurrent, methodResult.getAbsolutePath());
         }
      } catch (final RuntimeException e) {
         e.printStackTrace();
      }
      return success;
   }

   private boolean generateTraceFiles(final String versionCurrent, final File methodResult)
         throws FileNotFoundException, IOException, XmlPullParserException, com.github.javaparser.ParseException {
      boolean success = false;
      final long size = FileUtils.sizeOfDirectory(methodResult);
      final long sizeInMB = size / (1024 * 1024);
      LOG.debug("Filesize: {} ({})", sizeInMB, size);
      if (sizeInMB < MAX_SIZE_MB) {
         final File[] possiblyMethodFolder = methodResult.listFiles();
         final File kiekerResultFolder = possiblyMethodFolder[0];
         final ModuleClassMapping mapping = new ModuleClassMapping(folders.getProjectFolder(), modules);
         final List<TraceElement> shortTrace = new CalledMethodLoader(kiekerResultFolder, mapping).getShortTrace("");
         if (shortTrace != null) {
            LOG.debug("Short Trace: {} Folder: {} Project: {}", shortTrace.size(), methodResult.getAbsolutePath(), folders.getProjectFolder());
            final List<File> files = new LinkedList<>();
            for (int i = 0; i < modules.size(); i++) {
               final File module = modules.get(i);
               for (int folderIndex = 0; folderIndex < ChangedEntity.potentialClassFolders.length; folderIndex++) {
                  final String path = ChangedEntity.potentialClassFolders[folderIndex];
                  files.add(new File(module, path));
               }
            }
            if (shortTrace.size() > 0) {
               final TraceMethodReader traceMethodReader = new TraceMethodReader(shortTrace, files.toArray(new File[0]));
               final TraceWithMethods trace = traceMethodReader.getTraceWithMethods();
               List<File> traceFiles = traceFileMap.get(testcase.toString());
               if (traceFiles == null) {
                  traceFiles = new LinkedList<>();
                  traceFileMap.put(testcase.toString(), traceFiles);
               }
               final File methodDir = new File(getClazzDir(version, testcase), testcase.getMethod());
               if (!methodDir.exists()) {
                  methodDir.mkdir();
               }
               String shortVersion = versionCurrent.substring(0, 6);
               if (versionCurrent.endsWith("~1")) {
                  shortVersion = shortVersion + "~1";
               }
               final File currentTraceFile = new File(methodDir, shortVersion);
               traceFiles.add(currentTraceFile);
               Files.write(currentTraceFile.toPath(), trace.getWholeTrace().getBytes());
               final File commentlessTraceFile = new File(methodDir, shortVersion + NOCOMMENT);
               Files.write(commentlessTraceFile.toPath(), trace.getCommentlessTrace().getBytes());
               final File methodTrace = new File(methodDir, shortVersion + METHOD);
               Files.write(methodTrace.toPath(), trace.getTraceMethods().getBytes());
               if (sizeInMB < 10) {
                  final File methodExpandedTrace = new File(methodDir, shortVersion + METHOD_EXPANDED);
                  Files.write(methodExpandedTrace.toPath(), traceMethodReader.getExpandedTrace()
                        .stream()
                        .filter(value -> !(value instanceof RuleContent))
                        .map(value -> value.toString()).collect(Collectors.toList()));
               } else {
                  LOG.debug("Do not write expanded trace - size: {} MB", sizeInMB);
               }
               LOG.debug("Datei {} existiert: {}", methodTrace.getAbsolutePath(), methodTrace.exists());
               success = true;
            }
         }
      } else {
         LOG.error("File size exceeds 2000 MB");
      }
      return success;
   }
}