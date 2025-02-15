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
package nebula.plugin.publishing.maven

import groovy.transform.CompileDynamic
import nebula.plugin.info.scm.GitScmProvider
import nebula.plugin.info.scm.ScmInfoExtension
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
    void apply(Project project) {
        project.plugins.apply MavenBasePublishPlugin

        try {
            Class.forName('nebula.plugin.info.scm.ScmInfoPlugin')
        } catch (Throwable ignored) {
            project.logger.info('Skipping adding extra scm elements from the info plugin as it has not been applied')
            return
        }

        project.plugins.withType(ScmInfoPlugin) { ScmInfoPlugin scmInfo ->
            def publishingExtension = project.extensions.getByType(PublishingExtension)
            // Using rootProject's git info for performance reasons instead of having to re-calculate it
            // in every subproject.
            def scmExtension = project.rootProject.extensions.getByType(ScmInfoExtension)

            publishingExtension.publications(new Action<PublicationContainer>() {
                @Override
                void execute(PublicationContainer publications) {
                    publications.withType(MavenPublication) { MavenPublication publication ->
                        if (scmInfo.selectedProvider instanceof GitScmProvider) {
                            publication.pom(new Action<MavenPom>() {
                                @Override
                                void execute(MavenPom pom) {
                                    pom.url.set(calculateUrlFromOrigin(scmExtension.origin, project))
                                }
                            })
                        }
                        publication.pom(new Action<MavenPom>() {
                            @Override
                            void execute(MavenPom pom) {
                                pom.scm(new Action<MavenPomScm>() {
                                    @Override
                                    void execute(MavenPomScm scm) {
                                        scm.url.set(scmExtension.origin)
                                    }
                                })
                            }
                        })
                    }
                }
            })
        }
    }

    static final GIT_PATTERN = /((git|ssh|https?):(\/\/))?(\w+@)?([\w\.@\\/\-~]+)([\:\\/])([\w\.@\:\/\-~]+)(\.git)(\/)?/

    /**
     * Convert git syntax of git@github.com:reactivex/rxjava-core.git to https://github.com/reactivex/rxjava-core
     * @param origin
     */
    @CompileDynamic
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
