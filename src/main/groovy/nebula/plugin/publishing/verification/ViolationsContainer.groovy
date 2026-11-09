package nebula.plugin.publishing.verification

class ViolationsContainer {
    String targetStatus
    List<StatusVerificationViolation> statusViolations = []
    List<VersionSelectorVerificationViolation> versionSelectorViolations = []

    boolean hasViolations() {
        ! statusViolations.isEmpty() || ! versionSelectorViolations.isEmpty()
    }
}
