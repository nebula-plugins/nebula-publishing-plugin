package nebula.plugin.publishing.component

import com.google.common.collect.Lists
import com.google.common.collect.Sets
import groovy.transform.Canonical
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.CompositeDomainObjectSet
import org.gradle.api.internal.artifacts.DefaultDependencySet
import org.gradle.api.internal.component.Usage

@Canonical( excludes = "deferredDependencies" )
class CustomUsage implements Usage {
    final String confName
    final Set<PublishArtifact> artifacts
    final DeferredDependencies deferredDependencies

    CustomUsage(String confName, PublishArtifact artifact, DeferredDependencies deferredDependencies) {
        this.confName = confName
        this.artifacts = artifact?[artifact] as Set:[] as Set
        this.deferredDependencies = deferredDependencies
    }

    String getName() {
        return confName
    }

    @Canonical
    public static class DeferredDependencies {
        final String dependencyConfName
        final Configuration dependencyConfiguration
        final Set<String> confsToSkip
        //final DependencySet allDependencies

        DeferredDependencies(String dependencyConfName, Configuration dependencyConfiguration, Set<String> confsToSkip) {
            this.dependencyConfName = dependencyConfName
            this.dependencyConfiguration = dependencyConfiguration
            this.confsToSkip = confsToSkip?:([] as Set)
        }

        DefaultDependencySet collectPublicDependencies() {
            return collectPublicDependencies(dependencyConfiguration, confsToSkip)
        }

        static DefaultDependencySet collectPublicDependencies(Configuration configuration, Set<String> confsToSkip) {
            CompositeDomainObjectSet<Dependency> inheritedDependencies;
            inheritedDependencies = new CompositeDomainObjectSet<Dependency>(Dependency.class, configuration.dependencies);

            Queue<Configuration> confQueue = Lists.newLinkedList()
            confQueue.add(configuration)
            Set<String> visited = confsToSkip?Sets.newHashSet(confsToSkip):Sets.newHashSet()
            while (!confQueue.isEmpty()) {
                Configuration extendsConf = confQueue.remove()
                if (!visited.contains(extendsConf.name)) {
                    if (extendsConf.visible || extendsConf == configuration) { // Best hint we can use to know to skip a transitive set of dependencies
                        inheritedDependencies.addCollection(extendsConf.getDependencies());
                        confQueue.addAll(extendsConf.extendsFrom)
                    }
                    visited.add(extendsConf.name)
                }
            }

            def allPublicDependencies = new DefaultDependencySet(String.format("%s all public dependencies", configuration.name), inheritedDependencies);
            allPublicDependencies
        }
    }

    public Set<ModuleDependency> getDependencies() {
        // TODO See what kind of dependencies could also be here.
        // FYI ProjectDependency extends ModuleDependency
        deferredDependencies?deferredDependencies.collectPublicDependencies().withType(ModuleDependency):[] as Set
    }

}