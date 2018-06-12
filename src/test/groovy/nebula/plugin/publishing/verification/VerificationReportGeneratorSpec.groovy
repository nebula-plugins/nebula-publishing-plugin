package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import spock.lang.Specification

class VerificationReportGeneratorSpec extends Specification {

    def 'simple one project with one status violation error'() {
        given:
        def projectStatus = 'release'
        def violations = [
                root: new ViolationsContainer(statusViolations: [createStatusViolation('foo', 'bar', '1.0-SNAPSHOT')])
        ]

        when:
        def result = new VerificationReportGenerator().generateReport(violations, projectStatus)

        then:
        assertStatusViolationsForProjects(result, violations, projectStatus)
        assertIgnoreHeader(result)
        assertSingleProjectIgnore(result, violations)
    }

    def 'simple one project with one version violation error'() {
        given:
        def projectStatus = 'release'
        def violations = [
                root: new ViolationsContainer(versionSelectorViolations: [createVersionViolation('foo', 'bar', '1.0+')])
        ]

        when:
        def result = new VerificationReportGenerator().generateReport(violations, projectStatus)

        then:
        assertVersionViolationsForProjects(result, violations)
        assertIgnoreHeader(result)
        assertSingleProjectIgnore(result, violations)
    }

    def 'one project with more violations'() {
        given:
        def projectStatus = 'release'
        def violations = [
                root: new ViolationsContainer(
                        statusViolations:
                                [createStatusViolation('foo', 'bar', '1.0-SNAPSHOT'), createStatusViolation('baz', 'giz', '2.0-rc.1', 'candidate')],
                        versionSelectorViolations: [createVersionViolation('foo', 'bar', '1.0+')])
        ]

        when:
        def result = new VerificationReportGenerator().generateReport(violations, projectStatus)

        then:
        assertStatusViolationsForProjects(result, violations, projectStatus)
        assertVersionViolationsForProjects(result, violations)
        assertIgnoreHeader(result)
        assertSingleProjectIgnore(result, violations)
    }

    def 'multi-module project, each module with one violation'() {
        given:
        def projectStatus = 'release'
        def violations = [
                root: new ViolationsContainer(statusViolations:[createStatusViolation('foo', 'bar', '1.0-SNAPSHOT')]),
                module1: new ViolationsContainer(statusViolations:[createStatusViolation('baz', 'giz', '2.0-rc.1', 'candidate')]),
                module2: new ViolationsContainer(statusViolations:[])
        ]

        when:
        def result = new VerificationReportGenerator().generateReport(violations, projectStatus)

        then:
        assertStatusViolationsForProjects(result, violations, projectStatus)
        assertIgnoreHeader(result)
        assertMultiModuleProjectIgnore(result, violations)
    }

    def 'multi-module project with more violations'() {
        given:
        def projectStatus = 'release'
        def violations = [
                root: new ViolationsContainer(statusViolations:[createStatusViolation('foo', 'bar', '1.0-SNAPSHOT'), createStatusViolation('baz', 'fiz', '2.0-rc.1', 'candidate')]),
                module1: new ViolationsContainer(
                        statusViolations:[createStatusViolation('baz', 'giz', '2.0-rc.1', 'candidate'), createStatusViolation('foo', 'zig', '1.2-SNAPSHOT')],
                        versionSelectorViolations: [createVersionViolation('foo', 'bar', '1.0+')]
                )
        ]

        when:
        def result = new VerificationReportGenerator().generateReport(violations, projectStatus)

        then:
        assertStatusViolationsForProjects(result, violations, projectStatus)
        assertVersionViolationsForProjects(result, violations)
        assertIgnoreHeader(result)
        assertMultiModuleProjectIgnore(result, violations)
    }

    def 'multi-module project with more violations with overlapping violating dependencies'() {
        given:
        def projectStatus = 'release'
        def violations = [
                root: new ViolationsContainer(statusViolations:
                        [createStatusViolation('foo', 'bar', '1.0-SNAPSHOT'), createStatusViolation('baz', 'fiz', '2.0-rc.1', 'candidate'), createStatusViolation('foo', 'zig', '1.2-SNAPSHOT')]),
                module1: new ViolationsContainer(statusViolations:
                        [createStatusViolation('baz', 'giz', '2.0-rc.1', 'candidate'), createStatusViolation('foo', 'zig', '1.2-SNAPSHOT')])
        ]

        when:
        def result = new VerificationReportGenerator().generateReport(violations, projectStatus)

        then:
        assertStatusViolationsForProjects(result, violations, projectStatus)
        assertIgnoreHeader(result)
        assertMultiModuleProjectIgnore(result, violations)
    }

    StatusVerificationViolation createStatusViolation(String group, String name, String version,
                                                      String status = "integration", List<String> statusScheme = ['integration', 'candidate', 'release']) {
        new StatusVerificationViolation(id: DefaultModuleVersionIdentifier.newId(group, name, version), status: status, statusScheme: statusScheme)
    }

    VersionSelectorVerificationViolation createVersionViolation(String group, String name, String version) {
        new VersionSelectorVerificationViolation(dependency: Mock(Dependency) {
            getName() >> name
            getGroup() >> group
            getVersion() >> version
        })
    }

    void assertStatusViolationsForProjects(String report, Map<String, ViolationsContainer> violationsPerProject, String targetStatus) {
        assert report.contains("Following dependencies have incorrect status lower then your current project status '$targetStatus':")
        violationsPerProject.each { project, violations ->
            if (violations.statusViolations.size() > 0) {
                assert report.contains("Dependencies for $project:")
                violations.statusViolations.each { violation ->
                    assert report.contains("    '$violation.id.module' resolved to version '$violation.id.version', status: '$violation.status' in status scheme: $violation.statusScheme")
                }
            }
        }
    }

    void assertVersionViolationsForProjects(String report, Map<String, ViolationsContainer> violationsPerProject) {
        assert report.contains("Following dependencies have version definition with patterns which resolves into unexpected version")
        violationsPerProject.each { project, violations ->
            if (violations.versionSelectorViolations.size() > 0) {
                assert report.contains("Dependencies for $project:")
                violations.versionSelectorViolations.each { violation ->
                    assert report.contains("    '$violation.dependency.group:$violation.dependency.name' with requested version '$violation.dependency.version'")
                }
            }
        }
    }

    void assertIgnoreHeader(String report) {
        assert report.contains("ATTENTION: If suggested steps for violations cannot be resolved you can ignore them using the following configurations.")
    }

    void assertSingleProjectIgnore(String report, Map<String, ViolationsContainer> violationsPerProject) {
        assert report.contains("Place following configuration at the end of your project build.gradle file")
        assert report.contains("nebulaPublishVerification {")
        assertIgnores(report, violationsPerProject)
    }

    void assertMultiModuleProjectIgnore(String report, Map<String, ViolationsContainer> violationsPerProject) {
        assert report.contains("Place following configuration at the end of your root project build.gradle file")
        assert report.contains("allprojects {\n" +
                "        nebulaPublishVerification {")
        assertIgnores(report, violationsPerProject)
    }

    void assertIgnores(String report, Map<String, ViolationsContainer> violationsPerProject) {
        def ignores = report.readLines().findAll { it.contains('ignore(') }
        //ignores must be unique
        assert ignores.size() == ignores.toSet().size()
        violationsPerProject.values().collect { it.statusViolations } .flatten().each { violation ->
            assert ignores.any { ignore -> ignore.contains(violation.id.module.toString()) }
        }
    }
}
