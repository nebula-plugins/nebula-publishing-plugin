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

import nebula.plugin.info.scm.GitScmProvider
import nebula.plugin.info.scm.PerforceScmProvider
import nebula.plugin.info.scm.ScmInfoPlugin
import nebula.plugin.info.scm.SvnScmProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

class ScmPomPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(MavenBasePublishingPlugin)

        project.plugins.withType(ScmInfoPlugin) { ScmInfoPlugin scmInfo ->
            project.plugins.withType(MavenPublishPlugin) {
                project.publishing {
                    publications {
                        nebula(MavenPublication) {
                            pom.withXml { XmlProvider xml ->
                                def root = xml.asNode()
                                if (scmInfo.selectedProvider instanceof GitScmProvider) {
                                    root.appendNode('url', calculateUrlFromOrigin(scmInfo.extension.origin))
                                }
                                def scmNode = root.appendNode('scm')
                                scmNode.appendNode('url', scmInfo.extension.origin)
                            }
                        }
                    }
                }
            }
        }
    }

    static final GIT_PATTERN = /((git|ssh|https?):(\/\/))?(\w+@)?([\w\.]+)([\:\\/])([\w\.@\:\/\-~]+)(\.git)(\/)?/

    /**
     * Convert git syntax of git@github.com:reactivex/rxjava-core.git to https://github.com/reactivex/rxjava-core
     * @param origin
     */
    static String calculateUrlFromOrigin(String origin) {
        def m = origin =~ GIT_PATTERN
        return "https://${m[0][5]}/${m[0][7]}"
    }
}
