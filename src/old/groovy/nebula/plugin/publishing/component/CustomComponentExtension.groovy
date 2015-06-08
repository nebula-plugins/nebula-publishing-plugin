package nebula.plugin.publishing.component

import org.gradle.api.internal.DefaultDomainObjectCollection

class CustomComponentExtension {
    final DefaultDomainObjectCollection<CustomUsage> usages

    CustomComponentExtension(DefaultDomainObjectCollection usages) {
        this.usages = usages
    }

    def usages(Closure closure) {
        usages.configure(closure)
    }
}
