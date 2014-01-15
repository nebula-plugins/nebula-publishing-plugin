package nebula.plugin.publishing.ivy

import nebula.test.ProjectSpec
import org.apache.commons.lang3.reflect.FieldUtils
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublication
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor

class NebulaIvyPublishingPluginSpec extends ProjectSpec {

    def 'apply plugin'() {
        when:
        project.plugins.apply(NebulaIvyPublishingPlugin)

        then:
        project.publishing.publications.size() == 1
    }

    def 'ensure coordinates'() {
        when:
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.group = 'group2'
        project.evaluate()

        then:
        DefaultIvyPublication ivyPub = project.publishing.publications.getByName('nebula')
        DefaultIvyPublicationIdentity id = FieldUtils.readField(ivyPub, 'publicationIdentity', true)
        id.organisation == 'group2'
    }

    def 'md generated'() {
        when:
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.evaluate()
        project.tasks.all {
            println "Task: ${it.name}"
        }
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        generateTask.destination.exists()
    }

    def 'md has dependencies'() {
        when:
        project.group = 'test'
        project.description = 'Description'
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.apply plugin: 'java'
        project.dependencies {
            compile 'asm:asm:3.1'
        }
        project.evaluate()
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        def pom = generateTask.destination.text
        pom.contains('<description>Description</description>')
        pom.contains('organisation="test"')
        pom.contains('org="asm" name="asm"')
    }

    /**
     * This actually happens in NebulaBaseIvyPublishingPlugin, but I'm too lazy to test it there.
     */
    def 'md has excludes'() {
        when:
        project.group = 'test'
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.apply plugin: 'java'
        project.dependencies {
            compile 'asm:asm:3.1'
            compile('org.apache.httpcomponents:httpclient:4.3.1') {
                exclude group: 'org.apache.httpcomponents', module: 'httpcore'
                exclude module: 'commons-logging'
            }
        }
        project.evaluate()
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        println generateTask.destination.text
        def pom = new XmlSlurper().parse(generateTask.destination)
        def deps = pom.dependencies.dependency
        deps.find { it.@org == 'asm' && it.@name == 'asm'}
        def httpclient = deps.find { it.@name == 'httpclient' }
        httpclient.exclude.find { it.@module == 'httpcore' && it.@org == 'org.apache.httpcomponents' }
        httpclient.exclude.find { it.@module == 'commons-logging' }
    }
}
