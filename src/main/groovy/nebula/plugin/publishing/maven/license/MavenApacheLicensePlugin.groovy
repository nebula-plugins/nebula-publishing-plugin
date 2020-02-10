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
package nebula.plugin.publishing.maven.license

import nebula.plugin.publishing.maven.MavenBasePublishPlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPomLicense
import org.gradle.api.publish.maven.MavenPomLicenseSpec
import org.gradle.api.publish.maven.MavenPublication

class MavenApacheLicensePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply MavenBasePublishPlugin

        PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
        publishing.publications(new Action<PublicationContainer>() {
            @Override
            void execute(PublicationContainer publications) {
                publications.withType(MavenPublication) { MavenPublication publication ->
                    configureLicense(publication)
                }
            }
        })
    }

    private void configureLicense(MavenPublication publication) {
        publication.pom(new Action<MavenPom>() {
            @Override
            void execute(MavenPom mavenPom) {
                mavenPom.licenses(new Action<MavenPomLicenseSpec>() {
                    @Override
                    void execute(MavenPomLicenseSpec mavenPomLicenseSpec) {
                        mavenPomLicenseSpec.license(new Action<MavenPomLicense>() {
                            @Override
                            void execute(MavenPomLicense mavenPomLicense) {
                                mavenPomLicense.name.set('The Apache Software License, Version 2.0')
                                mavenPomLicense.url.set('http://www.apache.org/licenses/LICENSE-2.0.txt')
                                mavenPomLicense.distribution.set('repo')
                            }
                        })
                    }
                })
            }
        })
    }
}