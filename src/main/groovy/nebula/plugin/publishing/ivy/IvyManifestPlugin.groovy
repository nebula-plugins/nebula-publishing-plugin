package nebula.plugin.publishing.ivy

import nebula.plugin.info.InfoBrokerPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.ivy.IvyPublication
import static nebula.plugin.publishing.ManifestElementNameGenerator.*

class IvyManifestPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply IvyBasePublishPlugin

        try {
            Class.forName('nebula.plugin.info.InfoBrokerPlugin')
        } catch (Throwable ex) {
            project.logger.info('Skipping adding extra manifest elements from the info plugin as it has not been applied')
            return
        }

        project.plugins.withType(InfoBrokerPlugin) { InfoBrokerPlugin infoBroker ->
            project.publishing {
                publications {
                    nebulaIvy(IvyPublication) {
                        descriptor.withXml { XmlProvider xml ->
                            // the ivy info>description tag is the only one which can contain free
                            // text, including arbitrary xml
                            def desc = xml.asNode()?.info[0].description[0]
                            desc.'@xmlns:nebula' = 'http://netflix.com/build'

                            infoBroker.buildManifest().each { key, value ->
                                desc.appendNode("nebula:${elementName(key)}", value)
                            }
                        }
                    }
                }
            }
        }
    }
}
