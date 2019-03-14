package io.ebean.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
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
  private def outputDirs = new HashMap<Project, Set<File>>().withDefault { [] }

  /**
   * Test output directories containing classes we want to run enhancement on.
   */
  private def testOutputDirs = new HashMap<Project, Set<File>>().withDefault { [] }

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
      initializeOutputDirs(project)
      // processResources task must be run before compileJava so ebean.mf to be in place. Same is valid for tests
      tasks.findByName("compileJava").mustRunAfter(tasks.findByName("processResources"))
      tasks.findByName("compileTestJava").mustRunAfter(tasks.findByName("processTestResources"))
      supportedCompilerTasks.each { compileTask ->
        tryHookCompilerTask(tasks, compileTask, project, params)
      }

      def testTask = project.tasks.getByName('test')
      testTask.doFirst {
        logger.debug("enhancement prior to running tests")

        List<URL> urls = createClassPath(project)
        URL[] classpathUrls = urls.toArray(new URL[urls.size()])

        enhanceDirectory(extension, "$project.buildDir/classes/main/", classpathUrls)
        enhanceDirectory(extension, "$project.buildDir/kotlin-classes/main/", classpathUrls)
        enhanceDirectory(extension, "$project.buildDir/classes/test/", classpathUrls)
      }
    })
  }

  /**
   * Fetch the output directories, containing the classes to enhance, from the project's sources set.
   */
  private void initializeOutputDirs(Project project) {
    project.sourceSets.each { sourceSet ->
      def files = sourceSet.output.classesDirs.files as Set<File>
      testOutputDirs[project] += files.findAll {
        it.name.contains("test")
      }
      outputDirs[project] += files.findAll {
        it.name.contains("main")
      }
    }
    logger.debug("Test output dirs: $testOutputDirs")
    logger.debug("Main output dirs: $outputDirs")
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
      deps.add('kapt', "io.ebean:ebean-querybean:11.34.1")
      deps.add('kapt', "io.ebean:persistence-api:2.2.1")

    } else {
      // add needed dependencies for apt processing
      deps.add('apt', "io.ebean:querybean-generator:$params.generatorVersion")
      deps.add('apt', "io.ebean:ebean-querybean:11.34.1")
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

      SourceSetContainer sourceSets = (SourceSetContainer) project.getProperties().get("sourceSets")
      createSourceSet(project, "generated", genDir, sourceSets.main.runtimeClasspath)
    }

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
        enhanceTaskOutputs(project, params, completedTask)
      })
    } catch (UnknownTaskException e) {
      logger.debug("Ignore as compiler task is not activated " + e.message)
    }
  }

  /**
   * Perform the enhancement for the classes and testClasses tasks only (otherwise skip).
   */
  private void enhanceTaskOutputs(Project project, EnhancePluginExtension params, Task task) {

    Set<File> projectOutputDirs = new HashSet<>()

    if (task instanceof AbstractCompile) {
      projectOutputDirs.addAll(task.outputs.files)
    } else {
      return
    }

    logger.debug("perform enhancement for task: $task")
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

  private void enhanceDirectory(EnhancePluginExtension params, String outputDir, URL[] classpathUrls) {

    File outDir = new File(outputDir)
    if (!outDir.exists()) {
      return
    }

    def cxtLoader = Thread.currentThread().getContextClassLoader()
    new EbeanEnhancer(outDir.toPath(), classpathUrls, cxtLoader, params).enhance()
  }


  private List<URL> createClassPath(Project project) {

    Set<File> compCP = project.configurations.getByName("compileClasspath").resolve()
    List<URL> urls = compCP.collect { it.toURI().toURL() }

    FileCollection outDirs = project.sourceSets.main.output.classesDirs
    outDirs.each { outputDir ->
      addToClassPath(urls, outputDir)
    }

    File resMain = new File(project.buildDir, "resources/main")
    if (resMain.exists() && resMain.isDirectory()) {
      addToClassPath(urls, resMain)
    }

    File kotlinMain = new File(project.buildDir, "kotlin-classes/main")
    if (kotlinMain.exists() && kotlinMain.isDirectory()) {
      addToClassPath(urls, kotlinMain)
    }
    return urls
  }

  static void addToClassPath(List<URL> urls, File file) {
    urls.add(file.toURI().toURL())
  }
}
