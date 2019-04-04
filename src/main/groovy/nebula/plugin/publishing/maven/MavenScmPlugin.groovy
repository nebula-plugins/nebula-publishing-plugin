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

import groovy.transform.CompileStatic
import nebula.plugin.info.scm.GitScmProvider
import nebula.plugin.info.scm.ScmInfoPlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPomScm
import org.gradle.api.publish.maven.MavenPublication

class MavenScmPlugin implements Plugin<Project> {
    @Override
    @CompileStatic
    void apply(Project project) {
        project.plugins.apply MavenBasePublishPlugin

        try {
            Class.forName('nebula.plugin.info.scm.ScmInfoPlugin')
        } catch (Throwable ex) {
            project.logger.info('Skipping adding extra scm elements from the info plugin as it has not been applied')
            return
        }

        project.plugins.withType(ScmInfoPlugin) { ScmInfoPlugin scmInfo ->
            def publishing = project.extensions.getByType(PublishingExtension)
            publishing.publications(new Action<PublicationContainer>() {
                @Override
                void execute(PublicationContainer publications) {
                    publications.withType(MavenPublication) { MavenPublication publication ->
                        if (scmInfo.selectedProvider instanceof GitScmProvider) {
                            publication.pom(new Action<MavenPom>() {
                                @Override
                                void execute(MavenPom pom) {
                                    pom.url.set(calculateUrlFromOrigin(scmInfo.extension.origin, project))
                                }
                            })
                        }
                        publication.pom(new Action<MavenPom>() {
                            @Override
                            void execute(MavenPom pom) {
                                pom.scm(new Action<MavenPomScm>() {
                                    @Override
                                    void execute(MavenPomScm scm) {
                                        scm.url.set(scmInfo.extension.origin)
                                    }
                                })
                            }
                        })
                    }
                }
            })
        }
    }

    static final GIT_PATTERN = /((git|ssh|https?):(\/\/))?(\w+@)?([\w\.]+)([\:\\/])([\w\.@\:\/\-~]+)(\.git)(\/)?/

    /**
     * Convert git syntax of git@github.com:reactivex/rxjava-core.git to https://github.com/reactivex/rxjava-core
     * @param origin
     */
    static String calculateUrlFromOrigin(String origin, Project project) {
        def m = origin =~ GIT_PATTERN
        if (m) {
            return "https://${m[0][5]}/${m[0][7]}"
        } else {
            project.logger.warn("Unable to convert $origin to https form in MavenScmPlugin. Using original value.")
            return origin
        }
    }
}
