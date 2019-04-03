package de.peass.utils;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Constants {

   public static final String PEASS_REPOS = "PEASS_REPOS";
   public static final String PEASS_PROJECTS = "PEASS_PROJECTS";

   public static final String[] VALIDATION_PROJECTS = new String[] { "commons-compress", "commons-csv",
         "commons-dbcp", "commons-fileupload", "commons-imaging", "commons-io", "commons-jcs", "commons-numbers", "commons-pool", "commons-text" };

   public static Map<String, String> defaultUrls = new HashMap<>();

   static {
      // Chunk 1
      defaultUrls.put("commons-compress", "https://github.com/apache/commons-compress.git");
      defaultUrls.put("commons-csv", "https://github.com/apache/commons-csv.git");
      defaultUrls.put("commons-dbcp", "https://github.com/apache/commons-dbcp.git");
      defaultUrls.put("commons-fileupload", "https://github.com/apache/commons-fileupload.git");
      defaultUrls.put("commons-imaging", "https://github.com/apache/commons-imaging.git");
      defaultUrls.put("commons-io", "https://github.com/apache/commons-io.git");
      defaultUrls.put("commons-text", "https://github.com/apache/commons-text.git");

      // Chunk 2
      defaultUrls.put("commons-pool", "https://github.com/apache/commons-pool.git");
      defaultUrls.put("commons-numbers", "https://github.com/apache/commons-numbers.git");
      defaultUrls.put("commons-jcs", "https://github.com/apache/commons-jcs.git");
      defaultUrls.put("httpcomponents-core", "https://github.com/apache/httpcomponents-core.git");
      defaultUrls.put("k-9", "https://github.com/k9mail/k-9.git");

      // Future candidates
      defaultUrls.put("commons-math", "https://github.com/apache/commons-math.git");
      defaultUrls.put("commons-lang", "https://github.com/apache/commons-lang.git");
      defaultUrls.put("jackson-core", "https://github.com/FasterXML/jackson-core.git");
      defaultUrls.put("spring-framework", "https://github.com/spring-projects/spring-framework.git");
      defaultUrls.put("maven", "https://github.com/apache/maven.git");
      defaultUrls.put("okhttp", "https://github.com/square/okhttp.git");
   }

   public final static ObjectMapper OBJECTMAPPER = new ObjectMapper();
}