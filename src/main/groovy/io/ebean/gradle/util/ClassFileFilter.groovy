package io.ebean.gradle.util

import java.nio.file.Path


class ClassFileFilter implements FileFilter {

    private final Path outputDir;
    private final ClassNameMatcher[] matchers;

    ClassFileFilter(Path outputDir, String[] patterns) {
        this.outputDir = outputDir;
        this.matchers = patterns.collect { pattern -> new ClassNameMatcher(pattern) }
    }

    @Override
    boolean accept(File pathname) {
        def className = ClassUtils.makeClassName(outputDir, pathname)
        matchers.any { matcher -> matcher.matches(className) }
    }
}
