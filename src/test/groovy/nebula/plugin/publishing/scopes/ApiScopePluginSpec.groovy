/*
 * Copyright 2016 Netflix, Inc.
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
package nebula.plugin.publishing.scopes

import nebula.test.PluginProjectSpec
import spock.lang.Unroll

class ApiScopePluginSpec extends PluginProjectSpec {
    String pluginName = 'nebula.compile-api'

    def 'new scope does not exist if language plugin not applied'() {
        when:
        project.plugins.apply(ApiScopePlugin)

        then:
        !project.configurations.findByName('compileApi')
    }

    @Unroll
    def 'compileApi scope exists if #plugin plugin is present'() {
        project.plugins.apply(plugin)

        when:
        project.plugins.apply(ApiScopePlugin)

        then:
        project.configurations.findByName('compileApi')

        where:
        plugin << ['java', 'groovy', 'scala']
    }
}
