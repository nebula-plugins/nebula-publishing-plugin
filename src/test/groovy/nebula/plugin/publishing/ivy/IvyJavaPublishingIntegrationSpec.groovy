/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.publishing.ivy

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import spock.lang.Specification

class IvyJavaPublishingIntegrationSpec extends Specification {
    boolean keepFiles = true
    @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
    @Rule TestName testName = new TestName()
    File projectDir
    File buildFile
    File settingsFile
    File publishDir

    def setup() {
        projectDir = new File("build/test/${this.class.canonicalName}/${testName.methodName.replaceAll(/\W+/, '-')}")
        if (projectDir.exists()) {
            projectDir.deleteDir()
        }
        projectDir.mkdirs()
        buildFile = new File(projectDir, 'build.gradle')
        def pluginClasspathResource = this.class.classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
        }

        def pluginClasspath = pluginClasspathResource.readLines()
                .collect { it.replace('\\', '\\\\') } // escape backslashes in Windows paths
                .collect { "'$it'" }
                .join(", ")

        settingsFile = new File(projectDir, 'settings.gradle')
        buildFile << """\
            buildscript {
                dependencies {
                    classpath files($pluginClasspath)
                }
            }

            apply plugin: 'nebula.ivy-java-publishing'

            version = '0.1.0'
            group = 'test.nebula'

            publishing {
                repositories {
                    ivy {
                        name = 'testLocal'
                        url = 'testrepo'
                    }
                }
            }
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'ivytest'
        '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test.nebula/ivytest/0.1.0')
    }

    def cleanup() {
        if (!keepFiles) {
            projectDir.deleteDir()
        }
    }

    def 'creates a jar publication'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        new File(publishDir, 'ivytest-0.1.0.jar').exists()
        new File(publishDir, 'ivy-0.1.0.xml').exists()
    }

    def 'creates a jar publication for scala projects'() {
        buildFile << '''\
            apply plugin: 'scala'
        '''.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        new File(publishDir, 'ivytest-0.1.0.jar').exists()
    }

    def 'creates a jar publication for groovy projects'() {
        buildFile << '''\
            apply plugin: 'groovy'
        '''.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        new File(publishDir, 'ivytest-0.1.0.jar').exists()
    }

    def 'creates a war publication'() {
        buildFile << '''\
            apply plugin: 'war'
        '''.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        new File(publishDir, 'ivytest-0.1.0.war').exists()
        new File(publishDir, 'ivy-0.1.0.xml').exists()
    }

    def 'creates a war publication in presence of java plugin'() {
        buildFile << '''\
            apply plugin: 'java'
            apply plugin: 'war'
        '''.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        new File(publishDir, 'ivytest-0.1.0.war').exists()
        new File(publishDir, 'ivy-0.1.0.xml').exists()
        !new File(publishDir, 'ivytest-0.1.0.jar').exists()
    }

    def 'creates a war publication in presence of java plugin no matter the order'() {
        buildFile << '''\
            apply plugin: 'war'
            apply plugin: 'java'
        '''.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        new File(publishDir, 'ivytest-0.1.0.war').exists()
        new File(publishDir, 'ivy-0.1.0.xml').exists()
        !new File(publishDir, 'ivytest-0.1.0.jar').exists()
    }

    def 'verify ivy.xml is correct'() {
        buildFile << '''\
            apply plugin: 'java'

            description = 'test description'
        '''.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'ivy-0.1.0.xml').text)
        root.info.@organisation == 'test.nebula'
        root.info.@module == 'ivytest'
        root.info.@revision == '0.1.0'
        root.info.description == 'test description'
        def artifact = root.publications.artifact[0]
        artifact.@name == 'ivytest'
        artifact.@type == 'jar'
        artifact.@ext == 'jar'
        artifact.@conf == 'runtime'
    }

    def 'verify ivy.xml contains compile dependencies'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:a:0.0.1').build()
        File ivyrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ivy { url '${ivyrepo.absolutePath}' }
            }

            dependencies {
                compile 'testjava:a:0.0.1'
            }
        """.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'ivy-0.1.0.xml').text)
        def dependency = root.dependencies.dependency[0]
        dependency.@org == 'testjava'
        dependency.@name == 'a'
        dependency.@rev == '0.0.1'
        dependency.@conf == 'runtime->default'
    }

    def 'verify ivy.xml contains compile dependencies for war projects'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:b:0.0.1').build()
        File ivyrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'
            apply plugin: 'war'

            repositories {
                ivy { url '${ivyrepo.absolutePath}' }
            }

            dependencies {
                compile 'testjava:b:0.0.1'
            }
        """.stripIndent()

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('publishNebulaIvyPublicationToTestLocalRepository')
                .build()

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'ivy-0.1.0.xml').text)
        def dependency = root.dependencies.dependency[0]
        dependency.@org == 'testjava'
        dependency.@name == 'b'
        dependency.@rev == '0.0.1'
        dependency.@conf == 'runtime->default'
    }

    /*def 'verify ivy.xml contains runtime dependencies'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:c:0.0.1').build()
        File ivyrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ivy { url '${ivyrepo.absolutePath}' }
            }

            dependencies {
                runtime 'testjava:c:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'testjava'
        dependency.artifactId.text() == 'c'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'runtime'
    }

    def 'verify ivy.xml contains provided dependencies for wars'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:d:0.0.1').build()
        File ivyrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'war'

            repositories {
                ivy { url '${ivyrepo.absolutePath}' }
            }

            dependencies {
                providedCompile 'testjava:d:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'testjava'
        dependency.artifactId.text() == 'd'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'provided'
    }*/
}
