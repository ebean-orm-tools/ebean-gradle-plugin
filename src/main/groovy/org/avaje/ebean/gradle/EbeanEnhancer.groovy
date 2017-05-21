package org.avaje.ebean.gradle

import io.ebean.enhance.agent.Transformer
import io.ebean.typequery.agent.CombinedTransform
import io.ebean.typequery.agent.QueryBeanTransformer
import java.lang.instrument.IllegalClassFormatException
import java.nio.file.Path

import org.avaje.ebean.gradle.util.ClassUtils
import org.avaje.ebean.gradle.util.EnhancementFileFilter
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import io.ebean.enhance.agent.InputStreamTransform

class EbeanEnhancer {

    private final Logger logger = Logging.getLogger( EnhancePlugin.class );

    /**
     * Directory containing .class files.
     */
    private final Path outputDir

    private final FileFilter fileFilter

    private final CombinedTransform combinedTransform;

    private final ClassLoader classLoader;

    EbeanEnhancer(Path outputDir, URL[] extraClassPath, ClassLoader classLoader, EnhancePluginExtension params) {

		logger.info('Calling enhancer' + outputDir + ':' + extraClassPath)
        this.outputDir = outputDir
        this.fileFilter = new EnhancementFileFilter(outputDir, params.packages)
        this.classLoader = new URLClassLoader(extraClassPath, classLoader);

        def args = "debug=" + params.debugLevel;

        def packages = new HashSet<String>()
        packages.addAll(params.packages)

        def queryBeanTransformer = new QueryBeanTransformer(args, classLoader, packages);
        def transformer = new Transformer(extraClassPath, args);

        this.combinedTransform = new CombinedTransform(transformer, queryBeanTransformer);
    }

    void enhance() {

        collectClassFiles(outputDir.toFile()).each { classFile ->
			logger.info('Filter classFile: ' + classFile)
            if (fileFilter.accept(classFile)) {
				logger.info('Enhancing: ' + classFile)
			    enhanceClassFile(classFile);
            }
        }
    }

    private void enhanceClassFile(File classFile) {

        if (logger.isTraceEnabled()) {
            logger.trace("trying to enhance $classFile.absolutePath")
        }

        def className = ClassUtils.makeClassName(outputDir, classFile);

        if (
            className.contains('$$anonfun$') ||     //scala lambda: anonymous function
            className.contains('$_')                //groovy meta info classes & closures
        ) return

        try {
            classFile.withInputStream { classInputStream ->

                def classBytes = InputStreamTransform.readBytes(classInputStream)
				
				// Make sure to close the stream otherwise classFile.delete() returns false on Windows
				classInputStream.close();
				
                CombinedTransform.Response response = combinedTransform.transform(classLoader, className, null, null, classBytes)

                if (response.isEnhanced()) {
                    try {
                        if (!classFile.delete()) {
                            logger.error("Failed to delete enhanced file at $classFile.absolutePath")
                        } else {
                            InputStreamTransform.writeBytes(response.classBytes, classFile)
                        }

                    } catch (IOException e) {
                        throw new EnhanceException("Unable to store фт enhanced class data back to file $classFile.name", e);
                    }
                }
            }
        } catch (IOException e) {
            throw new EnhanceException("Unable to read class file $classFile.name for enhancement", e);
        } catch (IllegalClassFormatException e) {
            throw new EnhanceException("Unable to parse class file $classFile.name while enhance", e);
        }
    }

    private static List<File> collectClassFiles(File dir) {

        List<File> classFiles = new ArrayList<>();

        dir.listFiles().each { file ->
            if (file.directory) {
                classFiles.addAll(collectClassFiles(file));
            } else {
                if (file.name.endsWith(".class")) {
                    classFiles.add(file);
                }
            }
        }

        classFiles
    }
}

