package nebula.plugin.publishing.verification

import groovy.transform.Immutable

@Immutable
class DefinedDependency {
    String configuration
    String preferredVersion
}
