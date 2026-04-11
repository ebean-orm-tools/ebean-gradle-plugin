package io.ebean.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.After
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

class EnhancePluginFunctionalTest {

  private File testProjectDir

  @After
  void cleanup() {
    if (testProjectDir != null) {
      testProjectDir.deleteDir()
    }
  }

  @Test
  void storesAndReusesConfigurationCacheForTestTask() {
    writeProject()

    def first = runner('test', '--configuration-cache', '--stacktrace').build()
    assertTrue(first.output.contains('Configuration cache entry stored'))

    def second = runner('test', '--configuration-cache', '--stacktrace').build()
    assertTrue(second.output.contains('Reusing configuration cache.'))
  }

  @Test
  void enhancesTestFixturesBeforePackagingThem() {
    writeProject()

    def result = runner('verifyEnhancedTestFixturesJar', '--stacktrace').build()
    assertEquals(SUCCESS, result.task(':verifyEnhancedTestFixturesJar').outcome)
    assertTrue(result.output.contains('Verified enhancement in test fixtures jar'))
  }

  private GradleRunner runner(String... arguments) {
    GradleRunner.create()
      .withProjectDir(testProjectDir)
      .withArguments(arguments)
      .withPluginClasspath()
      .withGradleVersion('9.4.1')
  }

  private void writeProject() {
    testProjectDir = File.createTempDir('ebean-gradle-plugin', 'functional-test')

    new File(testProjectDir, 'settings.gradle') << "rootProject.name = 'sample'\n"

    new File(testProjectDir, 'build.gradle') << '''
plugins {
  id 'java-library'
  id 'java-test-fixtures'
  id 'io.ebean'
}

repositories {
  mavenCentral()
}

dependencies {
  implementation 'io.ebean:ebean:17.4.0'
  implementation 'jakarta.persistence:jakarta.persistence-api:3.1.0'
  testFixturesImplementation 'io.ebean:ebean:17.4.0'
  testFixturesImplementation 'jakarta.persistence:jakarta.persistence-api:3.1.0'
  testImplementation 'junit:junit:4.13.2'
}

tasks.register('verifyEnhancedTestFixturesJar') {
  dependsOn tasks.named('testFixturesJar')
  doLast {
    def jarFile = tasks.named('testFixturesJar').get().archiveFile.get().asFile
    def urls = (configurations.testFixturesRuntimeClasspath.files + [jarFile]).collect { it.toURI().toURL() } as URL[]
    URLClassLoader loader = new URLClassLoader(urls, (ClassLoader) null)
    try {
      def clazz = loader.loadClass('sample.FixtureEntity')
      def entityBeanType = loader.loadClass('io.ebean.bean.EntityBean')
      assert entityBeanType.isAssignableFrom(clazz)
      println 'Verified enhancement in test fixtures jar'
    } finally {
      loader.close()
    }
  }
}
'''

    writeSource('src/main/java/sample/MainEntity.java', '''
package sample;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class MainEntity {
  @Id
  Long id;
}
''')

    writeSource('src/testFixtures/java/sample/FixtureEntity.java', '''
package sample;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FixtureEntity {
  @Id
  Long id;
}
''')

    writeSource('src/test/java/sample/SampleTest.java', '''
package sample;

import org.junit.Test;

public class SampleTest {
  @Test
  public void passes() {
  }
}
''')
  }

  private void writeSource(String relativePath, String content) {
    File file = new File(testProjectDir, relativePath)
    file.parentFile.mkdirs()
    file.text = content.trim() + '\n'
  }
}