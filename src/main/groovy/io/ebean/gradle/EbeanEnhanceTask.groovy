package io.ebean.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = 'Enhances compiled classes in place')
abstract class EbeanEnhanceTask extends DefaultTask {

  @Classpath
  abstract ConfigurableFileCollection getClasspathFiles()

  @InputFiles
  @SkipWhenEmpty
  @PathSensitive(PathSensitivity.RELATIVE)
  abstract ConfigurableFileCollection getClassesDirs()

  @Input
  abstract Property<Integer> getDebugLevel()

  @TaskAction
  void enhanceClasses() {
    List<URL> baseUrls = classpathFiles.files.collect { it.toURI().toURL() }
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader()
    EnhancePluginExtension params = new EnhancePluginExtension(debugLevel: debugLevel.get())

    classesDirs.files.findAll { it.exists() }.each { outputDir ->
      List<URL> urls = new ArrayList<>(baseUrls)
      urls.add(outputDir.toURI().toURL())
      new EbeanEnhancer(outputDir.toPath(), urls.toArray(new URL[0]), contextLoader, params).enhance()
    }
  }
}