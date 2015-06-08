package nebula.plugin.publishing.maven

import nebula.test.ProjectSpec
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom

class NebulaMavenPublishingPluginSpec extends ProjectSpec {

    def 'apply plugin'() {
        when:
        project.plugins.apply(NebulaMavenPublishingPlugin)

        then:
        project.publishing.publications.size() == 1
    }

    def 'ensure coordinates'() {
        when:
        project.plugins.apply(NebulaMavenPublishingPlugin)
        project.group = 'group2'
        project.evaluate()

        then:
        MavenPublication mavenJava = project.publishing.publications.getByName('mavenNebula')
        mavenJava.groupId == 'group2'
    }

    def 'pom generated'() {
        when:
        project.plugins.apply(NebulaMavenPublishingPlugin)
        project.evaluate()
        GenerateMavenPom generateTask = project.tasks.getByName('generatePomFileForMavenNebulaPublication')
        generateTask.doGenerate()

        then:
        generateTask.destination.exists()
    }

    def 'pom has dependencies'() {
        when:
        project.group = 'test'
        project.plugins.apply(NebulaMavenPublishingPlugin)
        project.apply plugin: 'java'
        project.dependencies {
            compile 'asm:asm:3.1'
        }
        project.evaluate()
        GenerateMavenPom generateTask = project.tasks.getByName('generatePomFileForMavenNebulaPublication')
        generateTask.doGenerate()

        then:
        def pom = generateTask.destination.text
        pom.contains("<artifactId>asm</artifactId>")
    }

    def 'pom has excludes'() {
        when:
        project.group = 'test'
        project.plugins.apply(NebulaMavenPublishingPlugin)
        project.apply plugin: 'java'
        project.dependencies {
            compile 'asm:asm:3.1'
            compile('org.apache.httpcomponents:httpclient:4.3.1') {
                exclude group: 'org.apache.httpcomponents', module: 'httpcore'
                exclude module: 'commons-logging'
            }
        }
        project.evaluate()
        GenerateMavenPom generateTask = project.tasks.getByName('generatePomFileForMavenNebulaPublication')
        generateTask.doGenerate()

        then:
        println generateTask.destination.text
        def pom = new XmlSlurper().parse(generateTask.destination)
        def deps = pom.dependencies.dependency
        deps.find { it.artifactId.text() == 'asm' && it.groupId.text() == 'asm' }
        def httpclient = deps.find { it.artifactId.text() == 'httpclient' }
        httpclient.exclusions.exclusion.find {
            it.artifactId.text() == 'httpcore' && it.groupId.text() == 'org.apache.httpcomponents'
        }
        httpclient.exclusions.exclusion.find { it.artifactId.text() == 'commons-logging' }
    }

    def 'skip java component if web project'() {
        given: 'the WarPlugin and the NebulaMavenPublishingPlugin are applied'
        project.plugins.apply(WarPlugin)
        project.plugins.apply(NebulaMavenPublishingPlugin)

        when: 'the project is configured'
        project.evaluate()

        then: 'make sure the configuration did not fail due to the java component blocking the web component publication'
        project.publishing.publications.size() == 1

        and: 'the mavenWeb publication is configured properly'
        project.publishing.publications.getByName('mavenNebula').artifacts.size() == 1
    }

    def 'web component wins if added after java component'() {
        given: 'the java plugin is added first'
        project.plugins.apply(JavaPlugin)

        and: 'then the NebulaMavenPublishingPlugin is added next'
        project.plugins.apply(NebulaMavenPublishingPlugin)

        and: 'and then the WarPlugin is added after the publication is configured'
        project.plugins.apply(WarPlugin)

        when: 'the project is configured'
        project.evaluate()

        then: 'the mavenWeb publication is configured properly'
        project.publishing.publications.size() == 1
        project.publishing.publications.getByName('mavenNebula').artifacts.size() == 1
    }
}
