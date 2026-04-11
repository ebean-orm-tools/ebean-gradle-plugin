package io.ebean.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

class EnhancePlugin implements Plugin<Project> {

  private final Logger logger = Logging.getLogger(EnhancePlugin.class)

  void apply(Project project) {
    EnhancePluginExtension params = project.extensions.create('ebean', EnhancePluginExtension)

    project.afterEvaluate({
      logger.info("EnhancePlugin apply")

      SourceSetContainer sourceSets = project.extensions.findByName('sourceSets') as SourceSetContainer
      if (sourceSets == null) {
        return
      }

      registerEnhanceTask(project, sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME), params)
      registerEnhanceTask(project, sourceSets.findByName(SourceSet.TEST_SOURCE_SET_NAME), params)
      registerEnhanceTask(project, sourceSets.findByName('testFixtures'), params)
    })
  }

  private void registerEnhanceTask(Project project, SourceSet sourceSet, EnhancePluginExtension params) {
    if (sourceSet == null) {
      return
    }

    def lifecycleTask = project.tasks.findByName(sourceSet.classesTaskName)
    if (lifecycleTask == null) {
      return
    }

    def enhanceTaskName = sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME ? 'ebeanEnhance' : "ebeanEnhance${sourceSet.name.capitalize()}"
    def enhanceTask = project.tasks.register(enhanceTaskName, EbeanEnhanceTask) { task ->
      task.group = 'ebean'
      task.description = "Enhances Ebean classes for the ${sourceSet.name} source set."
      task.debugLevel.set(params.debugLevel)
      task.classpathFiles.from(sourceSet.compileClasspath)
      task.classpathFiles.from(sourceSet.output)
      task.classesDirs.from(sourceSet.output.classesDirs)
      task.dependsOn(lifecycleTask)
    }

    def jarTaskName = sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME ? 'jar' : sourceSet.name == 'testFixtures' ? 'testFixturesJar' : null
    if (jarTaskName != null) {
      project.tasks.matching { it.name == jarTaskName }.configureEach {
        dependsOn(enhanceTask)
      }
    }

    project.tasks.withType(Test).configureEach {
      dependsOn(enhanceTask)
    }
  }
}
