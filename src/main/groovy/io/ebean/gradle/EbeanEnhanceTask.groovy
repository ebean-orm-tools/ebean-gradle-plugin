package io.ebean.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

@DisableCachingByDefault(because = 'Copies and enhances compiled classes into dedicated output directories')
abstract class EbeanEnhanceTask extends DefaultTask {

  @Classpath
  abstract ConfigurableFileCollection getClasspathFiles()

  @InputFiles
  @SkipWhenEmpty
  @PathSensitive(PathSensitivity.RELATIVE)
  abstract ConfigurableFileCollection getRawClassesDirs()

  @OutputDirectory
  abstract DirectoryProperty getEnhancedClassesDir()

  @Input
  abstract Property<Integer> getDebugLevel()

  @Inject
  abstract FileSystemOperations getFileSystemOperations()

  @TaskAction
  void enhanceClasses() {
    List<File> rawDirs = rawClassesDirs.files.sort { it.absolutePath }
    File enhancedDir = enhancedClassesDir.get().asFile

    fileSystemOperations.delete {
      delete(enhancedDir)
    }

    rawDirs.findAll { it.exists() }.each { File rawDir ->
      fileSystemOperations.copy {
        from(rawDir)
        into(enhancedDir)
      }
    }

    List<URL> baseUrls = classpathFiles.files.collect { it.toURI().toURL() }
    List<File> existingEnhancedDirs = enhancedDir.exists() ? [enhancedDir] : []
    baseUrls.add(enhancedDir.toURI().toURL())
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader()
    EnhancePluginExtension params = new EnhancePluginExtension(debugLevel: debugLevel.get())

    existingEnhancedDirs.each { outputDir ->
      new EbeanEnhancer(outputDir.toPath(), baseUrls.toArray(new URL[0]), contextLoader, params).enhance()
    }
  }
}