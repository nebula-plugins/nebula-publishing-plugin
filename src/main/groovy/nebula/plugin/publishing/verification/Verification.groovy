package nebula.plugin.publishing.verification

import org.gradle.api.BuildCancelledException
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency

class Verification {
    Set<ModuleIdentifier> ignore
    Set<String> ignoreGroups
    def targetStatus

    Verification(Set<ModuleIdentifier> ignore, Set<String> ignoreGroups, def targetStatus) {
        this.ignore = ignore
        this.ignoreGroups = ignoreGroups
        this.targetStatus = targetStatus
    }

    void verify(Set<ResolvedDependency> firstLevelDependencies,
                Map<ModuleVersionIdentifier, ComponentMetadataDetails> details,
                Map<String, DefinedDependency> definedDependencies = Collections.emptyMap(),
                Closure<String> errorMessageProducer = defaultErrorMessageProducer) {
        Set<ResolvedDependency> forVerification = firstLevelDependencies
                .findAll { ! ignoreGroups.contains(it.moduleGroup) }
                .findAll { ! ignore.contains(it.module.id.module) }
        forVerification.each {
            ModuleVersionIdentifier id = it.module.id
            ComponentMetadataDetails metadata = details[id]
            //we cannot collect metadata for dependencies on another modules in multimodule build
            if (metadata != null) {
                int projectStatus = metadata.statusScheme.indexOf(targetStatus)
                int moduleStatus = metadata.statusScheme.indexOf(metadata.status)
                if (moduleStatus < projectStatus) {
                    def (String definedDependencyToPrint, String configuration) = getDefinedDependencyWithConfiguration(definedDependencies, id)
                    throw new BuildCancelledException(errorMessageProducer(id, metadata, definedDependencyToPrint, configuration))
                }
            }
        }
    }

    static List getDefinedDependencyWithConfiguration(Map<String, DefinedDependency> definedDependencies, ModuleVersionIdentifier id) {
        def groupAndName = "${id.group}:${id.name}"
        DefinedDependency definedDependency = definedDependencies[groupAndName.toString()]
        if (definedDependency != null) {
            return [groupAndName + "${definedDependency.preferredVersion != null ? ':' : ''}${definedDependency.preferredVersion ?: ''}",
                    definedDependency.configuration]
        } else {
            //fallback in case we cannot find original definition e.g. when final dependency was provided by a substitution rule
            return ["foo:bar:1.0", 'compile']
        }
    }

    private Closure<String> defaultErrorMessageProducer = { ModuleVersionIdentifier id, ComponentMetadataDetails metadata,
                                                            String definedDependencyToPrint, String configuration ->
        """
        Module '${id.group}:${id.name}' resolved to version '${id.version}'.
        It cannot be used because it has status: '${metadata.status}' which is less then your current project status: '${targetStatus}' in your status scheme: ${metadata.statusScheme}.
        *** OPTIONS ***
        1) Use a specific module version with higher status or 'latest.${targetStatus}'.
        2) Ignore this check with ONE of the following build.gradle configurations.
        
          a) Single module project - place following configuration in your project build.gradle file
          
          nebulaPublishVerification {
              ignore('$definedDependencyToPrint')
          }
          
          b) Multi module project - place following configuration in your root project build.gradle file
          
          allprojects {
              nebulaPublishVerification {
                  ignore('$definedDependencyToPrint')
              }
          }

          """.stripIndent()
    }
}
