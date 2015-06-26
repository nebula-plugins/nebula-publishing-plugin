package nebula.plugin.publishing.maven

import nebula.test.PluginProjectSpec
import spock.lang.Unroll


class ManifestPomPluginSpec extends PluginProjectSpec {
    String pluginName = 'nebula.manifest-pom'

    @Unroll
    def 'name conversions from #manifestName to #convertedName'() {
        expect:
        ManifestPomPlugin.scrubElementName(manifestName) == convertedName

        where:
        manifestName | convertedName
        'prop-one' | 'prop_one'
        'prop-Two' | 'prop_Two'
        'Prop.three' | 'Prop_three'
        'prop_four_a' | 'prop_four_a'
        'a.b.c' | 'a_b_c'
        'a-be-ce' | 'a_be_ce'
    }
}
