package nebula.plugin.publishing

import nebula.plugin.publishing.component.CustomComponentPlugin
import nebula.plugin.publishing.component.CustomSoftwareComponent
import nebula.test.ProjectSpec
import org.gradle.api.plugins.JavaPlugin

class NebulaJavadocJarPluginTest extends ProjectSpec {
    def 'adds artifact'() {
        when:
        project.apply plugin: 'nebula-javadoc-jar'
        project.apply plugin: 'java'

        then:
        def sourcesArtifacts = project.configurations.getByName('javadoc').artifacts
        sourcesArtifacts.size() == 1
        def artifact = sourcesArtifacts.iterator().next()
        artifact.classifier == 'javadoc'
        artifact.extension == 'jar'
        project.tasks.getByName('javadocJar')

    }


    def 'adds usage'() {
        when:
        project.plugins.apply(NebulaJavadocJarPlugin)
        project.plugins.apply(CustomComponentPlugin)

        then:
        CustomSoftwareComponent component = project.components.getByName('custom')
        component.usages.size() == 0

        when:
        project.plugins.apply(JavaPlugin)

        then:
        def javadocUsage = component.usages.find { it.name == 'javadoc' }
        javadocUsage
        javadocUsage.artifacts.size() == 1
        def artifact = javadocUsage.artifacts.iterator().next()
        artifact.classifier == 'javadoc'
        artifact.extension == 'jar'
        javadocUsage.dependencies.isEmpty()

    }
}
