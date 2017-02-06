package io.ebean.gradle.util

import java.nio.file.Path

class EnhancementFileFilter implements FileFilter {

    private FileFilter includeFilter = { file -> true }

    EnhancementFileFilter(Path outputDir, String[] packages) {
        if (packages.length > 0) {
            includeFilter = new ClassFileFilter(outputDir, packages)
        }
    }

    @Override
    boolean accept(File pathname) {
        return includeFilter.accept(pathname)
    }
}



