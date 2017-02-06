package io.ebean.gradle

/**
 * Configuration options for the Ebean gradle plugin.
 */
class EnhancePluginExtension {

    /**
     * Packages to enhance.
     */
    def String[] packages = []

    /**
     * Ebean enhancer debug level
     */
    def int debugLevel = 0

    /**
     * When true registers KAPT querybean generation (for Kotlin entity beans).
     */
    def boolean addKapt = true

    /**
     * querybean-generator version for use when addKapt is true.
     */
    def String generatorVersion = '8.1.1'
}
