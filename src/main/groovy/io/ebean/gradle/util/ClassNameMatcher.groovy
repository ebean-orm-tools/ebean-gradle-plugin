package io.ebean.gradle.util

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class ClassNameMatcher {

  private final static Logger logger = Logging.getLogger(ClassNameMatcher.class)

  private final String pattern

  ClassNameMatcher(String pattern) {
    this.pattern = pattern.replace('*', '')
  }

  boolean matches(String className) {

    boolean b = className.startsWith(pattern) || className.endsWith(pattern)

    if (!b) {
      logger.info('ClassNameMatcher Pattern failed:' + pattern + ' className: ' + className)
    }

    return b
  }
}
