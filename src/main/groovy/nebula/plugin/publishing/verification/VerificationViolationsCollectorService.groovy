package nebula.plugin.publishing.verification

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

import java.util.concurrent.ConcurrentHashMap

abstract class VerificationViolationsCollectorService implements BuildService<BuildServiceParameters.None>, AutoCloseable {
    private final Map<String, ViolationsContainer> collector

    VerificationViolationsCollectorService() {
        this.collector = new ConcurrentHashMap<>()
    }

    void addProject(String projectName, ViolationsContainer violationsContainer) {
        collector.put(projectName, violationsContainer)
    }

    Map<String, ViolationsContainer> getCollector() {
        return collector
    }

    @Override
    void close() {
        collector.clear()
    }
}
