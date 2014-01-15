package nebula.plugin.publishing

import nebula.plugin.publishing.component.CustomComponentPlugin
import nebula.plugin.publishing.component.CustomSoftwareComponent
import nebula.test.ProjectSpec
import org.gradle.api.plugins.JavaPlugin

class NebulaTestJarPluginTest extends ProjectSpec {
    def 'adds artifact'() {
        when:
        project.apply plugin: 'nebula-test-jar'
        project.apply plugin: 'java'

        then:
        def sourcesArtifacts = project.configurations.getByName('test').artifacts
        sourcesArtifacts.size() == 1
        def artifact = sourcesArtifacts.iterator().next()
        artifact.classifier == 'tests'
        artifact.extension == 'jar'
        project.tasks.getByName('testJar')
    }

    def 'adds usage'() {
        when:
        project.plugins.apply(NebulaTestJarPlugin)
        project.plugins.apply(CustomComponentPlugin)

        then:
        CustomSoftwareComponent component = project.components.getByName('custom')
        component.usages.size() == 0

        when:
        project.plugins.apply(JavaPlugin)

        then:

        def jarUsage = component.usages.find { it.name == 'test' }
        jarUsage
        jarUsage.artifacts.size() == 1
        def artifact = jarUsage.artifacts.iterator().next()
        artifact.classifier == 'tests'
        artifact.extension == 'jar'
        artifact.type == 'test-jar'
        jarUsage.dependencies.isEmpty()

    }
}
