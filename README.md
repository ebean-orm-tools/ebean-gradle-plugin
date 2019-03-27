# Credit

Credit goes to khomich for his work https://github.com/khomich/gradle-ebean-enhancer - this plugin is based off that
(with updated enhancement, kapt support etc).

# Documentation

Refer to http://ebean-orm.github.io/docs/tooling/gradle

# ebean-gradle-plugin
Plugin that performs Enhancement (entity, transactional, query bean) and can generate query beans from entity beans written in Kotlin via kapt.

- Add `ebean-gradle-plugin` to buildscript/dependencies/classpath
- Add `apply plugin: 'io.ebean'`
- Add `generated/source/kapt/main` to sourceSets
- Add kapt generateStubs = true
- Add ebean plugin configuration

## Status

Currently using this with entity beans written in Kotlin (rather than Java).  
Tested with java project with gradle sub projects.

## Example build.gradle (kotlin)

```groovy
group 'org.example'
version '1.0-SNAPSHOT'

buildscript {
    ext.kotlin_version = '1.2.0'
    ext.ebean_version = "11.24.1"
    ext.postgresql_driver_version = "9.4.1207.jre7"

    repositories {
        mavenCentral()
    }
    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
        classpath "io.ebean:ebean-gradle-plugin:11.2.1"
    }
}

apply plugin: 'kotlin'
apply plugin: 'io.ebean'

repositories {
    mavenLocal()
    mavenCentral()
}

sourceSets {
    main.java.srcDirs += [file("$buildDir/generated/source/kapt/main")]
}

dependencies {

    compile "org.postgresql:postgresql:$postgresql_driver_version"
    compile "io.ebean:ebean:$ebean_version"
    compile "io.ebean:ebean-querybean:11.24.1"

    compile "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"

    testCompile "org.avaje.composite:junit:1.1"
}

kapt {
    generateStubs = true
}

ebean {
    debugLevel = 1 //1 - 9
}

test {
    useTestNG()
    testLogging.showStandardStreams = true
    testLogging.exceptionFormat = 'full'
}

```
## Example build.gradle (java)

##### Using the plugins DSL with Gradle >= 4.6:

#### The recommended approach.
1. Use latest version (at a time of writing 11.37.1 or higher)
2. Use Gradle >= 4.6 and do not use external `apt` plugins as Gradle natively supports `annotationProcessor`

The recommended approach allows a single point of control for all processors.
Particularly, the recommended approach allow to control the order 
of execution for processors. For example, if you use framework like Micronaut and Lombok at the same time it is necessary
that Lombok runs before Micronaut processors.
See [in Micronaut docs](https://github.com/micronaut-projects/micronaut-core/blob/master/src/main/docs/guide/languageSupport/java.adoc#using-project-lombok).
The same applies to querybean generation.

__If annotation processor runs behind Gradle ordering - it may lead
to unpredictable results if you rely on annotation processors extensively.__ 
Use recommended approach to avoid such cases.

```groovy
import versions.* //from buildSrc

plugins {
  id("io.ebean") version "<ebean_version>"
}

ebean {
    debugLevel       = 1 //1 - 9
    // these two options no longer needed. Add "ebean-querybean" and "querybean-generator" to annotationProcessor config as below
    // to control generation of query beans and versions of dependencies
//    queryBeans       = true
//    generatorVersion = "<version>"
}

dependencies {
    annotationProcessor("io.ebean:ebean-annotation:4.7")
    annotationProcessor("io.ebean:ebean-querybean:<version>")
    annotationProcessor("io.ebean:persistence-api:2.2.1")
    annotationProcessor("io.ebean:querybean-generator:<version>")
    
    implementation("io.ebean:ebean-annotation:4.7")
    implementation("io.ebean:ebean-querybean:<version>")
    implementation("io.ebean:ebean:<ebean_version>")
    
    testImplementation("io.ebean.test:ebean-test-config:<version>")
}

test {
    testLogging.showStandardStreams = true
}
```

N.B. With Gradle >= 5.2 you can specify output directory for query bean like so
```groovy
compileJava.options.annotationProcessorGeneratedSourcesDirectory = file('generated')

//compileGroovy.options.annotationProcessorGeneratedSourcesDirectory = file('generated')
``` 


For version <= `11.36.1`

```groovy
import versions.* //from buildSrc

plugins {
  id "io.ebean" version "<ebean_version>"
}

ebean {
    debugLevel       = 1 //1 - 9
    queryBeans       = true
    generatorVersion = Deps.Versions.EBEAN_QUERY_GEN
}

dependencies {
    // annotationProcessor(Deps.Libs.EBEAN_ANNOTATION) for ebean-gradle-plugin <= 11.36.1 - uncomment
    
    implementation(Deps.Libs.EBEAN)
    implementation(Deps.Libs.EBEAN_ANNOTATION)
    implementation(Deps.Libs.EBEAN_QUERY)
    
    testImplementation(Deps.Libs.EBEAN_TEST)
}

test {
    testLogging.showStandardStreams = true
}
```

##### Using the plugins DSL with Gradle version before 4.6:


```groovy
import versions.* //from buildSrc

plugins {
  id("io.ebean") version "<ebean_version>"
}

configurations {
    ebeanApt // for ebean-gradle-plugin <= 11.36.1 - just "apt"
}

ebean {
    debugLevel       = 1 //1 - 9
    queryBeans       = true
    generatorVersion = Deps.Versions.EBEAN_QUERY_GEN
}

dependencies {
    // annotationProcessor(Deps.Libs.EBEAN_ANNOTATION) for ebean-gradle-plugin <= 11.36.1  - uncomment
    
    implementation(Deps.Libs.EBEAN)
    implementation(Deps.Libs.EBEAN_ANNOTATION)
    implementation(Deps.Libs.EBEAN_QUERY)
    
    testImplementation(Deps.Libs.EBEAN_TEST)
}

test {
    testLogging.showStandardStreams = true
}
```

#### Using legacy plugin application:

```groovy
group 'org.example'
version '1.0-SNAPSHOT'

buildscript {

    repositories {
        mavenCentral()
    }
    dependencies {
        classpath "io.ebean:ebean-gradle-plugin:11.36.1"
    }
}

apply plugin: 'io.ebean'

repositories {
    mavenLocal()
    mavenCentral()
}


dependencies {

    compile "io.ebean:ebean:11.36.1"

    compile "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"

    testCompile "org.avaje.composite:junit:1.1"
}

ebean {
    debugLevel = 1 //1 - 9
}


```
