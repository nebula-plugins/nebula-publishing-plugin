/*
 * Copyright 2015-2019 Netflix, Inc.
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

import nebula.plugin.publishing.verification.DirectDependenciesVerifier
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.VariantVersionMappingStrategy
import org.gradle.api.publish.VersionMappingStrategy
import org.gradle.api.publish.maven.MavenPublication

/**
 * Replaces first order dependencies with the selected versions when publishing.
 */
class MavenResolvedDependenciesPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply MavenBasePublishPlugin
        project.afterEvaluate(new Action<Project>() {
            @Override
            void execute(Project p) {

                PublishingExtension publishing = p.extensions.getByType(PublishingExtension)
                publishing.publications(new Action<PublicationContainer>() {

                    @Override
                    void execute(PublicationContainer publications) {
                        DirectDependenciesVerifier.verify(project)
                        publications.withType(MavenPublication) { MavenPublication publication ->
                            publication.versionMapping(new Action<VersionMappingStrategy>() {
                                @Override
                                void execute(VersionMappingStrategy versionMappingStrategy) {
                                    versionMappingStrategy.allVariants(new Action<VariantVersionMappingStrategy>() {
                                        @Override
                                        void execute(VariantVersionMappingStrategy variantVersionMappingStrategy) {
                                            variantVersionMappingStrategy.fromResolutionResult()
                                        }
                                    })
                                }
                            })
                        }
                    }
                })
            }
        })
    }
}
