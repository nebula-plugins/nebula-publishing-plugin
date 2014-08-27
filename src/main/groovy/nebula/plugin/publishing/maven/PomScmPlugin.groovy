/*
 * Copyright 2014 Netflix, Inc.
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
import nebula.plugin.contacts.Contact
import nebula.plugin.info.InfoBrokerPlugin
import nebula.plugin.publishing.xml.NodeEnhancement
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.maven.MavenPublication

/**
 * Apply SCM and Project Url to POM.
 */
class PomScmPlugin implements Plugin<Project> {
    def markupPom(NebulaBaseMavenPublishingPlugin basePlugin, Closure rootNodeClosure) {
        basePlugin.withMavenPublication { MavenPublication t ->
            t.pom.withXml(new Action<XmlProvider>() {
                @Override
                void execute(XmlProvider x) {
                    def root = x.asNode()
                    rootNodeClosure.call(root)
                }
            })
        }
    }

    /**
     <scm>
         <url>scm:git://github.com/nebula-plugins/gradle-contacts-plugin.git</url>
         <connection>scm:git://github.com/nebula-plugins/gradle-contacts-plugin.git</connection>
     </scm>
     */
    def addScm(NebulaBaseMavenPublishingPlugin basePlugin, String moduleOrigin) {
        markupPom(basePlugin) { Node root ->
            use(NodeEnhancement) {
                def url = "scm:${moduleOrigin}"
                (root / 'scm' / 'url') << url
                (root / 'scm' / 'connection') << url
                // TODO <tag>HEAD</tag>
            }
        }
    }

    /**
     <url>https://github.com/nebula-plugins/gradle-contacts-plugin</url>
     */
    def addProjectUrl(NebulaBaseMavenPublishingPlugin basePlugin, String moduleOrigin) {
        def calculatedUrl = calculateUrlFromOrigin(moduleOrigin)
        markupPom(basePlugin) { Node root ->
            use(NodeEnhancement) {
                (root / 'url') << calculatedUrl
            }
        }
    }

    static GIT_PATTERN = /((git|ssh|https?):(\/\/))?(\w+@)?([\w\.]+)([\:\\/])([\w\.@\:\/\-~]+)(\.git)(\/)?/

    /**
     * Convert git syntax of git@github.com:reactivex/rxjava-core.git to https://github.com/reactivex/rxjava-core
     * @param origin
     */
    static String calculateUrlFromOrigin(String origin) {
        def m = origin =~ GIT_PATTERN
        return "https://${m[0][5]}/${m[0][7]}"
    }

    @Override
    void apply(Project project) {
        project.plugins.withType(InfoBrokerPlugin.class) { InfoBrokerPlugin broker ->
            // React to Publishing Plugin
            project.plugins.withType(NebulaBaseMavenPublishingPlugin) { NebulaBaseMavenPublishingPlugin basePlugin ->
                broker.watch('Module-Origin') { String moduleOrigin ->
                    addScm(basePlugin, moduleOrigin)
                    addProjectUrl(basePlugin, moduleOrigin)
                }
            }
        }
    }
}
