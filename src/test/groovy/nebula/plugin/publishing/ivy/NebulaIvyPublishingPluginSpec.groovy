package nebula.plugin.publishing.ivy

import nebula.plugin.publishing.NebulaJavadocJarPlugin
import nebula.plugin.publishing.NebulaSourceJarPlugin
import nebula.plugin.publishing.NebulaTestJarPlugin
import nebula.test.ProjectSpec
import org.apache.commons.lang3.reflect.FieldUtils
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
        def ivy = generateTask.destination.text
        ivy.contains('<description>Description</description>')
        ivy.contains('organisation="test"')
        ivy.contains('org="asm" name="asm"')
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
        def ivy = new XmlSlurper().parse(generateTask.destination)
        def deps = ivy.dependencies.dependency
        deps.find { it.@org == 'asm' && it.@name == 'asm'}
        def httpclient = deps.find { it.@name == 'httpclient' }
        httpclient.exclude.find { it.@module == 'httpcore' && it.@org == 'org.apache.httpcomponents' }
        httpclient.exclude.find { it.@module == 'commons-logging' }
    }

    def 'md has other usages'() {
        when:
        project.group = 'test'
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.plugins.apply(NebulaJavadocJarPlugin)
        project.plugins.apply(NebulaTestJarPlugin)
        project.plugins.apply(NebulaSourceJarPlugin)
        project.apply plugin: 'java'
        project.dependencies {
            compile 'asm:asm:3.1'
            runtime 'org.apache.httpcomponents:httpclient:4.3.1'
            testCompile 'org.apache.httpcomponents:httpcore:4.3.1'
            testRuntime 'commons-logging:commons-logging:1.1.3'

        }
        project.evaluate()
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        println generateTask.destination.text
        def ivy = new XmlSlurper().parse(generateTask.destination)
        def artifacts = ivy.publications.artifact

        artifacts.size() == 4
        def correctName = artifacts.findAll { it.@name == canonicalName }
        correctName.size() == 4
        artifacts.any {
            it.@type == 'javadoc' && it.@ext == 'jar' && it.@conf == 'javadoc' && it.@'e:classifier' == 'javadoc'
        }

    }

    def 'handle providedCompile'() {
        project.group = 'test'
        project.apply plugin: 'war'
        project.plugins.apply(NebulaIvyPublishingPlugin)
        project.dependencies {
            providedCompile 'asm:asm:3.1'
        }

        when:
        project.evaluate()
        GenerateIvyDescriptor generateTask = project.tasks.getByName('generateDescriptorFileForNebulaPublication')
        generateTask.doGenerate()

        then:
        def ivy = new XmlSlurper().parse(generateTask.destination)
        def deps = ivy.dependencies.dependency
        def asm = deps.find { it?.@org == 'asm' && it?.@name == 'asm' }
        asm?.@conf?.text() == 'provided'
    }
}
