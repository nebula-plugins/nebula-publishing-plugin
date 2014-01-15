package nebula.plugin.publishing.component

import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.Usage

public class CustomSoftwareComponent implements SoftwareComponentInternal {
    final String name
    final Set<CustomUsage> usages

    CustomSoftwareComponent(String name, Set<CustomUsage> usages) {
        this.name = name
        this.usages = usages
    }

    Set<Usage> getUsages() {
        return usages as Set
    }
}