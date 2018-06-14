package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier

class StatusVerificationViolation {
    /*Identification of module which has a lower status then project status*/
    ModuleVersionIdentifier id
    /*Violating status*/
    String status
    /*statusScheme of violating module*/
    List<String> statusScheme
}
