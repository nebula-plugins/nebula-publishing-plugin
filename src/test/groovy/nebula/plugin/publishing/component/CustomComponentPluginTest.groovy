package nebula.plugin.publishing.component

import com.energizedwork.spock.extensions.TempDirectory
import nebula.test.ProjectSpec
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.plugins.JavaPlugin

class CustomComponentPluginTest extends ProjectSpec {
    @TempDirectory File projectRoot

    def 'has component'() {
        when:
        project.plugins.apply(CustomComponentPlugin)

        then:
        project.components.size() == 1
        CustomSoftwareComponent softwareComponent = project.components.getByName('custom')
        softwareComponent
        softwareComponent.usages.size() == 0
    }

    def 'reacts to java plugin'() {
        when:
        project.plugins.apply(CustomComponentPlugin)
        project.plugins.apply(JavaPlugin)
        project.dependencies.add('compile', 'com.google.guava:guava:15.0')

        then:
        project.components.size() == 2
        CustomSoftwareComponent softwareComponent = project.components.getByName('custom')
        softwareComponent
        softwareComponent.usages.size() == 1
        CustomUsage usage = softwareComponent.usages.iterator().next()
        usage.name == 'runtime'
        usage.artifacts.size() == 1
        def artifact = usage.artifacts.iterator().next()
        artifact.type == 'jar'
        artifact.extension == 'jar'
        usage.dependencies.size() == 1

    }

    def 'artifact can be added by file'() {
        when:
        project.plugins.apply(CustomComponentPlugin)
        project.configurations.create('myconf')
        CustomComponentPlugin.addArtifact(project, 'myconf',
            new DefaultPublishArtifact("test", "txt", "txt", "classif", new Date(), new File(projectRoot, 'test.txt')))

        then:
        project.plugins.withType(CustomComponentPlugin).usages.size() == 1
    }
}
