package nebula.plugin.publishing.verification


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

    List<VersionSelectorVerificationViolation> verify(List<DeclaredDependency> dependencies) {
        dependencies.findAll { it.version != null }
                .findAll { ! ignoreGroups.contains(it.group) }
                .findAll { ! ignore.contains(DefaultModuleIdentifier.newId(it.group, it.name)) }
                .findAll { dependency -> verifySubVersion(dependency) }
                .collect { dependency -> new VersionSelectorVerificationViolation(dependency: dependency) }
    }

    private boolean verifySubVersion(DeclaredDependency dependency) {
        VersionSelector selector = parseSelector(dependency.version)
        selector instanceof SubVersionSelector && !VERSION_PART_SEPARATORS.contains(selector.selector[selector.selector.length() - 2])
    }

    private VersionSelector parseSelector(String version) {
        def scheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator(), new VersionParser())
        def selector = scheme.parseSelector(version)
        selector
    }

    // See https://github.com/gradle/gradle/blob/c140b42ed9b263a06a5346782a04310313bc29b2/platforms/software/dependency-management/src/main/java/org/gradle/api/internal/artifacts/ivyservice/ivyresolve/strategy/VersionParser.java#L50
    private static final Set<String> VERSION_PART_SEPARATORS = ['.', '_', '-', '+']

}
