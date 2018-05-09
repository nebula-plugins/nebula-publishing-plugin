package nebula.plugin.publishing.verification

import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import spock.lang.Specification

class VerificationReportGeneratorSpec extends Specification {

    def 'simple one project with one violation error'() {
        given:
        def projectStatus = 'release'
        def violations = [
                root: [create('foo', 'bar', '1.0-SNAPSHOT')]
        ]

        when:
        def result = new VerificationReportGenerator().generateReport(violations, projectStatus)

        then:
        assertHeader(result, projectStatus)
        assertProjects(result, violations)
        assertOptions(result, projectStatus)
        assertSingleProjectIgnore(result, violations)
    }

    def 'one project with more violations'() {
        given:
        def projectStatus = 'release'
        def violations = [
                root: [create('foo', 'bar', '1.0-SNAPSHOT'), create('baz', 'giz', '2.0-rc.1', 'candidate')]
        ]

        when:
        def result = new VerificationReportGenerator().generateReport(violations, projectStatus)

        then:
        assertHeader(result, projectStatus)
        assertProjects(result, violations)
        assertOptions(result, projectStatus)
        assertSingleProjectIgnore(result, violations)
    }

    def 'multi-module project, each module with one violation'() {
        given:
        def projectStatus = 'release'
        def violations = [
                root: [create('foo', 'bar', '1.0-SNAPSHOT')],
                module1: [create('baz', 'giz', '2.0-rc.1', 'candidate')],
                module2: []
        ]

        when:
        def result = new VerificationReportGenerator().generateReport(violations, projectStatus)

        then:
        assertHeader(result, projectStatus)
        assertProjects(result, violations)
        assertOptions(result, projectStatus)
        assertMultiModuleProjectIgnore(result, violations)
    }

    def 'multi-module project with more violations'() {
        given:
        def projectStatus = 'release'
        def violations = [
                root: [create('foo', 'bar', '1.0-SNAPSHOT'), create('baz', 'fiz', '2.0-rc.1', 'candidate')],
                module1: [create('baz', 'giz', '2.0-rc.1', 'candidate'), create('foo', 'zig', '1.2-SNAPSHOT')]
        ]

        when:
        def result = new VerificationReportGenerator().generateReport(violations, projectStatus)

        then:
        assertHeader(result, projectStatus)
        assertProjects(result, violations)
        assertOptions(result, projectStatus)
        assertMultiModuleProjectIgnore(result, violations)
    }

    def 'multi-module project with more violations with overlapping violating dependencies'() {
        given:
        def projectStatus = 'release'
        def violations = [
                root: [create('foo', 'bar', '1.0-SNAPSHOT'), create('baz', 'fiz', '2.0-rc.1', 'candidate'), create('foo', 'zig', '1.2-SNAPSHOT')],
                module1: [create('baz', 'giz', '2.0-rc.1', 'candidate'), create('foo', 'zig', '1.2-SNAPSHOT')]
        ]

        when:
        def result = new VerificationReportGenerator().generateReport(violations, projectStatus)

        then:
        assertHeader(result, projectStatus)
        assertProjects(result, violations)
        assertOptions(result, projectStatus)
        assertMultiModuleProjectIgnore(result, violations)
    }

    StatusVerificationViolation create(String group, String name, String version,
                                       String status = "integration", List<String> statusScheme = ['integration', 'candidate', 'release']) {
        new StatusVerificationViolation(id: DefaultModuleVersionIdentifier.newId(group, name, version),metadata: Mock(ComponentMetadataDetails) {
            getStatus() >> {
                status
            }
            getStatusScheme() >> {
                statusScheme
            }
        })
    }

    void assertHeader(String report, String targetStatus) {
        assert report.contains("Following dependencies have incorrect status lower then your current project status '$targetStatus':")
    }

    void assertProjects(String report, Map<String, List<StatusVerificationViolation>> violationsPerProject) {
        violationsPerProject.each { project, violations ->
            if (!violations.isEmpty()) {
                assert report.contains("Dependencies for $project:")
                violations.each { violation ->
                    assert report.contains("    '$violation.id.module' resolved to version '$violation.id.version', status: '$violation.metadata.status' in status scheme: $violation.metadata.statusScheme")
                }
            }
        }
    }

    void assertOptions(String report, String targetStatus) {
        assert report.contains("*** OPTIONS ***\n" +
                "1) Use a specific module version with higher status or 'latest.$targetStatus'.\n" +
                "2) Ignore this check with the following build.gradle configurations.")
    }

    void assertSingleProjectIgnore(String report, Map<String, List<StatusVerificationViolation>> violationsPerProject) {
        assert report.contains("You have a single module project - place following configuration after plugins section" +
                " in your project build.gradle file\n\n" +
                "nebulaPublishVerification {")
        assertIgnores(report, violationsPerProject)
    }

    void assertMultiModuleProjectIgnore(String report, Map<String, List<StatusVerificationViolation>> violationsPerProject) {
        assert report.contains("You have a multi module project - place following configuration after plugins section in your root project build.gradle file\n\n" +
                "allprojects {\n" +
                "    nebulaPublishVerification {")
        assertIgnores(report, violationsPerProject)
    }

    void assertIgnores(String report, Map<String, List<StatusVerificationViolation>> violationsPerProject) {
        def ignores = report.readLines().findAll { it.contains('ignore(') }
        //ignores must be unique
        assert ignores.size() == ignores.toSet().size()
        violationsPerProject.values().flatten().each { violation ->
            assert ignores.any { ignore -> ignore.contains(violation.id.module.toString()) }
        }
    }
}
