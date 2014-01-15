package nebula.plugin.publishing.component

import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer
import org.gradle.internal.reflect.Instantiator

class UsageContainer extends DefaultPolymorphicDomainObjectContainer<CustomUsage> {
    UsageContainer(Instantiator instantiator) {
        super(CustomUsage, instantiator)
    }
}
