package nebula.plugin.publishing

import nebula.test.ProjectSpec

class NebulaPublishingPluginSpec extends ProjectSpec {
    def 'apply plugin'() {
        when:
        project.plugins.apply(NebulaPublishingPlugin)

        then:
        project.plugins.getPlugin(NebulaMavenPublishingPlugin)
        project.plugins.getPlugin(NebulaBintrayPublishingPlugin)
        project.plugins.getPlugin(NebulaBaseMavenPublishingPlugin)
    }
}
