package nebula.plugin.publishing.verification;

import org.gradle.api.Project;
import org.gradle.api.project.IsolatedProject;
import org.gradle.api.provider.ProviderFactory;

import java.util.List;

/** Build-wide inputs and read-only discovery for isolated publication verification. */
final class IsolatedVerificationInputs {
    static final String FLAG = "nebula.publishing.isolatedProjectsSupport";
    static final String CONTRIBUTION_TASK = "collectPublicationVerification";

    private IsolatedVerificationInputs() {}

    static boolean enabled(ProviderFactory providers) {
        return providers.gradleProperty(FLAG).map(Boolean::parseBoolean).getOrElse(false);
    }

    static List<String> discoveredProjects(Project rootProject) {
        // Enumerate identities only; never inspect or configure another project's mutable model.
        return rootProject.getAllprojects().stream()
                .map(Project::getIsolated)
                .map(IsolatedProject::getPath)
                .sorted()
                .toList();
    }

    static String contributionTask(String path) {
        return (path.equals(":") ? ":" : path + ":") + CONTRIBUTION_TASK;
    }
}
