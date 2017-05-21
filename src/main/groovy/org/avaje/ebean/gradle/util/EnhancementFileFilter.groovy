package org.avaje.ebean.gradle.util

import java.nio.file.Path

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class EnhancementFileFilter implements FileFilter {
	private final Logger logger = Logging.getLogger( EnhancementFileFilter.class );
	
    private FileFilter includeFilter = { file -> true }

    EnhancementFileFilter(Path outputDir, String[] packages) {
		logger.info('Enhancment packages:' + packages)
        if (packages.length > 0) {
            includeFilter = new ClassFileFilter(outputDir, packages)
        }
    }

    @Override
    boolean accept(File pathname) {
        return includeFilter.accept(pathname)
    }
}



