package nebula.plugin.publishing.contacts

import nebula.plugin.contacts.BaseContactsPlugin
import nebula.plugin.contacts.Contact
import org.gradle.api.publish.maven.MavenPublication

class BaseContactPluginConfigurator {

    static void configureContacts(BaseContactsPlugin contactsPlugin, MavenPublication publication) {
        publication.pom.developers {
            def myContacts = contactsPlugin.getAllContacts()
            myContacts.each { Contact contact ->
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
