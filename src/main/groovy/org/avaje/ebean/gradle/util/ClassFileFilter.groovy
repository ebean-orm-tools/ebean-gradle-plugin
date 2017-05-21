package org.avaje.ebean.gradle.util

import java.nio.file.Path

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class ClassFileFilter implements FileFilter {
	private final Logger logger = Logging.getLogger( ClassFileFilter.class );
	
    private final Path outputDir;
    private final ClassNameMatcher[] matchers;

    ClassFileFilter(Path outputDir, String[] patterns) {
        this.outputDir = outputDir;
        this.matchers = patterns.collect { pattern -> new ClassNameMatcher(pattern) }
    }

    @Override
    boolean accept(File pathname) {
        def className = ClassUtils.makeClassName(outputDir, pathname)
		
		logger.info('Match className:' + className)
        matchers.any { matcher -> matcher.matches(className) }
    }
}
