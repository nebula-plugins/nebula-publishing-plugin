package nebula.plugin.publishing

import nebula.plugin.publishing.component.CustomComponentPlugin
import nebula.plugin.publishing.component.CustomSoftwareComponent
import nebula.test.ProjectSpec
import org.gradle.api.plugins.JavaPlugin

class NebulaSourcesJarPluginTest extends ProjectSpec {
    def 'adds artifact'() {
        when:
        project.apply plugin: 'nebula-source-jar'
        project.apply plugin: 'java'

        then:
        def sourcesArtifacts = project.configurations.getByName('sources').artifacts
        sourcesArtifacts.size() == 1
        def artifact = sourcesArtifacts.iterator().next()
        artifact.classifier == 'sources'
        artifact.extension == 'jar'
        project.tasks.getByName('sourceJar')

    }

    def 'adds usage'() {
        when:
        project.plugins.apply(NebulaSourceJarPlugin)
        CustomComponentPlugin componentPlugin = project.plugins.apply(CustomComponentPlugin)

        then:
        CustomSoftwareComponent component = project.components.getByName('custom')
        component.usages.size() == 0

        when:
        project.plugins.apply(JavaPlugin)

        then:
        def sourcesUsage = component.usages.find { it.name == 'sources' }
        sourcesUsage
        sourcesUsage.artifacts.size() == 1
        def artifact = sourcesUsage.artifacts.iterator().next()
        artifact.classifier == 'sources'
        artifact.extension == 'jar'
        sourcesUsage.dependencies.isEmpty()

    }
}
