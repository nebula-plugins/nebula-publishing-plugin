/*
 * Copyright 2015-2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.publishing

import nebula.test.PluginProjectSpec
import spock.lang.Unroll

class ManifestElementNameGeneratorSpec extends PluginProjectSpec {
    String pluginName = 'com.netflix.nebula.ivy-manifest'

    @Unroll
    def 'name conversions from #manifestName to #convertedName'() {
        expect:
        ManifestElementNameGenerator.elementName(manifestName) == convertedName

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
