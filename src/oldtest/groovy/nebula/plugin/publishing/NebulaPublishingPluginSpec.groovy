package nebula.plugin.publishing

import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import nebula.plugin.publishing.maven.NebulaMavenPublishingPlugin
import nebula.test.ProjectSpec

class NebulaPublishingPluginSpec extends ProjectSpec {
    def 'apply plugin'() {
        when:
        project.plugins.apply(NebulaPublishingPlugin)

        then:
        project.plugins.getPlugin(NebulaMavenPublishingPlugin)
        project.plugins.getPlugin(NebulaBaseMavenPublishingPlugin)
    }
}
