package io.ebean.gradle.util


class ClassNameMatcher {

    private final String pattern

    ClassNameMatcher(String pattern) {
        this.pattern = pattern.replace('.','/')
    }

    boolean matches(String className) {
        className.startsWith(pattern) || className.endsWith(pattern)
    }
}
