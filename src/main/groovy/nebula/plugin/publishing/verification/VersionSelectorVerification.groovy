package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.SubVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector

class VersionSelectorVerification {

    private Set<ModuleIdentifier> ignore
    private Set<String> ignoreGroups

    VersionSelectorVerification(Set<ModuleIdentifier> ignore, Set<String> ignoreGroups) {
        this.ignoreGroups = ignoreGroups
        this.ignore = ignore
    }

    List<VersionSelectorVerificationViolation> verify(List<Dependency> dependencies) {
        dependencies.findAll { it.version != null }
                .findAll { ! ignoreGroups.contains(it.group) }
                .findAll { ! ignore.contains(DefaultModuleIdentifier.newId(it.group, it.name)) }
                .findAll { dependency -> verifySubVersion(dependency) }
                .collect { dependency -> new VersionSelectorVerificationViolation(dependency: dependency) }
    }

    private boolean verifySubVersion(Dependency dependency) {
        VersionSelector selector = parseSelector(dependency.version)
        selector instanceof SubVersionSelector && !selector.selector.endsWith(".+")
    }

    private VersionSelector parseSelector(String version) {
        def scheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator(), new VersionParser())
        def selector = scheme.parseSelector(version)
        selector
    }
}
