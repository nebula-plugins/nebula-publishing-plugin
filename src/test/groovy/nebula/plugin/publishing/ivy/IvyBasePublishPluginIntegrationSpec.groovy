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

import nebula.plugin.testkit.IntegrationHelperSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class IvyBasePublishPluginIntegrationSpec extends IntegrationHelperSpec {
    File publishDir

    def setup() {
        keepFiles = true

        buildFile << """\
            apply plugin: ${IvyBasePublishPlugin.name}

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

    def 'verify that ivy configurations have been added for Maven interoperability'() {
        setup:
        buildFile << '''\
            apply plugin: 'java'
        '''

        def expectedConfs = [
            compile: [], default: ['runtime', 'master'], javadoc: [], master: [],
            runtime: ['compile'], sources: [], test: ['runtime']
        ]

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')
        def root = new XmlSlurper().parse(new File(publishDir, 'ivy-0.1.0.xml'))
        def confs = root.configurations.conf

        then:
        expectedConfs.each { expected ->
            def conf = confs.find { it.@name == expected.key }
            assert conf
            assert conf.@extends == expected.value.join(',')
            assert conf.@visibility == 'public'
        }

        confs.size() == expectedConfs.size()
    }

    def 'verify ivy.xml is correct'() {
        buildFile << '''\
            apply plugin: 'java'

            description = 'test description'
        '''.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parse(new File(publishDir, 'ivy-0.1.0.xml'))
        root.info.@organisation == 'test.nebula'
        root.info.@module == 'ivytest'
        root.info.@revision == '0.1.0'
        root.info.description == 'test description'
        root.info.@status == 'integration'

        def artifact = root.publications.artifact[0]
        artifact.@name == 'ivytest'
        artifact.@type == 'jar'
        artifact.@ext == 'jar'
        artifact.@conf == 'runtime'
    }

    def 'status changes when set'() {
        buildFile << '''\
            apply plugin: 'java'

            status = 'release'
        '''.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parse(new File(publishDir, 'ivy-0.1.0.xml'))
        root.info.@status == 'release'
    }

    def 'creates a jar publication'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'ivytest-0.1.0.jar').exists()
        new File(publishDir, 'ivy-0.1.0.xml').exists()
    }

    def 'creates a jar publication for scala projects'() {
        buildFile << '''\
            apply plugin: 'scala'
        '''.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'ivytest-0.1.0.jar').exists()
    }

    def 'creates a jar publication for groovy projects'() {
        buildFile << '''\
            apply plugin: 'groovy'
        '''.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'ivytest-0.1.0.jar').exists()
    }

    def 'creates a war publication'() {
        buildFile << '''\
            apply plugin: 'war'
        '''.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

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
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

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
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'ivytest-0.1.0.war').exists()
        new File(publishDir, 'ivy-0.1.0.xml').exists()
        !new File(publishDir, 'ivytest-0.1.0.jar').exists()
    }

    def 'verify ivy.xml contains compile and runtime dependencies'() {
        def graph = new DependencyGraphBuilder().addModule('testjava:a:0.0.1').addModule('testjava:b:0.0.1').build()
        File ivyrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ivy { url '${ivyrepo.absolutePath}' }
            }

            dependencies {
                compile 'testjava:a:0.0.1'
                runtime 'testjava:b:0.0.1'
            }
        """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        assertDependency('testjava', 'a', '0.0.1', 'runtime->default')
        assertDependency('testjava', 'b', '0.0.1', 'runtime->default')
    }

    boolean assertDependency(String org, String name, String rev, String conf = null) {
        def dependencies = new XmlSlurper().parse(new File(publishDir, 'ivy-0.1.0.xml')).dependencies.dependency
        def found = dependencies.find { it.@name == name && it.@org == org }
        assert found
        assert found.@rev == rev
        assert !conf || found.@conf == conf
        found
    }
}
