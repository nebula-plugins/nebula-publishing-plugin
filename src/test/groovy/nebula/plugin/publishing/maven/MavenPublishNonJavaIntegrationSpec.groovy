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

import nebula.test.IntegrationSpec

class MavenPublishNonJavaIntegrationSpec extends IntegrationSpec {
    def 'when applied to non java project do not break'() {
        setup:
        def dir = new File(projectDir, 'zip')
        dir.mkdir()
        new File(dir, 'test.txt').text = 'test'
        buildFile << """\
            ${applyPlugin(MavenPublishPlugin)}

            group = 'test.nebula'
            version = '0.1.0'

            task createZip(type: Zip) {
                destinationDir = project.buildDir
                classifier = 'testzip'
                from 'zip'
            }

            publishing {
                repositories {
                    maven {
                        name 'testmaven'
                        url 'build/testmavenrepo'
                    }
                }

                publications {
                    nebula(MavenPublication) {
                        artifact project.tasks.createZip
                    }
                }
            }
        """.stripIndent()

        when:
        runTasksSuccessfully('publishNebulaPublicationToTestmavenRepository')

        then:
        noExceptionThrown()
    }
}
