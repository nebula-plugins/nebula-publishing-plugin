# Nebula Publishing Plugin

![Support Status](https://img.shields.io/badge/nebula-active-green.svg)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com.netflix.nebula/nebula-publishing-plugin/maven-metadata.xml.svg?label=gradlePluginPortal)](https://plugins.gradle.org/plugin/nebula.maven-publish)
[![Maven Central](https://img.shields.io/maven-central/v/com.netflix.nebula/nebula-publishing-plugin)](https://maven-badges.herokuapp.com/maven-central/com.netflix.nebula/nebula-publishing-plugin)
![Build](https://github.com/nebula-plugins/nebula-publishing-plugin/actions/workflows/nebula.yml/badge.svg)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/nebula-publishing-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

## Usage

__WARNING: Version 14.x.x requires at least Gradle 5.4__

__WARNING: Version 17.x.x requires at least Gradle 6.0 or newer for sources and javadoc plugins__

__WARNING: Version 19.x.x requires at least Gradle 8.0 or newer for sources and javadoc plugins__

To apply this plugin if using plugins block

    plugins {
      id 'com.netflix.nebula.<publishing plugin of your choice>' version '20.2.0'
    }

If using an older version of Gradle

    buildscript {
      repositories { mavenCentral() }
      dependencies {
        classpath 'com.netflix.nebula:nebula-publishing-plugin:20.2.0'
      }
    }

    apply plugin: 'com.netflix.nebula.<publishing plugin of your choice>'


Provides publishing related plugins to reduce boiler plate and add functionality to maven-publish/ivy-publish.

## Maven Related Publishing Plugins

### nebula.maven-base-publish

Create a maven publication named nebula. This named container will be used to generate task names as described in 
[the Maven Publish documentation on gradle.org](https://docs.gradle.org/current/userguide/publishing_maven.html). Examples 
include `publishNebulaPublicationToMavenLocalRepository` and `publishNebulaPublicationToMyArtifactoryRepository`. 

All of the maven based publishing plugins will interact with this publication. All of the other maven based plugins will
also automatically apply this so most users will not need to.

Eliminates this boilerplate:

    apply plugin: 'maven-publish'
                   
    publishing {
      publications {
        nebula(MavenPublication) {
        }
      }
    }

### nebula.maven-dependencies

We detect if the war plugin is applied and publish the web component, it will default to publishing the java component.

Applying this plugin would be the same as:

if war detected

    publishing {
      publications {
        nebula(MavenPublication) {
          from components.web
          // code to append dependencies since they are useful information
        }
      }
    }

if war not detected

    publishing {
      publications {
        nebula(MavenPublication) {
          from components.java
        }
      }
    }
    
Note that nebula.maven-dependencies is based on the Gradle maven-publish plugin, which places first order compile dependencies in the runtime scope in the POM.  If you wish your compile dependencies to be compile scope dependencies in the POM, you can add a withXml block like so:

    publishing {
        publications {
            nebula(MavenPublication) {
                pom.withXml {
                    configurations.compile.resolvedConfiguration.firstLevelModuleDependencies.each { dep ->
                        asNode().dependencies[0].dependency.find {
                            it.artifactId[0].text() == dep.moduleName &&
                            it.groupId[0].text() == dep.moduleGroup
                        }?.scope[0]?.value = 'compile'
                    }
                }
            }
        }
    }

### Shadow Jar Publishing Support

When using the [Gradle Shadow plugin](https://gradleup.com/shadow/) to create fat/uber JARs, `nebula-publishing-plugin` can automatically use the shadow component for proper Maven publishing. This feature is controlled by a feature flag and is **opt-in** for backward compatibility.

#### Enabling Shadow Component Detection

Add to your `gradle.properties`:

```properties
nebula.publishing.features.detectShadowComponent.enabled=true
```

Or enable via command line:

```bash
./gradlew publish -Pnebula.publishing.features.detectShadowComponent.enabled=true
```

#### How It Works

When the feature flag is enabled and a shadow plugin is detected (either `com.gradleup.shadow` or `com.github.johnrengelman.shadow`), the plugin will use `components.shadow` instead of `components.java` for publication. This ensures proper handling of dependencies:

| Dependency Configuration | In Shadow JAR? | In POM? | Use Case |
|-------------------------|---------------|-------|----------|
| `implementation` (with relocation) | Yes (relocated) |  No | Bundled/shaded dependencies |
| `shadow` |  No |  Yes | External runtime dependencies |

#### Example

```gradle
plugins {
    id 'com.netflix.nebula.maven-publish'
    id 'com.gradleup.shadow' version '8.3.7'
    id 'java'
}

dependencies {
    implementation 'com.google.guava:guava:32.0.0'  // Will be bundled & relocated
    shadow 'commons-io:commons-io:2.11.0'           // External (appears in POM)
}

shadowJar {
    archiveClassifier.set(null)  // Replace main jar
    relocate 'com.google.common', 'myapp.shaded.guava'
}
```

**Result:**
- Shadow JAR contains Guava at `myapp/shaded/guava/...` (relocated)
- POM lists `commons-io` as a runtime dependency
- POM does NOT list `guava` (it's bundled in the JAR)

#### Why Use This?

Without shadow component detection:
- All dependencies (including shaded ones) appear in the POM
- Consumers may have dependency conflicts with your shaded libraries
- Doesn't follow Shadow plugin best practices

With shadow component detection:
- Only external dependencies appear in the POM
- Shaded dependencies are excluded from POM (they're already in your JAR)
- Follows [Shadow plugin recommended practices](https://gradleup.com/shadow/publishing/)
- Proper Gradle Module Metadata generation

#### Migration Path

This feature is currently **opt-in** with the feature flag defaulting to `false` to maintain backward compatibility. In a future major version, this may become the default behavior.

### com.netflix.nebula.maven-publish

Link all the other maven plugins together.

### com.netflix.nebula.maven-manifest

Adds a properties block to the pom. Copying in data from our [gradle-info-plugin](https://github.com/nebula-plugins/gradle-info-plugin).

### com.netflix.nebula.maven-resolved-dependencies

Walk through all project dependencies and replace all dynamic dependencies with what those jars currently resolve to. Necessary if [nebula-dependency-recommender-plugin](https://github.com/nebula-plugins/nebula-dependency-recommender-plugin) is used to include version numbers in POM files generated by Gradle's `maven-publish`.

### com.netflix.nebula.maven-scm

Adds scm block to the pom. Tries to use the the info-scm plugin from [gradle-info-plugin](https://github.com/nebula-plugins/gradle-info-plugin) 

### com.netflix.nebula.maven-developer

When [Gradle Contacts](https://github.com/nebula-plugins/gradle-contacts-plugin) plugin is applied, it will take the configured contacts and add them to the POM file. 

Example, given:

```
 apply plugin: 'com.netflix.nebula.contacts'

contacts {
    'nebula@example.test' {
        moniker 'Example Nebula'
        github 'nebula-plugins'
    }
}
```

resulting POM will be:

```
<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>test.nebula</groupId>
  <artifactId>developerpomtest</artifactId>
  <version>0.1.0</version>
  <packaging>pom</packaging>
  <name>developerpomtest</name>
  <developers>
    <developer>
      <id>nebula-plugins</id>
      <name>Example Nebula</name>
      <email>nebula@example.test</email>
    </developer>
  </developers>
</project>
```

## Ivy Related Publishing Plugins

### com.netflix.nebula.ivy-base-publish

Create an ivy publication named nebulaIvy. This named container will be used to generate task names as described in 
[the Ivy Publish documentation on gradle.org](https://docs.gradle.org/current/userguide/publishing_ivy.html). Examples 
include `publishNebulaIvyPublicationToMyLocalIvyRepository` and `publishNebulaIvyPublicationToMyArtifactoryRepository`. 

All of the ivy based publishing plugins will interact with this publication. All of the other ivy based plugins will
also automatically apply this so most users will not need to.

Eliminates this boilerplate:

    apply plugin: 'ivy-publish'
                   
    publishing {
      publications {
        nebulaIvy(IvyPublication) {
        }
      }
    }
    
### com.netflix.nebula.ivy-dependencies

We detect if the war plugin is applied and publish the web component, it will default to publishing the java component.

Applying this plugin would be the same as:

if war detected

    publishing {
      publications {
        nebulaIvy(IvyPublication) {
          from components.web
          // code to append dependencies since they are useful information
        }
      }
    }

if war not detected

    publishing {
      publications {
        nebulaIvy(IvyPublication) {
          from components.java
        }
      }
    }

### com.netflix.nebula.ivy-publish

Link all the other ivy plugins together.

### com.netflix.nebula.ivy-manifest

Adds a properties block to the ivy.xml. Copying in data from our [gradle-info-plugin](https://github.com/nebula-plugins/gradle-info-plugin).

### com.netflix.nebula.ivy-resolved

Walk through all project dependencies and replace all dynamic dependencies with what those jars currently resolve to.

## Extra Publication Plugins

### com.netflix.nebula.javadoc-jar

Creates a javadocJar task to package up javadoc and add it to publications so it is published.

Eliminates this boilerplate:

    tasks.create('javadocJar', Jar) {
      dependsOn tasks.javadoc
      from tasks.javadoc.destinationDir
      archiveClassifier 'javadoc'
      archiveExtension 'jar'
      group 'build'
    }
    publishing {
      publications {
        nebula(MavenPublication) { // if maven-publish is applied
          artifact tasks.javadocJar
        }
        nebulaIvy(IvyPublication) { // if ivy-publish is applied
          artifact tasks.javadocJar
        }
      }
    }

### com.netflix.nebula.source-jar

Creates a sourceJar tasks to package up the the source code of your project and add it to publications so it is published.

Eliminates this boilerplate:

    tasks.create('sourceJar', Jar) {
        dependsOn tasks.classes
        from sourceSets.main.allSource
        archiveClassifier 'sources'
        archiveExtension 'jar'
        group 'build'
    }
    publishing {
      publications {
        nebula(MavenPublication) { // if maven-publish is applied
          artifact tasks.sourceJar
        }
        nebulaIvy(IvyPublication) { // if ivy-publish is applied
          artifact tasks.sourceJar
        }
      }
    }
    
### com.netflix.nebula.publish-verification

Plugin features are enabled only for Gradle 4.8 and higher.
Creates a task which runs before actual publication into repositories. It catches some known bad patters so you can make explicit decision dependency by dependency.

#### Usage

This plugin is NOT automatically applied with `nebula.ivy-publish` or `nebula.maven-publish`. You have to apply the plugin to all modules within the project.

    allprojects {
        apply plugin: 'com.netflix.nebula.publish-verification'
    }

Plugin is integrated with Gradle publishing and with Artifactory plugin. The task itself is a dependence of tasks with type `PublishToIvyRepository` or `PublishToMavenRepository`. The task will also get hooked to tasks named 
`artifactoryPublish` and `artifactoryDeploy` coming from `com.jfrog.artifactory` plugin. If you need any other integration
you have to manually configure relationship in your build file.

##### Your dependency has a lower status then your project violation

###### Explanation

When you are publishing a release build and your first level dependency (directly specified in your build file) is with lower status like `candidate` (e.g. `1.2.0-rc.2` or `latest.candidate`) or `snapshot` (e.g. `1.2.0-SNAPSHOT` or `latest.snapshot`) your build will fail. The reason for this check is a protection of your consumers. As a library producer, you could easily introduce lower status of dependencies in your consumer dependency graph. We consider that dependency with lower status means also lower or not completely guaranteed quality. This verification will explicitly highlight such dependencies and you can make an appropriate decision how to handle them instead of silently publishing.

###### How to resolve

The solution could be slightly different depending on your situation:
* Concrete violating version `1.2.0-rc.2` could be replaced with stable version `1.2.0`
* `latest.candidate` can be replaced with `latest.release`
* Open range version `1.+` can currently also resolve to `1.5-SNAPSHOT`, then you can switch to a preferred fixed version.
* You might be having the right version in your file but some of your dependencies are depending on a lower status version which will win conflict resolution. The best is to fix your downstream library not depend on the lower status library.
* It can be a completely valid situation, that you depend on a candidate. Then you can ignore given library from this verification.

##### Your dependency has incorrect subversion definition

###### Explanation

An incorrect version definition is e.g. 1.1+. It would resolve to 1.1, 1.10, 1.11 etc. but it would miss versions like 1.2, 1.3 etc. This is not what most people would want. We are detecting this situation because it is hard to spot and it leads to unexpected dependency resolutions which are confusing build owners.

###### How to resolve

The right definition is 1.+ to use the highest version with major version 1 or you can use [1.1,] to use version 1.1 or higher.

#### How to ignore selected dependencies.

It might happen that you need to create a release which has to depend on a library which has a lower status. E.g. it
contains critical bug fix or you are early adopter of release candidates. You can exclude those libraries from the 
checking process.

    dependencies {
      implementation 'group:this_will_be_checked:1.0'
      implementation nebulaPublishVerification.ignore('foo:bar:1.0-SNAPSHOT')
      implementation nebulaPublishVerification.ignore(group: 'baz', name: 'bax', version: '1.0-SNAPSHOT')
    }
    
Or you can use extension for this plugin which allows you exclude not just single artifacts but whole groups.

    nebulaPublishVerification {
        ignore('foo:bar:1.0-SNAPSHOT')
        ignoreGroup 'com.company.foo.group'
    }    
    
Gradle Compatibility Tested
---------------------------

Built with Azul JDK8
Tested with Azul JDK8, JDK11, JDK17

| Gradle Version    | Works |
| 7.0   | yes   |
| 8.0   | yes   |
| 8.1   | yes   |


LICENSE
=======

Copyright 2014-2023 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
