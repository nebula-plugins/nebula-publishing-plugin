package nebula.plugin.publishing.component

import org.gradle.api.DomainObjectSet
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.Usage

public class CustomSoftwareComponent implements SoftwareComponentInternal {
    final String name
    final DomainObjectSet<CustomUsage> usages

    CustomSoftwareComponent(String name, DomainObjectSet<CustomUsage> usages) {
        this.name = name
        this.usages = usages
    }

    Set<Usage> getUsages() {
        return usages
    }
}