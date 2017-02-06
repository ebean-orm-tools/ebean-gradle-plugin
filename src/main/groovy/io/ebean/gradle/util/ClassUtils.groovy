package io.ebean.gradle.util

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Helper to derive the class name.
 */
class ClassUtils {

    /**
     * Return the class name given the base path and file
     */
    static String makeClassName(Path basePath, File classFile) {
        def classRelPath = basePath.relativize(Paths.get(classFile.toURI()))
        classRelPath.toString().replaceAll('[.]class$', '').replace('\\', '.').replace('//', '.')
    }
}
