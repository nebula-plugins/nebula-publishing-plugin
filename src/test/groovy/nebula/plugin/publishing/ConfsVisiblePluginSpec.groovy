package nebula.plugin.publishing

import nebula.test.ProjectSpec

class ConfsVisiblePluginSpec extends ProjectSpec {
    def 'apply plugin'() {
        when:
        project.apply plugin: 'confs-visible'

        then:
        noExceptionThrown()
    }

    def 'java configurations are visible'() {
        when:
        project.apply plugin: 'java'
        project.plugins.apply(ConfsVisiblePlugin)

        then:
        project.configurations.compile.visible == true
        project.configurations.runtime.visible == true
    }

    def 'configurations are visible'() {
        when:
        def A = project.configurations.create('A').setVisible(false)
        project.configurations.create('B').setVisible(true).extendsFrom(A)
        project.plugins.apply(ConfsVisiblePlugin)

        then:
        project.configurations.A.visible == true
        project.configurations.B.visible == true
    }
}
