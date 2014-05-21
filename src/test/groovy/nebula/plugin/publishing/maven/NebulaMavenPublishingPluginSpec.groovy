package nebula.plugin.publishing.maven

import nebula.test.ProjectSpec
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
        MavenPublication mavenJava = project.publishing.publications.getByName('mavenJava')
        mavenJava.groupId == 'group2'
    }

    def 'pom generated'() {
        when:
        project.plugins.apply(NebulaMavenPublishingPlugin)
        project.evaluate()
        GenerateMavenPom generateTask = project.tasks.getByName('generatePomFileForMavenJavaPublication')
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
        GenerateMavenPom generateTask = project.tasks.getByName('generatePomFileForMavenJavaPublication')
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
        GenerateMavenPom generateTask = project.tasks.getByName('generatePomFileForMavenJavaPublication')
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
        given:
        project.plugins.apply(NebulaMavenPublishingPlugin)
        project.plugins.apply(WarPlugin)

        when:
        project.evaluate()

        then:
        project.publishing.publications.size() == 1
        project.publishing.publications.getByName('mavenJava').artifacts.size() == 1
    }
}
