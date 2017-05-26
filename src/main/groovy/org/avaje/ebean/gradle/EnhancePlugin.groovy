package org.avaje.ebean.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
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
    'compileKotlinAfterJava', 'compileJava', 'compileKotlin', 'compileGroovy',
    'compileScala', 'compileTestJava', 'compileTestKotlin', 'compileTestGroovy']

  void apply(Project project) {

    def params = project.extensions.create("ebean", EnhancePluginExtension)

    // delay the registration of the various compile task.doLast hooks
    project.afterEvaluate({

      def extension = project.extensions.findByType(EnhancePluginExtension)
      logger.debug("EnhancePlugin apply")

      if (extension.queryBeans) {
        hookQueryBeans(project, extension)
      }

      def tasks = project.tasks
      supportedCompilerTasks.each { compileTask ->
        tryHookCompilerTask(tasks, compileTask, project, params)
      }
    })
  }

  /**
   * Hook up APT querybean generation.
   */
  private void hookQueryBeans(Project project, EnhancePluginExtension params) {

    logger.info("add querybean-generator")

    def deps = project.dependencies
    // add needed dependencies for apt processing
    deps.add('apt', "io.ebean:querybean-generator:$params.generatorVersion")
    deps.add('apt', "io.ebean:ebean-querybean:10.2.1")
    deps.add('apt', "io.ebean:persistence-api:2.2.1")

    String genDir = "$project.projectDir/generated/java"

    def cl = { AbstractCompile task ->

      if (task.getName() != 'compileJava') {
        return
      }

      task.options.annotationProcessorPath = project.configurations.apt
      task.options.compilerArgs << "-s"
      task.options.compilerArgs << genDir

      task.doFirst {
        new File(project.projectDir, '/generated/java').mkdirs()
      }
    }
    project.tasks.withType(JavaCompile, cl)

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
        logger.info("perform enhancement for task: $taskName")
        enhanceTaskOutput(completedTask.outputs, project, params)
      })
    } catch (UnknownTaskException _) {
      // ignore as compiler task is not activated
    }
  }

  private void enhanceTaskOutput(TaskOutputs taskOutputs, Project project, EnhancePluginExtension params) {

    Set<File> compCP = project.configurations.getByName("compileClasspath").resolve()
    def urls = compCP.collect { it.toURI().toURL() }

    File classesDir = project.sourceSets.main.output.classesDir
    File resourcesDir = project.sourceSets.main.output.resourcesDir
    addToClassPath(urls, classesDir)
    addToClassPath(urls, resourcesDir)

    File kotlinMain = new File(project.buildDir, "kotlin-classes/main")
    if (kotlinMain.exists() && kotlinMain.isDirectory()) {
      urls.add(kotlinMain.toURI().toURL())
    }

    def cxtLoader = Thread.currentThread().getContextClassLoader()

    taskOutputs.files.each { outputDir ->
      if (outputDir.isDirectory()) {

        // also add outputDir to the classpath
        def output = outputDir.toPath()
        urls.add(output.toUri().toURL())
        if (logger.isTraceEnabled()) {
          logger.trace("classpath urls: ${urls}")
        }

        def urlsArray = urls.toArray(new URL[urls.size()])
        new EbeanEnhancer(output, urlsArray, cxtLoader, params).enhance()
      }
    }
  }

  static void addToClassPath(List<URL> urls, File file) {
    urls.add(file.toURI().toURL())
  }
}
