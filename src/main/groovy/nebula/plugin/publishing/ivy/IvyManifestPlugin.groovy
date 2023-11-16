package nebula.plugin.publishing.ivy

import groovy.transform.CompileDynamic
import nebula.plugin.info.InfoBrokerPlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec
import org.gradle.api.publish.ivy.IvyPublication


import static nebula.plugin.publishing.ManifestElementNameGenerator.*

@CompileDynamic
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
            PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
            publishing.publications(new Action<PublicationContainer>() {
                @Override
                void execute(PublicationContainer publications) {
                    publications.withType(IvyPublication) { IvyPublication publication ->
                        publication.descriptor(new Action<IvyModuleDescriptorSpec>() {
                            @Override
                            void execute(IvyModuleDescriptorSpec ivyModuleDescriptorSpec) {
                                ivyModuleDescriptorSpec.withXml(new Action<XmlProvider>() {
                                    @Override
                                    void execute(XmlProvider xml) {
                                        def desc = xml.asNode()?.info[0].description[0]
                                        desc.'@xmlns:nebula' = 'http://netflix.com/build'

                                        infoBroker.buildManifest().each { key, value ->
                                            desc.appendNode("nebula:${elementName(key)}", value)
                                        }
                                    }
                                })
                            }
                        })
                    }
                }
            })
        }
    }
}
