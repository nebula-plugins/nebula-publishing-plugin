package nebula.plugin.publishing.verification

import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class VerifyPublicationTaskSpec extends Specification {

    private static String DUMMY_LIBRARY = 'foo:bar:1.0'

    @Unroll
    def 'test releasable combinations of statuses library=#libraryStatus project=#projectStatus'() {
        given:
        Project project = ProjectBuilder.builder().build()
        def task = setupProjectAndTask(project, libraryStatus, projectStatus)

        when:
        task.verifyDependencies()

        then:
        noExceptionThrown()
        def holderExtension =  project.getGradle().getSharedServices().registerIfAbsent("verificationViolationsCollectorService", VerificationViolationsCollectorService.class, spec -> {
        }).get()
        holderExtension.collector.size() == 1
        def violations = holderExtension.collector[project.name]
        violations.statusViolations.size() == 0

        where:
        libraryStatus | projectStatus
        'integration' | 'integration'
        'milestone'   | 'integration'
        'milestone'   | 'milestone'
        'release'     | 'integration'
        'release'     | 'milestone'
        'release'     | 'release'

    }

    @Unroll
    def 'test error collection when combinations of statuses library=#libraryStatus project=#projectStatus'() {
        given:
        Project project = ProjectBuilder.builder().build()
        def task = setupProjectAndTask(project, libraryStatus, projectStatus)

        when:
        task.verifyDependencies()

        then:
        noExceptionThrown()
        def holderExtension =  project.getGradle().getSharedServices().registerIfAbsent("verificationViolationsCollectorService", VerificationViolationsCollectorService.class, spec -> {
        }).get()
        holderExtension.collector.size() == 1
        def violations = holderExtension.collector[project.name]
        violations.statusViolations.size() == 1
        def violation = violations.statusViolations.first()
        violation.id.group == 'foo'
        violation.id.name == 'bar'
        violation.status == libraryStatus

        where:
        libraryStatus | projectStatus
        'integration' | 'milestone'
        'integration' | 'release'
        'milestone'   | 'release'
    }

    def 'test ignore through specific name and group'() {
        given:
        Project project = ProjectBuilder.builder().build()
        def task = setupProjectAndTask(project, 'integration', 'release')
        project.dependencies {
            runtimeOnly 'foo:bar:1.0+'
        }
        task.configure {
            ignore = [DefaultModuleIdentifier.newId('foo', 'bar')] as Set
        }

        when:
        task.verifyDependencies()

        then:
        noExceptionThrown()
        def holderExtension =  project.getGradle().getSharedServices().registerIfAbsent("verificationViolationsCollectorService", VerificationViolationsCollectorService.class, spec -> {
        }).get()
        holderExtension.collector.size() == 1
        def violations = holderExtension.collector[project.name]
        violations.statusViolations.size() == 0
        violations.versionSelectorViolations.size() == 0
    }

    def 'test ignore through group'() {
        given:
        Project project = ProjectBuilder.builder().build()
        def task = setupProjectAndTask(project, 'integration', 'release')
        project.dependencies {
            runtimeOnly 'foo:bar:1.0+'
        }
        task.configure {
            ignoreGroups = ['foo'] as Set
        }

        when:
        task.verifyDependencies()

        then:
        noExceptionThrown()
        def holderExtension =  project.getGradle().getSharedServices().registerIfAbsent("verificationViolationsCollectorService", VerificationViolationsCollectorService.class, spec -> {
        }).get()
        holderExtension.collector.size() == 1
        def violations = holderExtension.collector[project.name]
        violations.statusViolations.size() == 0
        violations.versionSelectorViolations.size() == 0
    }

    Task setupProjectAndTask(Project project, String libraryStatus, String projectStatus) {
        Provider<VerificationViolationsCollectorService> verificationViolationsCollectorServiceProvider = project.getGradle().getSharedServices().registerIfAbsent("verificationViolationsCollectorService", VerificationViolationsCollectorService.class, spec -> {
        })
        project.plugins.apply(JavaPlugin)
        project.status = projectStatus

        populateAndSetRepository(project, libraryStatus)
        createConfigurations(project)

        def task = project.task('verify', type: VerifyPublicationTask)
        task.configure {
            verificationViolationsCollectorService.set(verificationViolationsCollectorServiceProvider)
            usesService(verificationViolationsCollectorServiceProvider)
            ignore.set(Collections.emptySet())
            ignoreGroups.set(Collections.emptySet())
            resolvedComponentResultProvider = project.configurations.named(project.sourceSets.main.getRuntimeClasspathConfigurationName()).get().incoming.resolutionResult.rootComponent
            definedDependencies.set(project.configurations.collect { Configuration configuration ->
                configuration.dependencies
            }.flatten().collect { Dependency dependency -> new DeclaredDependency(dependency.group, dependency.name, dependency.version) } as List<DeclaredDependency>)
            projectName.set(project.name)
            targetStatus.set(project.status.toString())
        }
    }

    private void populateAndSetRepository(Project project, String libraryStatus) {
        DependencyGraphBuilder builder = new DependencyGraphBuilder()
        builder.addModule(new ModuleBuilder(DUMMY_LIBRARY).setStatus(libraryStatus).build())
        DependencyGraph graph = builder.build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestIvyRepo()

        project.repositories {
            ivy {
                url generator.getIvyRepoUrl()
                patternLayout {
                    ivy '[organisation]/[module]/[revision]/[module]-[revision]-ivy.[ext]'
                    artifact '[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]'
                    m2compatible = true
                }
            }
        }
    }

    private void createConfigurations(Project project) {
        project.dependencies {
            runtimeOnly DUMMY_LIBRARY
        }
        project.dependencies.components.all(StatusSchemaAttributeRule)
    }
}
