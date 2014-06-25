package nebula.plugin.publishing.maven

import nebula.test.ProjectSpec

/**
 * Unit tests for the NetflixMavenPlugin
 * @author mmcgarr
 */
class MavenDistributePluginSpec extends ProjectSpec {

    void 'apply plugin'() {
        when:
        project.plugins.apply(MavenDistributePlugin)

        then:
        project.publishing.publications.size() == 1
        project.publishing.publications.getByName('mavenNebula') != null
    }


}
