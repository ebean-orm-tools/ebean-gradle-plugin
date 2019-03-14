package io.ebean.gradle

/**
 * Configuration options for the Ebean gradle plugin.
 */
class EnhancePluginExtension {

  /**
   * Ebean enhancer debug level
   */
  int debugLevel = 0

  /**
   * When true registers Java querybean generation.
   */
  boolean queryBeans = false

  /**
   *
   */
  boolean kotlin = false

  /**
   * querybean-generator version for use when addKapt is true.
   */
  String generatorVersion = '11.36.1'
}
