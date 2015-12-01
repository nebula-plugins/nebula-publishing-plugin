/*
 * Copyright 2015 Netflix, Inc.
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
package nebula.plugin.publishing.maven

import nebula.test.PluginProjectSpec
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import spock.lang.Unroll

class MavenScmPluginSpec extends PluginProjectSpec {
    String pluginName = 'nebula.maven-scm'

    @Unroll
    def 'test scm pattern #scmOrigin'() {
        when:
        def project = Mock(Project)
        project.logger >> Mock(Logger)
        def scmPlugin = new MavenScmPlugin()

        then:
        scmPlugin.calculateUrlFromOrigin(scmOrigin, project) == calculatedUrl

        where:
        scmOrigin                                                        | calculatedUrl
        'https://github.com/nebula-plugins/nebula-publishing-plugin'     | 'https://github.com/nebula-plugins/nebula-publishing-plugin'
        'https://github.com/nebula-plugins/nebula-publishing-plugin.git' | 'https://github.com/nebula-plugins/nebula-publishing-plugin'
        'git@github.com:nebula-plugins/nebula-publishing-plugin.git'     | 'https://github.com/nebula-plugins/nebula-publishing-plugin'
        'git@github.com:username/nebula-publishing-plugin.git'           | 'https://github.com/username/nebula-publishing-plugin'
    }
}
