package nebula.plugin.publishing.verification

import groovy.transform.Canonical
import groovy.transform.Immutable
import groovy.transform.ToString

@Canonical
@Immutable
@ToString
class DeclaredDependency implements Serializable {
    String group
    String name
    String version
}
