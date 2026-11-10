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
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPomScm
import org.gradle.api.publish.maven.MavenPublication

/**
 * Adds SCM metadata to Maven publications when the SCM info collector is applied.
 *
 * <p>With {@code nebula.publishing.isolatedProjectsSupport=true}, reads only the
 * current project's extension and wires origin providers lazily into native POM
 * properties. Also enable {@code nebula.info.isolatedProjectsSupport=true} in an
 * info-plugin version 17.1.0 or newer. Apply both plugins
 * locally; root SCM overrides are not inherited in this mode.</p>
 *
 * <p>The default path retains root metadata reuse and eager origin conversion.</p>
 */
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
            if (project.providers.gradleProperty('nebula.publishing.isolatedProjectsSupport')
                    .map { Boolean.parseBoolean(it) }.getOrElse(false)) {
                configureIsolated(publishingExtension, project.extensions.getByType(ScmInfoExtension),
                        scmInfo.selectedProvider instanceof GitScmProvider)
                return
            }
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
                                    pom.url.set(calculateUrlFromOrigin(scmExtension.origin.get(), project))
                                }
                            })
                        }
                        publication.pom(new Action<MavenPom>() {
                            @Override
                            void execute(MavenPom pom) {
                                pom.scm(new Action<MavenPomScm>() {
                                    @Override
                                    void execute(MavenPomScm scm) {
                                        scm.url.set(scmExtension.origin.get())
                                    }
                                })
                            }
                        })
                    }
                }
            })
        }
    }

    private static void configureIsolated(PublishingExtension publishing, ScmInfoExtension scmInfo, boolean git) {
        publishing.publications.withType(MavenPublication).configureEach { MavenPublication publication ->
            publication.pom.scm { MavenPomScm scm -> scm.url.set(scmInfo.origin) }
            if (git) {
                publication.pom.url.set(gitUrl(scmInfo.origin))
            }
        }
    }

    /** The transformation retains no project or extension in task execution state. */
    private static Provider<String> gitUrl(Provider<String> origin) {
        origin.map { String value -> calculateUrl(value, Logging.getLogger(MavenScmPlugin)) }
    }

    static final GIT_PATTERN = /((git|ssh|https?):(\/\/))?(\w+@)?([\w\.@\\/\-~]+)([\:\\/])([\w\.@\:\/\-~]+)(\.git)(\/)?/

    /**
     * Converts a Git origin such as {@code git@github.com:team/library.git} to
     * {@code https://github.com/team/library} using the existing origin pattern.
     *
     * @param origin repository origin to convert
     * @param project supplies the logger for a nonconvertible origin
     * @return the HTTPS URL, or the original value with a warning when it cannot be converted
     */
    static String calculateUrlFromOrigin(String origin, Project project) {
        calculateUrl(origin, project.logger)
    }

    @CompileDynamic
    private static String calculateUrl(String origin, Logger logger) {
        def m = origin =~ GIT_PATTERN
        if (m) {
            return "https://${m[0][5]}/${m[0][7]}"
        } else {
            logger.warn("Unable to convert $origin to https form in MavenScmPlugin. Using original value.")
            return origin
        }
    }
}
