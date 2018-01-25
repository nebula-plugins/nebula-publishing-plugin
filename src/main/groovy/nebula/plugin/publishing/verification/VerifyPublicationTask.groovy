package nebula.plugin.publishing.verification

import groovy.transform.Immutable
import org.gradle.api.BuildCancelledException
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class VerifyPublicationTask extends DefaultTask {

    @Input
    Map<ModuleVersionIdentifier, ComponentMetadataDetails> details
    @Input
    Set<ModuleIdentifier> ignore
    @Input
    Set<String> ignoreGroups

    @TaskAction
    void verifyDependencies() {
        Configuration runtimeClasspath = project.configurations.runtimeClasspath
        Map<String, DefinedDependency> definedDependencies = collectDefinedDependencies(runtimeClasspath, [:])
        Set<ResolvedDependency> firstLevel = runtimeClasspath.resolvedConfiguration.firstLevelModuleDependencies
        Set<ResolvedDependency> forVerification = firstLevel
                .findAll { ! ignoreGroups.contains(it.moduleGroup) }
                .findAll { ! ignore.contains(it.module.id.module) }
        forVerification.each {
            ModuleVersionIdentifier id = it.module.id
            ComponentMetadataDetails metadata = details[id]
            int projectStatus = metadata.statusScheme.indexOf(project.status)
            int moduleStatus = metadata.statusScheme.indexOf(metadata.status)
            if (moduleStatus < projectStatus) {
                DefinedDependency definedDependency = definedDependencies["${id.group}:${id.name}".toString()]
                def (String definedDependencyToPrint, String configuration) = getDefinedDependencyWithConfiguration(definedDependency, id)
                throw new BuildCancelledException("""
                    Module '${id.group}:${id.name}' resolved to version '${id.version}'.
                    It cannot be used because it has status: '${metadata.status}' which is less then your current project status: '${project.status}' in your status scheme: ${metadata.statusScheme}.
                    *** OPTIONS ***
                    1) Use specific version with higher status or 'latest.${project.status}'.
                    2) ignore this check with "${configuration} nebulaPublishVerification.ignore('$definedDependencyToPrint')".
                    """.stripIndent())
            }
        }
    }

    List getDefinedDependencyWithConfiguration(DefinedDependency definedDependency, ModuleVersionIdentifier id) {
        if (definedDependency != null) {
            return ["${id.group}:${id.name}" +
                    "${definedDependency.preferredVersion != null ? ':' : ''}${definedDependency.preferredVersion ?: ''}",
                    definedDependency.configuration]
        } else {
            //fallback in case we cannot find original definition e.g. when final dependency was provided by a substitution rule
            return ["foo:bar:1.0", 'compile']
        }
    }

    Map<String, DefinedDependency> collectDefinedDependencies(Configuration parentConfiguration, Map<String, DefinedDependency> collector) {
        parentConfiguration.extendsFrom.each {
            collectDefinedDependencies(it, collector)
        }
        parentConfiguration.getDependencies().each {
            if (it instanceof ExternalDependency) {
                ExternalDependency dependency = (ExternalDependency) it
                String preferredVersion = dependency.getVersionConstraint().preferredVersion
                collector.put("${it.group}:${it.name}".toString(), new DefinedDependency(parentConfiguration.name, preferredVersion))
            }
        }
        return collector
    }

    @Immutable
    private static class DefinedDependency {
        String configuration
        String preferredVersion
    }
}
