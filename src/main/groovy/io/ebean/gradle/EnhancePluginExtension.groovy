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

  /**
   * Source sets to apply enhancement to. Defaults to main, test, and testFixtures.
   * <p>
   * Override to limit enhancement to specific source sets. For example, set to
   * {@code ['test']} to only enhance the test source set (e.g. when ebean is a
   * test-only dependency).
   */
  List<String> enhanceSourceSets = ['main', 'test', 'testFixtures']
}
