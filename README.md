# Credit

Credit goes to khomich for his work https://github.com/khomich/gradle-ebean-enhancer - this plugin is based off that
(with updated enhancement, kapt support etc).

# ebean-gradle-plugin
Plugin that performs Enhancement (entity, transactional, query bean) and can generate query beans from entity beans written in Kotlin via kapt.

- Add `ebean-gradle-plugin` to buildscript/dependencies/classpath
- Add `apply plugin: 'ebean'`
- Add `generated/source/kapt/main` to sourceSets
- Add kapt generateStubs = true
- Add ebean plugin configuration

## Status

Currently using this with entity beans written in Kotlin (rather than Java).  I need to test the Java use.
There is also an issue with the Ebean IDEA plugin due to the unexpected directory structure (where ebean-typequery.mf goes).
Works with java classes. 


## Example 1 - build.gradle
```groovy

// First install the plugin in your local maven repo with 
// gradle publishToMavenLocal
 
buildscript {
    repositories {
        mavenLocal() 
        mavenCentral()
    }
    dependencies {
        classpath 'io.ebean:ebean-gradle-plugin:0.2.1'
    }
}

group 'info.vnnv'
version '1.0-SNAPSHOT'

apply plugin: 'java'
apply plugin: 'ebean'

sourceCompatibility = 1.8

repositories {
    mavenCentral()
}

ebean {
    debugLevel = 9 //1 - 9
    packages = [''] // fill packages to enhance
    addKapt = false // false if you are not using kotlin
    generatorVersion = '8.1.4' // this is for kotlin
}

dependencies {
    testCompile group: 'junit', name: 'junit', version: '4.12'

    //EBean ORM
    compile "io.ebean:ebean:10.1.4"


}

```


## Example 2 -  build.gradle

```groovy
group 'org.example'
version '1.0-SNAPSHOT'

buildscript {
    ext.kotlin_version = '1.0.4'
    ext.ebean_version = "8.6.2-SNAPSHOT"
    ext.postgresql_driver_version = "9.4.1207.jre7"

    repositories {
        mavenCentral()
    }
    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
        classpath "org.avaje.ebean:ebean-gradle-plugin:0.1.1"
    }
}

apply plugin: 'kotlin'
apply plugin: 'ebean'

repositories {
    mavenLocal()
    mavenCentral()
}

sourceSets {
    main.java.srcDirs += [file("$buildDir/generated/source/kapt/main")]
}

dependencies {

    compile "org.postgresql:postgresql:$postgresql_driver_version"
    compile "org.avaje.ebean:ebean:$ebean_version"
    compile "org.avaje.ebean:ebean-querybean:8.4.1"

    compile "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"

    testCompile "org.avaje.composite:composite-testing:3.1"
}

kapt {
    generateStubs = true
}

ebean {
    debugLevel = 0 //1 - 9
    packages = ['org.example']
}

test {
    useTestNG()
    testLogging.showStandardStreams = true
    testLogging.exceptionFormat = 'full'
}

```
