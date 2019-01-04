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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

class MavenDeveloperPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply MavenBasePublishPlugin

        project.plugins.withId("nebula.contacts-base") { contactsPlugin ->
            project.publishing {
                publications {
                    withType(MavenPublication) { publication ->
                        if (! project.state.executed) {
                            project.afterEvaluate {
                                configureContacts(contactsPlugin, publication)
                            }
                        } else {
                            configureContacts(contactsPlugin, publication)
                        }
                    }
                }
            }
        }
    }

    private void configureContacts(contactsPlugin, MavenPublication publication) {
        publication.pom.developers {
            def myContacts = contactsPlugin.getAllContacts()
            myContacts.each { contact ->
                developer {
                    if (contact.github) {
                        id = contact.github
                    } else if (contact.twitter) {
                        id = contact.twitter
                    }
                    if (contact.moniker) {
                        name = contact.moniker
                    }

                    email = contact.email
                    if (contact.roles) {
                        roles = contact.roles
                    }
                }
            }
        }
    }
}
