package nebula.plugin.publishing.ivy

import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import spock.lang.Ignore
import spock.lang.Subject

@Subject(IvyRemovePlatformDependenciesPlugin)
class IvyRemovePlatformDependenciesPluginSpec extends BaseIntegrationTestKitSpec {
    File publishDir

    def setup() {
        settingsFile << '''\
            rootProject.name = 'removedplatformivytest'
            '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test.nebula/removedplatformivytest/0.1.0')
    }

    def 'publishes ivy descriptor without platform dependency'() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.ivy-publish'
                id 'com.netflix.nebula.ivy-resolved-dependencies'
                id 'com.netflix.nebula.ivy-remove-platform-dependencies'
            } 
            
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


        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule('test.resolved:a:1.1.0')
                .addModule('test.resolved:b:1.1.0').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")

        generator.generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                mavenCentral()
                ${generator.ivyRepositoryBlock}
                maven {
                    url = 'https://repo.jenkins-ci.org/releases/'
                }
            }

            dependencies {
                implementation platform('org.jenkins-ci.main:jenkins-bom:latest.release')
                implementation 'test.resolved:a:1.+'
            }
            """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.@rev == '1.1.0'

        def bom = findDependency('jenkins-bom')
        !bom
    }

    def 'publishes ivy descriptor without platform dependency - platform is a subproject'() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.ivy-publish'
                id 'com.netflix.nebula.ivy-resolved-dependencies'
                id 'com.netflix.nebula.ivy-remove-platform-dependencies'
            } 
            allprojects {
                apply plugin: 'com.netflix.nebula.ivy-publish'
                apply plugin: 'com.netflix.nebula.ivy-resolved-dependencies'
                apply plugin: 'com.netflix.nebula.ivy-remove-platform-dependencies'
    
                version = '0.1.0'
                group = 'test.nebula'
    
                repositories {
                            mavenCentral()
                            maven {
                                url = 'https://repo.jenkins-ci.org/releases/'
                            }
                        }
                publishing {
                    repositories {
                        ivy {
                            name = 'testLocal'
                            url = 'testrepo'
                        }
                    }
                }
            }
            """.stripIndent()


        addSubproject('platform', """
plugins {
    id 'java-platform'
}

dependencies {
    constraints {
        api 'commons-httpclient:commons-httpclient:3.1'
        runtime 'org.postgresql:postgresql:42.2.5'
    }
}

publishing {
    publications {
        myPlatform(IvyPublication) {
            from components.javaPlatform
        }
    }
}
        """)


        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule('test.resolved:a:1.1.0')
                .addModule('test.resolved:b:1.1.0').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")

        generator.generateTestIvyRepo()


        addSubproject('clienta', """
plugins {
    id 'java'
}

repositories {
    ${generator.ivyRepositoryBlock}
    mavenCentral()
}

dependencies {
    implementation platform('org.jenkins-ci.main:jenkins-bom:latest.release')
    implementation platform(project(":platform"))
    implementation 'test.resolved:a:1.+'
}
 """)

        when:
        def x = runTasks(':clienta:publishNebulaIvyPublicationToTestLocalRepository')

        then:
        File ivyDescriptor = new File(buildFile.parentFile, 'clienta/build/publications/nebulaIvy/ivy.xml')
        def a = findDependency('a', ivyDescriptor)
        a.@rev == '1.1.0'

        def jenkinsBom = findDependency('jenkins-bom', ivyDescriptor)
        !jenkinsBom

        def projectPlatform = findDependency('platform', ivyDescriptor)
        !projectPlatform
    }

    @Ignore
    def 'publishes ivy descriptor without enforced-platform dependency'() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.ivy-publish'
                id 'com.netflix.nebula.ivy-resolved-dependencies'
                id 'com.netflix.nebula.ivy-remove-platform-dependencies'
            } 
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


        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule('test.resolved:a:1.1.0')
                .addModule('test.resolved:b:1.1.0').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")

        generator.generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'
            repositories {
                ${generator.ivyRepositoryBlock}
                mavenCentral()
                maven {
                     url = 'https://repo.jenkins-ci.org/releases/'
                }
            }

            dependencies {
                implementation enforcedPlatform('org.jenkins-ci.main:jenkins-bom:latest.release')
                implementation 'test.resolved:a:1.+'
            }

            tasks.withType(GenerateModuleMetadata).configureEach {
                suppressedValidationErrors.add('enforced-platform')
            }
            """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.@rev == '1.1.0'

        def bom = findDependency('jenkins-bom')
        !bom
    }

    @Ignore
    def 'publishes ivy descriptor with platform dependency if plugin is not applied'() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.ivy-publish'
                id 'com.netflix.nebula.ivy-resolved-dependencies'
            } 
          
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


        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule('test.resolved:a:1.1.0')
                .addModule('test.resolved:b:1.1.0').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")

        generator.generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ${generator.ivyRepositoryBlock}
                mavenCentral()
                maven {
                     url = 'https://repo.jenkins-ci.org/releases/'
                }
            }

            dependencies {
                implementation enforcedPlatform('org.jenkins-ci.main:jenkins-bom:latest.release')
                implementation 'test.resolved:a:1.+'
            }

            tasks.withType(GenerateModuleMetadata).configureEach {
                suppressedValidationErrors.add('enforced-platform')
            }
            """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.@rev == '1.1.0'

        def bom = findDependency('jenkins-bom')
        bom != null
    }

    @Ignore
    def 'publishes ivy descriptor with enforced-platform dependency if plugin is not applied'() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.ivy-publish'
                id 'com.netflix.nebula.ivy-resolved-dependencies'
            } 
            
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


        def graph = new DependencyGraphBuilder().addModule('test.resolved:a:1.0.0')
                .addModule('test.resolved:a:1.1.0')
                .addModule('test.resolved:b:1.1.0').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen")

        generator.generateTestIvyRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                ${generator.ivyRepositoryBlock}
                mavenCentral()
                maven {
                     url = 'https://repo.jenkins-ci.org/releases/'
                }
            }

            dependencies {
                implementation enforcedPlatform('org.jenkins-ci.main:jenkins-bom:latest.release')
                implementation 'test.resolved:a:1.+'
            }

            tasks.withType(GenerateModuleMetadata).configureEach {
                suppressedValidationErrors.add('enforced-platform')
            }
            """.stripIndent()

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def a = findDependency('a')
        a.@rev == '1.1.0'

        def bom = findDependency('jenkins-bom')
        bom != null
    }


    def findDependency(String module, File ivyDescriptor = new File(publishDir, 'ivy-0.1.0.xml')) {
        def root = new XmlSlurper().parseText(ivyDescriptor.text)
        def d = root.dependencies.dependency.find {
            it.@name == module
        }
        return d
    }

}