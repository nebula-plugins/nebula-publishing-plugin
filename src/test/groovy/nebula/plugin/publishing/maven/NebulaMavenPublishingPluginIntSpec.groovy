package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator

/**
 * @author jmcgarr
 */
class NebulaMavenPublishingPluginIntSpec extends IntegrationSpec {
    File reposRootDir
    File mavenRepoDir

    def setup() {
        reposRootDir = new File(projectDir, 'build/repos')
        mavenRepoDir = new File(reposRootDir, 'mavenrepo')

        def myGraph = [
          'test.example:foo:3.1'
        ]

        def generator = new GradleDependencyGenerator(new DependencyGraph(myGraph), reposRootDir.absolutePath)
        generator.generateTestMavenRepo()
    }

    def 'simple publishing test'() {
        given:
        settingsFile = new File(projectDir, 'settings.gradle')
        settingsFile.text = "rootProject.name='world'"
        writeHelloWorld('nebula.hello')
        buildFile << """
            apply plugin: 'nebula-maven-publishing'
            apply plugin: 'java'

            group = 'nebula.hello'
            version = '1.0'

            repositories { maven { url 'file://$mavenRepoDir' } }

            dependencies {
                compile 'test.example:foo:3.1'
            }

            project.publishing {
                repositories {
                    maven { url 'file://$mavenRepoDir' }
                }
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('publishMavenNebulaPublicationToMavenRepository')

        then: 'the build passed'
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenRepoDir/nebula/hello/world/1.0/world-1.0.pom").exists()
        new File("$mavenRepoDir/nebula/hello/world/1.0/world-1.0.jar").exists()
    }

    def 'simple publishing of multi-module'() {
        given:
        def buildText = '''
            apply plugin: 'nebula-maven-publishing'
            apply plugin: 'java'
            apply plugin: 'nebula-test-jar'

            group = 'nebula.hello'
            version = '1.0'
        '''.stripIndent()
        buildFile << buildText

        createSubProject('subA', buildText)
        createSubProject('subB', buildText + """
dependencies {
    compile project(':subA')
}

project.publishing {
    repositories {
        maven { url 'file://$mavenRepoDir' }
    }
}
""")

        when:
        def results = runTasksSuccessfully('publishMavenNebulaPublicationToMavenRepository')

        then: 'the build passed'
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/subB/build/publications/mavenNebula/pom-default.xml").exists()

    }

    def 'publishes artifacts regardless of plugin ordering'() {
        given:
        settingsFile = new File(projectDir, 'settings.gradle')
        settingsFile.text = "rootProject.name='world'"
        writeHelloWorld('nebula.hello')
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'nebula-maven-publishing'

            group = 'nebula.hello'
            version = '1.0'

            repositories { maven { url 'file://$mavenRepoDir' } }
            dependencies {
                compile 'test.example:foo:3.1'
            }

            project.publishing {
                repositories {
                    maven { url 'file://$mavenRepoDir' }
                }
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('publishMavenNebulaPublicationToMavenRepository')

        then:
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenRepoDir/nebula/hello/world/1.0/world-1.0.pom").exists()
        new File("$mavenRepoDir/nebula/hello/world/1.0/world-1.0.jar").exists()
    }

    def 'declaring war plugin first does not break maven'() {
        given:
        settingsFile = new File(projectDir, 'settings.gradle')
        settingsFile.text = "rootProject.name='world'"
        writeHelloWorld('nebula.hello')
        buildFile << """
            apply plugin: 'war'
            apply plugin: 'nebula-maven-publishing'

            group = 'nebula.hello'
            version = '1.0'

            repositories { maven { url 'file://$mavenRepoDir' } }
            dependencies {
                compile 'test.example:foo:3.1'
            }

            project.publishing {
                repositories {
                    maven { url 'file://$mavenRepoDir' }
                }
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('publishMavenNebulaPublicationToMavenRepository')

        then:
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenRepoDir/nebula/hello/world/1.0/world-1.0.pom").exists()
        new File("$mavenRepoDir/nebula/hello/world/1.0/world-1.0.war").exists()
    }

    def 'publishes web artifacts'() {
        given:
        settingsFile = new File(projectDir, 'settings.gradle')
        settingsFile.text = "rootProject.name='world'"
        writeHelloWorld('nebula.hello')
        buildFile << """
            apply plugin: 'nebula-maven-publishing'
            apply plugin: 'war'

            group = 'nebula.hello'
            version = '1.2'

            repositories { maven { url 'file://$mavenRepoDir' } }
            dependencies {
                compile 'test.example:foo:3.1'
            }

            project.publishing {
                repositories {
                    maven { url 'file://$mavenRepoDir' }
                }
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('publishMavenNebulaPublicationToMavenRepository')

        then:
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenRepoDir/nebula/hello/world/1.2/world-1.2.pom").exists()
        new File("$mavenRepoDir/nebula/hello/world/1.2/world-1.2.war").exists()
    }

    def 'works with ivy-plugin'() {
        given:
        settingsFile = new File(projectDir, 'settings.gradle')
        settingsFile.text = "rootProject.name='world'"
        writeHelloWorld('nebula.hello')
        buildFile << """
            apply plugin: 'nebula-ivy-publishing'
            apply plugin: 'war'
            apply plugin: 'nebula-maven-publishing'

            group = 'nebula.hello'
            version = '1.2'

            repositories { maven { url 'file://$mavenRepoDir' } }
            dependencies {
                compile 'test.example:foo:3.1'
            }

            project.publishing {
                repositories {
                    maven { url 'file://$mavenRepoDir' }
                }
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('publishMavenNebulaPublicationToMavenRepository')

        then:
        results.failure == null

        and:
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenRepoDir/nebula/hello/world/1.2/world-1.2.pom").exists()
        new File("$mavenRepoDir/nebula/hello/world/1.2/world-1.2.war").exists()
    }

    def 'install task works'() {
        given:
        settingsFile = new File(projectDir, 'settings.gradle')
        settingsFile.text = "rootProject.name='world'"
        writeHelloWorld('nebula.hello')
        buildFile << """
            apply plugin: 'nebula-maven-publishing'
            apply plugin: 'java'

            group = 'nebula.hello'
            version = '1.0'

            repositories { maven { url 'file://$mavenRepoDir' } }

            dependencies {
                compile 'test.example:foo:3.1'
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('install')

        then: 'the build passed'
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        String mavenLocal = "${System.env['HOME']}/.m2/repository"
        new File("$mavenLocal/nebula/hello/world/1.0/world-1.0.pom").exists()
        new File("$mavenLocal/nebula/hello/world/1.0/world-1.0.jar").exists()
    }

    def 'maven publishing works with javadoc and sources'() {
        given:
        settingsFile = new File(projectDir, 'settings.gradle')
        settingsFile.text = "rootProject.name='world'"
        writeHelloWorld('nebula.hello')
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'nebula-javadoc-jar'
            apply plugin: 'nebula-source-jar'
            apply plugin: 'nebula-maven-publishing'
            apply plugin: 'war'

            group = 'nebula.hello'
            version = '1.0'

            repositories { maven { url 'file://$mavenRepoDir' } }

            dependencies {
                compile 'test.example:foo:3.1'
            }

            project.publishing {
                repositories {
                    maven { url 'file://$mavenRepoDir' }
                }
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('publishMavenNebulaPublicationToMavenRepository')

        then:
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenRepoDir/nebula/hello/world/1.0/world-1.0.pom").exists()
        new File("$mavenRepoDir/nebula/hello/world/1.0/world-1.0.war").exists()
    }

    def 'handle providedCompile'() {
        writeHelloWorld('nebula.hello')
        buildFile << """
            apply plugin: 'war'
            apply plugin: 'nebula-maven-publishing'
            repositories { maven { url 'file://$mavenRepoDir' } }
            dependencies {
                providedCompile 'test.example:foo:2.2.3'
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('generatePomFileForMavenNebulaPublication')

        then:
        def pomFile = file('build/publications/mavenNebula/pom-default.xml')
        pomFile.exists()
        def pom = new XmlSlurper().parse(pomFile)
        def deps = pom.dependencies.dependency
        def asm = deps.find { it.artifactId.text() == 'foo' && it.groupId.text() == 'test.example'}
        asm.scope.text() == 'provided'
    }

    private File createSubProject(String name, String buildFile) {
        settingsFile << """
            include '${name}'
        """.stripIndent()

        def sub = new File(projectDir, name)
        sub.mkdirs()

        new File(sub, 'build.gradle') << buildFile

        return sub
    }

}
