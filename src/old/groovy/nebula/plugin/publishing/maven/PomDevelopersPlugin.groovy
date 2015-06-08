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
import nebula.plugin.contacts.BaseContactsPlugin
import nebula.plugin.contacts.Contact
import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Take the contacts and apply them to the POM file. Probably should be in the publishing plugin.
 */
class PomDevelopersPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        // React to BaseContactsPlugin
        project.plugins.withType(BaseContactsPlugin) { BaseContactsPlugin contactsPlugin ->
            // React to Publishing Plugin
            project.plugins.withType(NebulaBaseMavenPublishingPlugin) { NebulaBaseMavenPublishingPlugin basePlugin ->
                basePlugin.withMavenPublication {
                    // This should be late enough to look at the contacts
                    pom.withXml {
                        def pomConfig = {
                            if (contactsPlugin.getAllContacts()) {
                                developers {
                                    // TODO make which roles that are used configurable
                                    contactsPlugin.getAllContacts().each { Contact contact ->
                                        // Can't put this in a function or else the closure's delegate won't be used correctly
                                        developer {
                                            if (contact.github) {
                                                id contact.github
                                            } else if (contact.twitter) {
                                                id contact.twitter
                                            }

                                            if (contact.moniker) {
                                                name contact.moniker
                                            }

                                            email contact.email

                                            if (contact.roles) {
                                                roles {
                                                    contact.roles.each { String roleString ->
                                                        role roleString
                                                    }
                                                }
                                            }
                                            //timezone '-8'
                                        }
                                    }
                                }
                            }
                        }
                        asNode().children().last() + pomConfig
                    }
                }
            }
        }
    }
}
