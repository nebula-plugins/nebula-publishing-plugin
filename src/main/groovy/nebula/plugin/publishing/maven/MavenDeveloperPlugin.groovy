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

import nebula.plugin.contacts.BaseContactsPlugin
import nebula.plugin.contacts.Contact
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

class MavenDeveloperPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply MavenBasePublishPlugin

        try {
            Class.forName('nebula.plugin.contacts.BaseContactsPlugin')
        } catch (Throwable ex) {
            project.logger.info('Skipping adding extra manifest elements from the contacts plugin as it has not been applied')
            return
        }

        project.plugins.withType(BaseContactsPlugin) { BaseContactsPlugin contactsPlugin ->
            project.publishing {
                publications {
                    withType(MavenPublication) {
                        pom.developers {
                            def myContacts = contactsPlugin.getAllContacts()
                            myContacts.each { Contact contact ->
                                developer {
                                    if (contact.github) {
                                        id =  contact.github
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
            }
        }
    }
}
