package nebula.plugin.publishing.verification

class ViolationsContainer {
    List<StatusVerificationViolation> statusViolations = []
    List<VersionSelectorVerificationViolation> versionSelectorViolations = []

    boolean hasViolations() {
        ! statusViolations.isEmpty() || ! versionSelectorViolations.isEmpty()
    }
}
