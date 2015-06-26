package nebula.plugin.publishing.maven

import nebula.test.PluginProjectSpec

class ScmPomPluginSpec extends PluginProjectSpec {
    String pluginName = 'nebula.scm-pom'

    def 'test various scm patterns'() {
        expect:
        ScmPomPlugin.calculateUrlFromOrigin(scmOrigin) == calculatedUrl

        where:
        scmOrigin | calculatedUrl
        'https://github.com/nebula-plugins/nebula-publishing-plugin.git' | 'https://github.com/nebula-plugins/nebula-publishing-plugin'
        'git@github.com:nebula-plugins/nebula-publishing-plugin.git' | 'https://github.com/nebula-plugins/nebula-publishing-plugin'
        'git@github.com:username/nebula-publishing-plugin.git' | 'https://github.com/username/nebula-publishing-plugin'
    }
}
