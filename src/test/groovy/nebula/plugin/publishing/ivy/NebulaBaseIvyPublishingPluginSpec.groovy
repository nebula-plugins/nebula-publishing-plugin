package nebula.plugin.publishing.ivy

import nebula.test.ProjectSpec
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.plugins.IvyPublishPlugin

import java.util.concurrent.atomic.AtomicBoolean

class NebulaBaseIvyPublishingPluginSpec extends ProjectSpec {

    def 'apply plugin'() {
        AtomicBoolean marker = new AtomicBoolean(false)

        when:
        project.plugins.apply(IvyPublishPlugin)
        project.getExtensions().getByType(PublishingExtension).publications.create('nebula', IvyPublication)

        def plugin = project.plugins.apply(NebulaBaseIvyPublishingPlugin)
        plugin.withIvyPublication {
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
        def plugin = project.plugins.apply(NebulaBaseIvyPublishingPlugin)
        plugin.withIvyPublication {
            marker.set(true)
        }

        then:
        marker.get() == false

        when:
        project.evaluate()

        then:
        marker.get() == false

        when:
        project.plugins.apply(IvyPublishPlugin)

        then:
        marker.get() == false

        when:
        project.getExtensions().getByType(PublishingExtension).publications.create('nebula', IvyPublication)

        then:
        marker.get() == true

    }


}
