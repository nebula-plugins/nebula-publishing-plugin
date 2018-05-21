package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult

class StatusVerification {
    Set<ModuleIdentifier> ignore
    Set<String> ignoreGroups
    def targetStatus

    StatusVerification(Set<ModuleIdentifier> ignore, Set<String> ignoreGroups, def targetStatus) {
        this.ignore = ignore
        this.ignoreGroups = ignoreGroups
        this.targetStatus = targetStatus
    }

    List<StatusVerificationViolation> verify(Set<ResolvedDependencyResult> firstLevelDependencies,
                                             Map<ModuleVersionIdentifier, ComponentMetadataDetails> details) {
        Set<ResolvedDependencyResult> forVerification = firstLevelDependencies
                .findAll { ! ignoreGroups.contains(it.selected.moduleVersion.group) }
                .findAll { ! ignore.contains(it.selected.moduleVersion.module) }
        forVerification.collect {
            ModuleVersionIdentifier id = it.selected.moduleVersion
            ComponentMetadataDetails metadata = details[id]
            //we cannot collect metadata for dependencies on another modules in multimodule build
            if (metadata != null) {
                int projectStatus = metadata.statusScheme.indexOf(targetStatus)
                int moduleStatus = metadata.statusScheme.indexOf(metadata.status)
                if (moduleStatus < projectStatus) {
                    new StatusVerificationViolation(id: id, metadata: metadata)
                } else
                    null
            } else
                null
        }.findAll { it != null }
    }
}
