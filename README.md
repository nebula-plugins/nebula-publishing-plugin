# Nebula Publishing Plugin

[![Build Status](https://travis-ci.org/nebula-plugins/nebula-publishing-plugin.svg?branch=master)](https://travis-ci.org/nebula-plugins/nebula-publishing-plugin)
[![Coverage Status](https://coveralls.io/repos/nebula-plugins/nebula-publishing-plugin/badge.svg?branch=master&service=github)](https://coveralls.io/github/nebula-plugins/nebula-publishing-plugin?branch=master)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nebula-plugins/nebula-publishing-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/nebula-publishing-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

## Usage

To apply this plugin if using Gradle 2.1 or newer

    plugins {
      id 'nebula.<publishing plugin of your choice>' version '3.0.3'
    }

If using an older version of Gradle

    buildscript {
      repositories { jcenter() }
      dependencies {
        classpath 'com.netflix.nebula:nebula-publishing-plugin:3.0.3'
      }
    }

    apply plugin: 'nebula.<publishing plugin of your choice>'


Provides publishing related plugins to reduce boiler plate and add functionality to maven-publish/ivy-publish. Current plugins:

* 'nebula-publishing' - Clean up maven output
* DEPRECATED: 'nebula-source-jar' - Creates a sources jar, that contains the source files. Replace with 'nebula.source-jar'.
* DEPRECATED: 'nebula-javadoc-jar' - Create a javadoc jar, that contains the html files from javadoc. Replace with 'nebula.javadoc-jar'.
* DEPRECATED: 'nebula-test-jar' - Creates a jar containing test classes, and a "test" configuration that other projects can depend on. Occasionally projects may want to depend on test fixtures from other modules. To add a dependency on this you will add `testCompile project(path: ':<project>', configuration: 'test')` to the `dependencies` block. Replace with 'nebula.test-jar'.

* nebula.apache-license-pom - adds the Apache v2 license to the pom
* nebula.javadoc-jar - adds a javadoc jar publication
* nebula.manifest-pom - adds various manifest values to the properties block of the pom
* nebula.maven-base-publishing - sets up basic publication used by other plugins
* nebula.maven-java-publishing - adds in a default of publishing components.java and detection of war projects components.web
* nebula.maven-publishing - apply if you want all of our opinions nebula.maven-base-publishing, nebula.maven-java-publishing, nebula.manifest-pom, nebula.resolved-pom, and nebula.scm-pom
* nebula.resolved-pom - changes all dynamic versions to resolved versions so 3.x or [2.0.0, 3.0.0) will be resolved to specific versions
* nebula.scm-pom - adds SCM information to the pom
* nebula.source-jar - adds a source jar publication
* nebula.test-jar - adds a test jar publication

## Maven Related Publishing Plugins

### nebula.maven-base-publishing

Create a maven publication named nebula. All of the maven based publishing plugins will interact with this publication.
All of the other maven based plugins will also automatically apply this so most users will not need to.

Eliminates this boilerplate:

    apply plugin: 'maven-publish'
                   
    publishing {
      publications {
        nebula(MavenPublication) {
        }
      }
    }

### nebula.maven-java-publishing

In an after evaluate block we detect if the war plugin is applied and publish the web component, it will default to publishing the java component.

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

### nebula.maven-publishing

Link all the other maven plugins together.

### nebula.manifest-pom

Adds a properties block to the pom. Copying in data from our [gradle-info-plugin](https://github.com/nebula-plugins/gradle-info-plugin).

### nebula.resolved-pom

Walk through all project dependencies and replace all dynamic dependencies with what those jars currently resolve to.

### nebula.scm-pom

Adds scm block to the pom. Tries to use the the info-scm plugin from [gradle-info-plugin](https://github.com/nebula-plugins/gradle-info-plugin) 

## Extra Publication Plugins

### nebula.javadoc-jar

Creates a javadocJar task to package up javadoc and add it to publications so it is published.

Eliminates this boilerplate:

    tasks.create('javadocJar', Jar) {
      dependsOn tasks.javadoc
      from tasks.javadoc.destinationDir
      classifier 'javadoc'
      extension 'jar'
      group 'build'
    }
    publishing {
      publications {
        nebula(MavenPublication) {
          artifact tasks.javadocJar
        }
      }
    }

### nebula.source-jar

Creates a sourceJar tasks to package up the the source code of your project and add it to publications so it is published.

Eliminates this boilerplate:

    tasks.create('sourceJar', Jar) {
        dependsOn tasks.classes
        from sourceSets.main.allSource
        classifier 'sources'
        extension 'jar'
        group 'build'
    }
    publishing {
      publications {
        nebula(MavenPublication) {
          artifact tasks.sourceJar
        }
      }
    }

### nebula.test-jar

Eliminates this boilerplate:

    tasks.create('testJar', Jar) {
      dependsOn tasks.testClasses
      classifier = 'tests'
      extension = 'jar'
      from sourceSets.test.output
      group 'build'
    }
    publishing {
      publications {
        nebula(MavenPublication) {
          artifact project.tasks.testJar

          pom.withXml { XmlProvider xml ->
            // code to add dependencies into the pom under the test scope see source code nebula.plugin.publishing.maven.
          }
        }
      }
    }
    
Gradle Compatibility Tested
---------------------------

| Gradle Version | Works |
| -------------- | ----- |
| 2.2.1          | yes   |
| 2.3            | ??    |
| 2.4            | yes   |
| 2.5            | ??    |
| 2.6            | ??    |

LICENSE
=======

Copyright 2014-2015 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
