package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.attributes.Attribute

class StatusVerification {

    private static final Attribute STATUS = Attribute.of("org.gradle.status", String.class)

    Set<ModuleIdentifier> ignore
    Set<String> ignoreGroups
    def targetStatus

    StatusVerification(Set<ModuleIdentifier> ignore, Set<String> ignoreGroups, def targetStatus) {
        this.ignore = ignore
        this.ignoreGroups = ignoreGroups
        this.targetStatus = targetStatus
    }

    List<StatusVerificationViolation> verify(Set<ResolvedDependencyResult> firstLevelDependencies) {
        Set<ResolvedDependencyResult> forVerification = firstLevelDependencies
                .findAll { ! ignoreGroups.contains(it.selected.moduleVersion.group) }
                .findAll { ! ignore.contains(it.selected.moduleVersion.module) }
        forVerification.collect {
            ModuleVersionIdentifier id = it.selected.moduleVersion
            //we cannot collect metadata for dependencies on another modules in multimodule build
            if (!(it.selected.id instanceof ProjectComponentIdentifier) && hasStatusScheme(it)) {
                def statusScheme = getStatusScheme(it)
                int projectStatus = statusScheme.indexOf(targetStatus)
                def status = getStatus(it)
                int moduleStatus = statusScheme.indexOf(status)
                if (moduleStatus < projectStatus) {
                    new StatusVerificationViolation(id: id, status: status, statusScheme: statusScheme)
                } else
                    null
            } else
                null
        }.findAll { it != null }
    }

    private String getStatus(ResolvedDependencyResult resolvedDependencyResult) {
        resolvedDependencyResult.selected.variant.attributes.getAttribute(STATUS)
    }

    private boolean hasStatusScheme(ResolvedDependencyResult resolvedDependencyResult) {
        //some dependencies can miss setting status scheme because gradle dont invoke metadata rules for
        //dependencies which are also defined in buildscript classpath
        resolvedDependencyResult.selected.variant.attributes.getAttribute(PublishVerificationPlugin.STATUS_SCHEME) != null
    }

    private List<String> getStatusScheme(ResolvedDependencyResult resolvedDependencyResult) {
        resolvedDependencyResult.selected.variant.attributes.getAttribute(PublishVerificationPlugin.STATUS_SCHEME).split(',').toList()
    }
}
