package nebula.plugin.publishing

import nebula.test.ProjectSpec
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

import java.util.concurrent.atomic.AtomicBoolean

class NebulaBaseMavenPublishingPluginSpec extends ProjectSpec {

    def 'apply plugin'() {
        AtomicBoolean marker = new AtomicBoolean(false)

        when:
        project.plugins.apply(MavenPublishPlugin)
        project.getExtensions().getByType(PublishingExtension).publications.create('mavenJava', MavenPublication)

        def plugin = project.plugins.apply(NebulaBaseMavenPublishingPlugin)
        plugin.withMavenPublication {
            marker.set(true)
        }

        then:
        marker.get() == false

        when:
        project.evaluate()

        then:
        marker.get() == true

    }

    def 'apply plugin before publishing plugin'() {
        AtomicBoolean marker = new AtomicBoolean(false)

        when:
        def plugin = project.plugins.apply(NebulaBaseMavenPublishingPlugin)
        plugin.withMavenPublication {
            marker.set(true)
        }

        then:
        marker.get() == false

        when:
        project.evaluate()

        then:
        marker.get() == false

        when:
        project.plugins.apply(MavenPlugin)

        then:
        marker.get() == false

        when:
        project.getExtensions().getByType(PublishingExtension).publications.create('mavenJava', MavenPublication)

        then:
        marker.get() == true

    }


}
