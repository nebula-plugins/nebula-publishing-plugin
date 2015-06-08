package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class NebulaMavenPublishingIntegrationSpec extends IntegrationSpec {
    File publishDir

    def setup() {
        buildFile << """\
            ${applyPlugin(MavenPublishingPlugin)}

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
            rootProject.name = 'maventest'
        '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test/nebula/maventest/0.1.0')
    }

    def 'creates a jar publication'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.jar').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
    }

    def 'creates a jar publication for scala projects'() {
        buildFile << '''\
            apply plugin: 'scala'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.jar').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
    }

    def 'creates a jar publication for groovy projects'() {
        buildFile << '''\
            apply plugin: 'groovy'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.jar').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
    }

    def 'creates a war publication'() {
        buildFile << '''\
            apply plugin: 'war'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.war').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
    }

    def 'creates a war publication in presence of java plugin'() {
        buildFile << '''\
            apply plugin: 'java'
            apply plugin: 'war'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.war').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
        !new File(publishDir, 'maventest-0.1.0.jar').exists()
    }

    def 'creates a war publication in presence of java plugin no matter the order'() {
        buildFile << '''\
            apply plugin: 'war'
            apply plugin: 'java'
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.war').exists()
        new File(publishDir, 'maventest-0.1.0.pom').exists()
        !new File(publishDir, 'maventest-0.1.0.jar').exists()
    }

    def 'verify pom is correct'() {
        buildFile << '''\
            apply plugin: 'java'
        '''.stripIndent()

        String expectedPom = '''\
            <?xml version="1.0" encoding="UTF-8"?>
            <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <modelVersion>4.0.0</modelVersion>
              <groupId>test.nebula</groupId>
              <artifactId>maventest</artifactId>
              <version>0.1.0</version>
            </project>
        '''.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        new File(publishDir, 'maventest-0.1.0.pom').text == expectedPom
    }

    def 'verify pom contains compile dependencies'() {
        def graph = new DependencyGraphBuilder().addModule('test:a:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph).generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                compile 'test:a:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'test'
        dependency.artifactId.text() == 'a'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'runtime'
    }

    def 'verify pom contains compile dependencies for war projects'() {
        def graph = new DependencyGraphBuilder().addModule('test:a:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph).generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'
            apply plugin: 'war'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                compile 'test:a:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'test'
        dependency.artifactId.text() == 'a'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'runtime'
    }

    def 'verify pom contains runtime dependencies'() {
        def graph = new DependencyGraphBuilder().addModule('test:a:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph).generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'java'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                runtime 'test:a:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'test'
        dependency.artifactId.text() == 'a'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'runtime'
    }

    def 'verify pom contains provided dependencies for wars'() {
        def graph = new DependencyGraphBuilder().addModule('test:a:0.0.1').build()
        File mavenrepo = new GradleDependencyGenerator(graph).generateTestMavenRepo()

        buildFile << """\
            apply plugin: 'war'

            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }

            dependencies {
                providedCompile 'test:a:0.0.1'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestLocalRepository')

        then:
        def root = new XmlSlurper().parseText(new File(publishDir, 'maventest-0.1.0.pom').text)
        def dependency = root.dependencies.dependency[0]
        dependency.groupId.text() == 'test'
        dependency.artifactId.text() == 'a'
        dependency.version.text() == '0.0.1'
        dependency.scope.text() == 'provided'
    }
}
