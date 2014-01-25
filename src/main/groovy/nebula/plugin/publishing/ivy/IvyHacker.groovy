package nebula.plugin.publishing.ivy

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.status.Status
import org.apache.ivy.core.module.status.StatusManager
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser
import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.repository.url.URLResource

/**
 * General Gradle + Ivy hackery encapsulated in a "shiny" package.
 */
class IvyHacker {

    /*
     * Creates an IvySettings instance from scratch that can be used for simple repo queries.
     */
    static IvySettings createIvySettings() {
        // Make an IvySettings from scratch
        IvySettings settings = new IvySettings()
        settings.defaultInit()

        // Tweak the setting's status manager with our CBF/Nebula statuses
        def sm = new StatusManager()
        sm.statuses.add(new Status('release', false))
        sm.statuses.add(new Status('candidate', false))
        sm.statuses.add(new Status('integration', true))
        sm.statuses.add(new Status('snapshot', true)) // Netflix specific, but shouldn't hurt to put in here.
        sm.setDefaultStatus('snapshot')
        settings.statusManager = sm

        settings
    }

    static DefaultModuleDescriptor readModuleDescriptor(File ivyxml) {
        IvySettings ivySettings = createIvySettings()
        InputStream is = new FileInputStream(ivyxml)
        Resource res = new URLResource(ivyxml.toURL())
        DefaultModuleDescriptor moduleDescriptor =
            (DefaultModuleDescriptor) XmlModuleDescriptorParser.getInstance().parseDescriptor(ivySettings, is, res, false)
        return moduleDescriptor
    }
}
