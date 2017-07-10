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
import org.gradle.api.tasks.compile.JavaCompile

class EnhancePlugin implements Plugin<Project> {

  private final Logger logger = Logging.getLogger(EnhancePlugin.class)

  private static def supportedCompilerTasks = [
    'compileKotlinAfterJava',
    'compileJava',
    'processResources',
    'compileKotlin',
    'compileGroovy',
    'copyMainKotlinClasses',
    'classes',
    'testClasses',
    'compileScala',
    'compileTestJava',
    'compileTestKotlin',
    'compileTestGroovy']

  void apply(Project project) {

    def params = project.extensions.create("ebean", EnhancePluginExtension)

    // delay the registration of the various compile task.doLast hooks
    project.afterEvaluate({

      EnhancePluginExtension extension = project.extensions.findByType(EnhancePluginExtension)
      logger.debug("EnhancePlugin apply")

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
        println("enhancement prior to running tests")
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
      deps.add('kapt', "io.ebean:ebean-querybean:10.2.1")
      deps.add('kapt', "io.ebean:persistence-api:2.2.1")

    } else {
      // add needed dependencies for apt processing
      deps.add('apt', "io.ebean:querybean-generator:$params.generatorVersion")
      deps.add('apt', "io.ebean:ebean-querybean:10.2.1")
      deps.add('apt', "io.ebean:persistence-api:2.2.1")
    }

    String genDir = "$project.projectDir/generated"

    if (params.kotlin) {

    } else {
      def cl = { AbstractCompile task ->

        if (task.getName() != 'compileJava') {
          return
        }

        task.options.annotationProcessorPath = project.configurations.apt
        task.options.compilerArgs << "-s"
        task.options.compilerArgs << genDir

        task.doFirst {
          new File(project.projectDir, '/generated').mkdirs()
        }
      }
      project.tasks.withType(JavaCompile, cl)
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
        println("perform enhancement for task: $taskName")
        enhanceTaskOutput(completedTask.outputs, project, params)
      })
    } catch (UnknownTaskException _) {
      // ignore as compiler task is not activated
    }
  }

  private void enhanceTaskOutput(TaskOutputs taskOutputs, Project project, EnhancePluginExtension params) {

    // for debug
//    def files = taskOutputs.getFiles()
//    println ("files are " + files.size())
//    for (File f : files) {
//      println("File $f.absolutePath")
//    }

    List<URL> urls = createClassPath(project)

    def cxtLoader = Thread.currentThread().getContextClassLoader()

    taskOutputs.files.each { outputDir ->
      if (outputDir.isDirectory()) {

        // also add outputDir to the classpath
        def output = outputDir.toPath()
        urls.add(output.toUri().toURL())
        if (logger.isTraceEnabled()) {
          logger.trace("classpath urls: ${urls}")
        }

        println("enhancement classes in $outputDir")

        def urlsArray = urls.toArray(new URL[urls.size()])
        new EbeanEnhancer(output, urlsArray, cxtLoader, params).enhance()
      }else{
        logger.error("$outputDir is not a directory");
      }
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

    println("enhancement classes in $outputDir")

    def urlsArray = urls.toArray(new URL[urls.size()])
    new EbeanEnhancer(path, urlsArray, cxtLoader, params).enhance()
  }


  private List<URL> createClassPath(Project project) {

    Set<File> compCP = project.configurations.getByName("compileClasspath").resolve()
    List<URL> urls = compCP.collect { it.toURI().toURL() }

//    File classesDir = project.sourceSets.main.output.classesDir // Will be removed in Gradle 5.0
//    addToClassPath(urls, classesDir)

    File resourcesDir = project.sourceSets.main.output.resourcesDir
    // Gradle 4.0+
    FileCollection classesDirs = project.sourceSets.main.output.classesDirs
    for (File f : classesDirs) {
      addToClassPath(urls, f)
    }
    addToClassPath(urls, resourcesDir)

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
