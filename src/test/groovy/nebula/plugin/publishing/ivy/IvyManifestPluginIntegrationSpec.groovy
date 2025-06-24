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
package nebula.plugin.publishing.ivy

import groovy.xml.XmlSlurper
import nebula.plugin.publishing.BaseIntegrationTestKitSpec
import spock.lang.Subject

@Subject(IvyManifestPlugin)
class IvyManifestPluginIntegrationSpec extends BaseIntegrationTestKitSpec {
    File publishDir

    def setup() {
        buildFile << """\
            plugins {
                id 'com.netflix.nebula.ivy-manifest'
                id 'com.netflix.nebula.ivy-nebula-publish'
            }

            version = '0.1.0'
            group = 'test.nebula'

            publishing {
                repositories {
                    ivy {
                        name = 'testLocal'
                        url = 'testrepo'
                    }
                }
            }
            """.stripIndent()

        settingsFile << '''\
            rootProject.name = 'ivytest'
            '''.stripIndent()

        publishDir = new File(projectDir, 'testrepo/test.nebula/ivytest/0.1.0')
    }

    def 'manifest created'() {
        buildFile << '''\
            apply plugin: 'java'
            apply plugin: 'com.netflix.nebula.info'
            '''

        when:
        runTasks('publishNebulaIvyPublicationToTestLocalRepository')

        then:
        def desc = new XmlSlurper().parse(new File(publishDir, 'ivy-0.1.0.xml'))
                .declareNamespace([nebula: 'http://netflix.com/build'])
                .info[0].description[0]
        desc.children().size() > 1

        and:
        String change = desc.'nebula:Change'
        String fullChange = desc.'nebula:Full_Change'
        desc.'nebula:Implementation_Version' == '0.1.0'
        desc.'nebula:Implementation_Title' == 'test.nebula#ivytest;0.1.0'
        fullChange
        fullChange.startsWith(change)
        fullChange.size() > change.size()
    }
}
