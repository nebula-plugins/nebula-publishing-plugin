package nebula.plugin.publishing

import nebula.test.ProjectSpec

class NebulaBintrayPublishingPluginSpec extends ProjectSpec {
    def 'apply plugin'() {
        when:
        project.plugins.apply(NebulaBintrayPublishingPlugin)

        then:
        project.tasks.getByName('bintrayUpload') != null
    }
}
