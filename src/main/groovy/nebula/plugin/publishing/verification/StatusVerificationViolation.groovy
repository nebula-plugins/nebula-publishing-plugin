package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier

class StatusVerificationViolation {
    /*Identification of module which has a lower status then project status*/
    ModuleVersionIdentifier id
    /*Metadata which contains actual status of resolved module and its statusScheme*/
    ComponentMetadataDetails metadata
}
