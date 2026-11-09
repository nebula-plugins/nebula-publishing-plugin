package nebula.plugin.publishing.verification

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, invocation-scoped results for verification, contribution, and reporting tasks.
 *
 * <p>Only task execution populates this service. It must not register participants or
 * coordinate project configuration. Isolated tasks key results by full project path;
 * legacy callers retain their existing project-name keys.</p>
 *
 * <p>A verification result and a completed contribution are distinct states. The root
 * report requires both, preventing a disabled contribution from passing merely because
 * its verifier executed. Configuration-cache reuse creates a fresh service, so producer
 * tasks must rerun rather than restore independently cached results.</p>
 */
abstract class VerificationViolationsCollectorService implements BuildService<BuildServiceParameters.None>, AutoCloseable {
    private final Map<String, ViolationsContainer> collector
    private final Set<String> completedContributions = ConcurrentHashMap.newKeySet()

    VerificationViolationsCollectorService() {
        this.collector = new ConcurrentHashMap<>()
    }

    /** Records a verification result or a non-Java project's empty result during execution. */
    void addProject(String projectName, ViolationsContainer violationsContainer) {
        collector.put(projectName, violationsContainer)
    }

    /** Current invocation's results; consumers must not query these during configuration. */
    Map<String, ViolationsContainer> getCollector() {
        return collector
    }

    /** Marks successful execution of the contribution task after its result is available. */
    void completeContribution(String projectPath) {
        completedContributions.add(projectPath)
    }

    /** Whether the contribution task completed in this invocation, independently of its verifier. */
    boolean hasContribution(String projectPath) {
        completedContributions.contains(projectPath)
    }

    @Override
    void close() {
        collector.clear()
        completedContributions.clear()
    }
}
