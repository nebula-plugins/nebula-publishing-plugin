package nebula.plugin.publishing.maven

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator
import org.junit.Ignore

/**
 * @author jmcgarr
 */
class NebulaMavenPublishingPluginIntSpec extends IntegrationSpec {

    String mavenLocal = "${System.env['HOME']}/.m2/repository"

    static String repo
    def setupSpec() {
        def myGraph = [
          'test.example:foo:3.1'
        ]

        def generator = new GradleDependencyGenerator(new DependencyGraph(myGraph), 'build/nebulamavenpublishingpluginintspec')
        generator.generateTestMavenRepo()
        repo = new File('build/nebulamavenpublishingpluginintspec/mavenrepo').absolutePath
    }

    def setup() {
        File toBeCleaned = new File("$mavenLocal/nebula/hello")
        if( toBeCleaned.exists() ) {
            boolean cleaned = toBeCleaned.deleteDir()
        }
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

            repositories { maven { url '${repo}' } }

            dependencies {
                compile 'test.example:foo:3.1'
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('publishToMavenLocal')

        then: 'the build passed'
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenLocal/nebula/hello/world/1.0/world-1.0.pom").exists()
        new File("$mavenLocal/nebula/hello/world/1.0/world-1.0.jar").exists()
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

            repositories { maven { url '${repo}' } }
            dependencies {
                compile 'test.example:foo:3.1'
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('publishToMavenLocal')

        then:
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenLocal/nebula/hello/world/1.0/world-1.0.pom").exists()
        new File("$mavenLocal/nebula/hello/world/1.0/world-1.0.jar").exists()
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

            repositories { maven { url '${repo}' } }
            dependencies {
                compile 'test.example:foo:3.1'
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('publishToMavenLocal')

        then:
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenLocal/nebula/hello/world/1.0/world-1.0.pom").exists()
        new File("$mavenLocal/nebula/hello/world/1.0/world-1.0.war").exists()
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

            repositories { maven { url '${repo}' } }
            dependencies {
                compile 'test.example:foo:3.1'
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('publishToMavenLocal')

        then:
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenLocal/nebula/hello/world/1.2/world-1.2.pom").exists()
        new File("$mavenLocal/nebula/hello/world/1.2/world-1.2.war").exists()
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

            repositories { maven { url '${repo}' } }
            dependencies {
                compile 'test.example:foo:3.1'
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('publishToMavenLocal')

        then:
        results.failure == null

        and:
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenLocal/nebula/hello/world/1.2/world-1.2.pom").exists()
        new File("$mavenLocal/nebula/hello/world/1.2/world-1.2.war").exists()
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

            repositories { maven { url '${repo}' } }

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

            repositories { maven { url '${repo}' } }

            dependencies {
                compile 'test.example:foo:3.1'
            }
        """.stripIndent()

        when:
        def results = runTasksSuccessfully('install')

        then:
        results.failure == null

        and: 'generates a pom file'
        new File("$projectDir/build/publications/mavenNebula/pom-default.xml").exists()

        and:
        new File("$mavenLocal/nebula/hello/world/1.0/world-1.0.pom").exists()
        new File("$mavenLocal/nebula/hello/world/1.0/world-1.0.war").exists()
    }

    def 'handle providedCompile'() {
        writeHelloWorld('nebula.hello')
        buildFile << """
            apply plugin: 'war'
            apply plugin: 'nebula-maven-publishing'
            repositories { maven { url '${repo}' } }
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
        asm.scope == 'provided'
    }
}
