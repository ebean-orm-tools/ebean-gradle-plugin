package org.avaje.ebean.gradle

/**
 * Configuration options for the Ebean gradle plugin.
 */
class EnhancePluginExtension {

    /**
     * Packages to enhance.
     */
    String[] packages = []

    /**
     * Ebean enhancer debug level
     */
    int debugLevel = 0

    /**
     * When true registers KAPT querybean generation (for Kotlin entity beans).
     */
    boolean addKapt = true

    /**
     * querybean-generator version for use when addKapt is true.
     */
    String generatorVersion = '8.1.4'
}
