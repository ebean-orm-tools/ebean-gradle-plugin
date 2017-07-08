package io.ebean.gradle

import io.ebean.enhance.Transformer
import io.ebean.enhance.common.InputStreamTransform
import io.ebean.gradle.util.ClassUtils
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.lang.instrument.IllegalClassFormatException
import java.nio.file.Path

class EbeanEnhancer {

  private final Logger logger = Logging.getLogger(EnhancePlugin.class)

  /**
   * Directory containing .class files.
   */
  private final Path outputDir

  private final Transformer combinedTransform

  private final ClassLoader classLoader

  EbeanEnhancer(Path outputDir, URL[] extraClassPath, ClassLoader contextLoader, EnhancePluginExtension params) {
    logger.info('Calling enhancer' + outputDir + ':' + extraClassPath)
    this.outputDir = outputDir
    this.classLoader = new URLClassLoader(extraClassPath, contextLoader)

    def args = "debug=" + params.debugLevel
    this.combinedTransform = new Transformer(classLoader, args)
  }

  void enhance() {
    collectClassFiles(outputDir.toFile()).each { classFile ->
      enhanceClassFile(classFile)
    }
  }

  private void enhanceClassFile(File classFile) {
    def className = ClassUtils.makeClassName(outputDir, classFile)
    if (isIgnorableClass(className)) {
      return
    }

    try {
      classFile.withInputStream { classInputStream ->

        def classBytes = InputStreamTransform.readBytes(classInputStream)

        // Make sure to close the stream otherwise classFile.delete() returns false on Windows
        classInputStream.close()

        String jvmClassName = className.replace('.','/')
        logger.info("Try to enhance $jvmClassName");
        byte[] response = combinedTransform.transform(classLoader, jvmClassName, null, null, classBytes)

        if (response != null) {
          try {
            if (!classFile.delete()) {
              logger.error("Failed to delete enhanced file at $classFile.absolutePath")
            } else {
              InputStreamTransform.writeBytes(response, classFile)
            }

          } catch (IOException e) {
            throw new EnhanceException("Unable to store enhanced class data back to file $classFile.name", e)
          }
        }
      }
    } catch (IOException e) {
      throw new EnhanceException("Unable to read class file $classFile.name for enhancement", e)
    } catch (IllegalClassFormatException e) {
      throw new EnhanceException("Unable to parse class file $classFile.name while enhance", e)
    }
  }

  /**
   * Ignore scala lambda anonymous function and groovy meta info classes & closures
   */
  private static boolean isIgnorableClass(String className) {
    return className.contains('$$anonfun$') || className.contains('$_')
  }

  private static List<File> collectClassFiles(File dir) {

    List<File> classFiles = new ArrayList<>()

    dir.listFiles().each { file ->
      if (file.directory) {
        classFiles.addAll(collectClassFiles(file))
      } else {
        if (file.name.endsWith(".class")) {
          classFiles.add(file)
        }
      }
    }

    classFiles
  }
}

