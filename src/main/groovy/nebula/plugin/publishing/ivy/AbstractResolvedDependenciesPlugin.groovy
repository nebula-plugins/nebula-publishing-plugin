package nebula.plugin.publishing.ivy

import groovy.transform.Memoized
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedDependencyResult

abstract class AbstractResolvedDependenciesPlugin implements Plugin<Project> {
    ModuleVersionIdentifier selectedModuleVersion(Project project, String scope, String group, String name) {
        def resolvedDependencies = resolvedDependencyByConfiguration(project)[scope]
        def result = resolvedDependencies.find { r ->
            def requested = r.requested
            (requested instanceof ModuleComponentSelector) &&
                    (requested.group == group) &&
                    (requested.module == name)
        }
        if (!result) {
            return null
        }
        result.selected.moduleVersion
    }

    @Memoized
    Map<String, Set<? extends ResolvedDependencyResult>> resolvedDependencyByConfiguration(Project project) {
        ConfigurationContainer configurations = project.configurations
        Map<String, Set<? extends ResolvedDependencyResult>> dependencyMap = [:]
        dependencyMap['runtime'] = resolvedDependencies(configurations.runtimeClasspath)
        dependencyMap['compile'] = resolvedDependencies(configurations.compileClasspath)
        dependencyMap['compileOnly'] = resolvedDependencies(configurations.compileOnly)
        dependencyMap['test'] = resolvedDependencies(configurations.testRuntimeClasspath)
        return dependencyMap
    }

    Set<? extends ResolvedDependencyResult> resolvedDependencies(Configuration configuration) {
        configuration.incoming.resolutionResult.allDependencies.findAll {
            it instanceof ResolvedDependencyResult
        } as Set<? extends ResolvedDependencyResult>
    }
}
