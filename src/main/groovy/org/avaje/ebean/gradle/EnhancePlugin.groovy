package org.avaje.ebean.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskOutputs

class EnhancePlugin implements Plugin<Project> {

  private final Logger logger = Logging.getLogger(EnhancePlugin.class);

  private static
  def supportedCompilerTasks = ['compileKotlinAfterJava', 'compileJava', 'compileKotlin', 'compileGroovy', 'compileScala', 'compileTestJava', 'compileTestKotlin', 'compileTestGroovy']

  void apply(Project project) {

    logger.debug('EnhancePlugin apply ...')

    def params = project.extensions.create("ebean", EnhancePluginExtension)

    if (params.isAddKapt()) {
      logger.debug("add kapt for Ebean querybean-generator using version $params.generatorVersion")

      def deps = project.dependencies

      // add needed dependencies for KAPT processing
      deps.add('kapt', "org.avaje.ebean:ebean-querybean:8.4.1")
      deps.add('kapt', "org.avaje.ebean:querybean-generator:$params.generatorVersion")
      deps.add('kapt', "javax.persistence:persistence-api:1.0")
    }

    // delay the registration of the various compile task.doLast hooks
    project.afterEvaluate({
      def tasks = project.tasks
      supportedCompilerTasks.each { compileTask ->
        tryHookCompilerTask(tasks, compileTask, project, params)
      }
    });
  }

  private void tryHookCompilerTask(TaskContainer tasks, String taskName, Project project, EnhancePluginExtension params) {
    try {
      def task = tasks.getByName(taskName)

      task.doLast({ completedTask ->
        logger.debug("perform enhancement for task: $taskName")
        enhanceTaskOutput(completedTask.outputs, project, params)
      })
    } catch (UnknownTaskException _) {
      ; // ignore as compiler task is not activated
    }
  }

  private void enhanceTaskOutput(TaskOutputs taskOutputs, Project project, EnhancePluginExtension params) {

    Set<File> compCP = project.configurations.getByName("compileClasspath").resolve()
    def urls = compCP.collect { it.toURI().toURL() }

    File kotlinMain = new File(project.buildDir, "kotlin-classes/main")
    if (kotlinMain.exists() && kotlinMain.isDirectory()) {
      urls.add(kotlinMain.toURI().toURL())
    }

    def cxtLoader = Thread.currentThread().getContextClassLoader()

    taskOutputs.files.each { outputDir ->
      if (outputDir.isDirectory()) {

        def output = outputDir.toPath()

        // also add outputDir to the classpath
        def outputUrl = output.toUri().toURL()
        urls.add(outputUrl)

        if (logger.isTraceEnabled()) {
          logger.trace("classpath urls: ${urls}")
        }

        def urlsArray = urls.toArray(new URL[urls.size()])

        new EbeanEnhancer(output, urlsArray, cxtLoader, params).enhance()
      }
    }
  }
}
