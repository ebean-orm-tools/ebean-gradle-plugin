package io.ebean.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskOutputs
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile

class EnhancePlugin implements Plugin<Project> {

  private final Logger logger = Logging.getLogger(EnhancePlugin.class)

  private static def supportedCompilerTasks = [
    'processResources',
    'compileJava',
    'compileKotlin',
    'compileGroovy',
    'compileScala',
    'compileKotlinAfterJava',
    'copyMainKotlinClasses',
    'classes',
    'testClasses',
    'compileTestJava',
    'compileTestKotlin',
    'compileTestGroovy',
    'compileTestScala']

  /**
   * Output directories containing classes we want to run enhancement on.
   */
  private def outputDirs = new HashMap<Project, Set<File>>()

  /**
   * Test output directories containing classes we want to run enhancement on.
   */
  private def testOutputDirs = new HashMap<Project, Set<File>>()

  void apply(Project project) {
    def params = project.extensions.create("ebean", EnhancePluginExtension)

    // delay the registration of the various compile task.doLast hooks
    project.afterEvaluate({

      EnhancePluginExtension extension = project.extensions.findByType(EnhancePluginExtension)
      logger.info("EnhancePlugin apply")

      if (extension.queryBeans) {
        hookQueryBeans(project, extension)
      }

      def tasks = project.tasks
      // processResources task must be run before compileJava so ebean.mf to be in place. Same is valid for tests
      tasks.findByName("compileJava").mustRunAfter(tasks.findByName("processResources"))
      tasks.findByName("compileTestJava").mustRunAfter(tasks.findByName("processTestResources"))
      supportedCompilerTasks.each { compileTask ->
        tryHookCompilerTask(tasks, compileTask, project, params)
      }

      def testTask = project.tasks.getByName('test')
      testTask.doFirst {
        logger.debug("enhancement prior to running tests")
        enhanceDirectory(project, extension, "$project.buildDir/classes/main/")
        enhanceDirectory(project, extension, "$project.buildDir/kotlin-classes/main/")
        enhanceDirectory(project, extension, "$project.buildDir/classes/test/")
      }
    })
  }

  /**
   * Hook up APT querybean generation.
   */
  private void hookQueryBeans(Project project, EnhancePluginExtension params) {
    logger.info("add querybean-generator")

    def deps = project.dependencies
    if (params.kotlin) {
      // add needed dependencies for apt processing
      deps.add('kapt', "io.ebean:kotlin-querybean-generator:$params.generatorVersion")
      deps.add('kapt', "io.ebean:ebean-querybean:11.24.1")
      deps.add('kapt', "io.ebean:persistence-api:2.2.1")

    } else {
      // add needed dependencies for apt processing
      deps.add('apt', "io.ebean:querybean-generator:$params.generatorVersion")
      deps.add('apt', "io.ebean:ebean-querybean:11.24.1")
      deps.add('apt', "io.ebean:persistence-api:2.2.1")
    }

    String genDir = "$project.projectDir/generated"

    if (params.kotlin) {

    } else {
      Closure cl = { AbstractCompile task ->
        if (!(task.name in ['compileJava', 'compileGroovy'])) {
          return
        }

        task.options.annotationProcessorPath = project.configurations.apt
        task.options.compilerArgs << "-s"
        task.options.compilerArgs << genDir

        task.doFirst {
          new File(project.projectDir, '/generated').mkdirs()
        }
      }

      [JavaCompile, GroovyCompile].each { Class type ->
        project.tasks.withType(type, cl)
      }
    }

    SourceSetContainer sourceSets = (SourceSetContainer)project.getProperties().get("sourceSets")
    createSourceSet(project, "generated", genDir, sourceSets.main.runtimeClasspath)
  }

  /**
   * Create sourceSet for generated source directory.
   */
  SourceSet createSourceSet(Project project, String name, String outputDir, Object cp) {

    JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention)
    SourceSetContainer sourceSets = javaConvention.sourceSets
    sourceSets.create(name) {
      output.dir(outputDir)
      runtimeClasspath = cp
    }
  }

  private void tryHookCompilerTask(TaskContainer tasks, String taskName, Project project, EnhancePluginExtension params) {
    try {
      def task = tasks.getByName(taskName)

      task.doLast({ completedTask ->
        recordTaskOutputs(completedTask.outputs, project, taskName)
        enhanceTaskOutputs(project, params, taskName)
      })
    } catch (UnknownTaskException e) {
      logger.debug("Ignore as compiler task is not activated " + e.message)
    }
  }

  /**
   * Record and collect the output directories for use with enhancement later.
   */
  private void recordTaskOutputs(TaskOutputs taskOutputs, Project project, String taskName) {

    Set<File> projectOutputDirs
    if (taskName.toLowerCase(Locale.US).contains("test")) {
      projectOutputDirs = testOutputDirs.computeIfAbsent(project, { new HashSet<File>() })
    } else {
      projectOutputDirs = outputDirs.computeIfAbsent(project, { new HashSet<File>() })
    }
    taskOutputs.files.each { taskOutputDir ->
      if (taskOutputDir.isDirectory()) {
        projectOutputDirs.add(taskOutputDir)
      } else {
        logger.error("$taskOutputDir is not a directory")
      }
    }
  }

  /**
   * Perform the enhancement for the classes and testClasses tasks only (otherwise skip).
   */
  private void enhanceTaskOutputs(Project project, EnhancePluginExtension params, String taskName) {

    Set<File> projectOutputDirs
    switch (taskName) {
      case "classes":
        projectOutputDirs = outputDirs.computeIfAbsent(project, { new HashSet<File>() })
        break
      case "testClasses":
        projectOutputDirs = testOutputDirs.computeIfAbsent(project, { new HashSet<File>() })
        break
      default:
        return
    }

    logger.debug("perform enhancement for task: $taskName")
    List<URL> urls = createClassPath(project)
    def cxtLoader = Thread.currentThread().getContextClassLoader()

    projectOutputDirs.each { urls.add(it.toURI().toURL()) }
    projectOutputDirs.each { outputDir ->
      // also add outputDir to the classpath
      def output = outputDir.toPath()
      urls.add(output.toUri().toURL())
      def urlsArray = urls.toArray(new URL[urls.size()])
      new EbeanEnhancer(output, urlsArray, cxtLoader, params).enhance()
    }
  }

  private void enhanceDirectory(Project project, EnhancePluginExtension params, String outputDir) {

    File outDir = new File(outputDir)
    if (!outDir.exists()) {
      return
    }

    List<URL> urls = createClassPath(project)

    def cxtLoader = Thread.currentThread().getContextClassLoader()
    def path = outDir.toPath()

    def urlsArray = urls.toArray(new URL[urls.size()])
    new EbeanEnhancer(path, urlsArray, cxtLoader, params).enhance()
  }


  private List<URL> createClassPath(Project project) {

    Set<File> compCP = project.configurations.getByName("compileClasspath").resolve()
    List<URL> urls = compCP.collect { it.toURI().toURL() }

    FileCollection outDirs = project.sourceSets.main.output.classesDirs
    outDirs.each { outputDir ->
      addToClassPath(urls, outputDir)
      addToClassPath(urls, outputDir)
    }

    File kotlinMain = new File(project.buildDir, "kotlin-classes/main")
    if (kotlinMain.exists() && kotlinMain.isDirectory()) {
      urls.add(kotlinMain.toURI().toURL())
    }
    return urls
  }

  static void addToClassPath(List<URL> urls, File file) {
    urls.add(file.toURI().toURL())
  }
}
