/*
 * Copyright 2015-2020 Netflix, Inc.
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
package nebula.plugin.publishing.maven

import groovy.xml.XmlSlurper
import groovy.json.JsonSlurper
import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import nebula.test.TestKitOutputUtil
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion

class MavenPublishPluginIntegrationSpec extends BaseIntegrationTestKitSpec {
    def setup() {
        keepFiles = true
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.maven-publish'
            }

            version = '0.1.0'
            group = 'test.nebula'

            publishing {
                repositories {
                    maven {
                        name = 'testLocal'
                        url = 'testrepo'
                    }
                }
            }
        """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'mavenpublishingtest'
        '''.stripIndent()

        if (GradleVersion.current().baseVersion < GradleVersion.version("6.0")) {
            settingsFile << '''\
                enableFeaturePreview("GRADLE_METADATA")
            '''.stripIndent()
        }
    }

    def 'publish POM with resolved dependencies'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test:a:0.0.1')
                .addModule('test:b:1.9.2')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            apply plugin: 'com.netflix.nebula.contacts'
            apply plugin: 'com.netflix.nebula.info'

            repositories {
                maven { url = '${mavenrepo.absolutePath}' }
            }

            contacts {
                'nebula@example.test' {
                    moniker 'Nebula'
                }
            }

            dependencies {
                implementation 'test:a:0.+'
                implementation 'test:b:[1.0.0, 2.0.0)'
            }
        """.stripIndent()

        when:
        runTasks('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.version == '0.1.0'
        pom.developers.developer[0].name == 'Nebula'
        pom.properties.nebula_Module_Owner == 'nebula@example.test'
        pom.url != null

        when:
        def dependencies = pom.dependencies.dependency
        def a = dependencies.find { it.artifactId == 'a' }

        then:
        a.version == '0.0.1'

        when:
        def b = dependencies.find { it.artifactId == 'b' }

        then:
        b.version == '1.9.2'
    }

    def 'aggregate publishing preserves metadata through POM reuse and manifest changes on Gradle 9.8'() {
        given:
        gradleVersion = '9.8.2'
        def graph = new DependencyGraphBuilder()
                .addModule('test:a:0.0.1')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            apply plugin: 'com.netflix.nebula.contacts'
            apply plugin: 'com.netflix.nebula.info'

            description = 'Aggregate publishing regression'
            repositories {
                maven { url = '${mavenrepo.toURI()}' }
            }
            contacts {
                'nebula@example.test' {
                    moniker 'Nebula'
                }
            }
            dependencies {
                implementation 'test:a:0.+'
            }
            publishing.publications {
                custom(MavenPublication) {
                    from components.java
                    artifactId = 'custom-artifact'
                }
                withType(MavenPublication).configureEach {
                    pom.properties.put('custom', 'preserved')
                }
            }

            def broker = plugins.getPlugin(nebula.plugin.info.InfoBrokerPlugin)
            broker.add('Gradle-Project-Path', project.path)
            broker.add('Gradle-Project-Directory', '.')
            broker.add('Manifest-Value', { providers.gradleProperty('manifestValue').getOrElse('one') })
        """.stripIndent()
        def taskPaths = ['generatePomFileForNebulaPublication', 'generatePomFileForCustomPublication',
                         'generateMetadataFileForNebulaPublication']

        when:
        def first = runAggregatePublishingTasks(taskPaths)

        then:
        taskPaths.each { taskPath ->
            assert first.task(":$taskPath").outcome == TaskOutcome.SUCCESS
        }
        assertAggregateMetadata('one')

        when:
        def unchanged = runAggregatePublishingTasks(taskPaths)

        then:
        unchanged.output.contains('Reusing configuration cache.')
        taskPaths.take(2).each { taskPath ->
            assert unchanged.task(":$taskPath").outcome == TaskOutcome.UP_TO_DATE
        }
        assertAggregateMetadata('one')

        when:
        def changed = runAggregatePublishingTasks(taskPaths + ['-PmanifestValue=two'])

        then:
        taskPaths.take(2).each { taskPath ->
            assert changed.task(":$taskPath").outcome == TaskOutcome.SUCCESS
        }
        assertAggregateMetadata('two')
    }

    private BuildResult runAggregatePublishingTasks(List<String> tasks) {
        def previous = System.getProperty('ignoreDeprecations')
        try {
            System.setProperty('ignoreDeprecations', 'true')
            def result = runTasks(*(tasks + ['--configuration-cache-problems=fail']))
            System.setProperty('ignoreDeprecations', 'false')
            // InfoJavaPlugin uses findAll(Closure); keep failing on any other deprecation.
            def checkedOutput = result.output.readLines().findAll {
                !it.startsWith('The DomainObjectCollection.findAll(Closure) method has been deprecated.')
            }.join('\n')
            TestKitOutputUtil.checkForDeprecations(checkedOutput)
            return result
        } finally {
            if (previous == null) {
                System.clearProperty('ignoreDeprecations')
            } else {
                System.setProperty('ignoreDeprecations', previous)
            }
        }
    }

    private void assertAggregateMetadata(String manifestValue) {
        ['nebula', 'custom'].each { publication ->
            def pom = new XmlSlurper().parse(new File(projectDir, "build/publications/$publication/pom-default.xml"))
            assert pom.groupId.text() == 'test.nebula'
            assert pom.artifactId.text() == (publication == 'nebula' ? 'mavenpublishingtest' : 'custom-artifact')
            assert pom.version.text() == '0.1.0'
            assert pom.name.text() == 'mavenpublishingtest'
            assert pom.description.text() == 'Aggregate publishing regression'
            assert pom.developers.developer.name*.text() == ['Nebula']
            assert pom.properties.nebula_Module_Owner*.text() == ['nebula@example.test']
            assert pom.properties.nebula_Implementation_Version*.text() == ['0.1.0']
            assert pom.properties.nebula_Implementation_Title*.text() == ['test.nebula#mavenpublishingtest;0.1.0']
            assert pom.properties.nebula_Full_Change.text()
            assert pom.properties.nebula_Full_Change.text().startsWith(pom.properties.nebula_Change.text())
            assert pom.properties.nebula_Gradle_Project_Path*.text() == [':']
            assert pom.properties.nebula_Gradle_Project_Directory*.text() == ['.']
            assert pom.properties.nebula_Manifest_Value*.text() == [manifestValue]
            assert pom.properties.custom*.text() == ['preserved']
            assert pom.dependencies.dependency.size() == 1
            assert pom.dependencies.dependency[0].groupId.text() == 'test'
            assert pom.dependencies.dependency[0].artifactId.text() == 'a'
            assert pom.dependencies.dependency[0].version.text() == '0.0.1'
        }
        def moduleJson = new JsonSlurper().parse(new File(projectDir, 'build/publications/nebula/module.json'))
        def runtimeVariant = moduleJson.variants.find { it.name == 'runtimeElements' }
        assert runtimeVariant.dependencies.size() == 1
        assert runtimeVariant.dependencies[0].group == 'test'
        assert runtimeVariant.dependencies[0].module == 'a'
        assert runtimeVariant.dependencies[0].version.requires == '0.0.1'
    }

    def 'produces gradle metadata file'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test:a:0.0.1')
                .addModule('test:b:1.9.2')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            apply plugin: 'com.netflix.nebula.contacts'
            apply plugin: 'com.netflix.nebula.info'

            repositories {
                maven { url = '${mavenrepo.absolutePath}' }
            }

            contacts {
                'nebula@example.test' {
                    moniker 'Nebula'
                }
            }

            dependencies {
                implementation 'test:a:0.+'
                implementation 'test:b:[1.0.0, 2.0.0)'
            }
        """.stripIndent()

        when:
        runTasks('generateMetadataFileForNebulaPublication')


        then:
        def moduleJson = new JsonSlurper().parse(new File(projectDir, 'build/publications/nebula/module.json'))
        def runtimeVariant = moduleJson.variants.find { it.name == 'runtimeElements'}
        def dependencies = runtimeVariant.dependencies
        dependencies.size() == 2

        when:
        def a = dependencies.find { it.module == 'a' }

        then:
        a.version.requires == '0.0.1'

        when:
        def b = dependencies.find { it.module == 'b' }

        then:
        b.version.requires == '1.9.2'
    }

    def 'publish BOM when platform plugin is used'() {
        def graph = new DependencyGraphBuilder()
                .addModule('test:a:0.0.1')
                .addModule('test:b:1.9.2')
                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java-platform'
            apply plugin: 'com.netflix.nebula.contacts'
            apply plugin: 'com.netflix.nebula.info'

            repositories {
                maven { url = '${mavenrepo.absolutePath}' }
            }

            contacts {
                'nebula@example.test' {
                    moniker 'Nebula'
                }
            }

            dependencies {
                constraints {
                    api 'test:a:0.0.1'
                    api 'test:b:1.9.2'
                }
            }
        """.stripIndent()

        when:
        runTasks('generatePomFileForNebulaPublication')

        then:
        def pom = new XmlSlurper().parse(new File(projectDir, 'build/publications/nebula/pom-default.xml'))
        pom.version == '0.1.0'
        pom.packaging == 'pom'
        pom.developers.developer[0].name == 'Nebula'
        pom.properties.nebula_Module_Owner == 'nebula@example.test'
        pom.url != null

        when:
        def dependencies = pom.dependencyManagement.dependencies.dependency
        def a = dependencies.find { it.artifactId == 'a' }

        then:
        a.version == '0.0.1'

        when:
        def b = dependencies.find { it.artifactId == 'b' }

        then:
        b.version == '1.9.2'
    }


}
