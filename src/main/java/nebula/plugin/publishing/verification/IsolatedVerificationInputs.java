package nebula.plugin.publishing.verification;

import org.gradle.api.Project;
import org.gradle.api.project.IsolatedProject;
import org.gradle.api.provider.ProviderFactory;

import java.util.List;

/**
 * Configuration-time inputs and project discovery for isolated publication verification.
 *
 * <p>Only immutable project identities cross project boundaries. Callers wire tasks by
 * absolute path and retain no foreign project models in task execution state.</p>
 *
 * @see Project#getIsolated()
 * @see IsolatedProject
 */
final class IsolatedVerificationInputs {
    static final String FLAG = "nebula.publishing.isolatedProjectsSupport";
    static final String CONTRIBUTION_TASK = "collectPublicationVerification";

    private IsolatedVerificationInputs() {}

    /**
     * Reads the build-wide opt-in through Gradle's tracked property provider.
     *
     * @param providers the current project's provider factory
     * @return whether experimental verification is enabled; defaults to false
     */
    static boolean enabled(ProviderFactory providers) {
        return providers.gradleProperty(FLAG).map(Boolean::parseBoolean).getOrElse(false);
    }

    /**
     * Discovers every project in the current build, including the root and non-Java projects.
     *
     * <p>Enumeration obtains project references; only {@link IsolatedProject#getPath()}
     * is read from their isolated views. Do not replace this with reads of foreign
     * tasks, plugins, extensions, configurations, or other mutable project state.</p>
     *
     * @param rootProject the current build's root project; call only during its configuration
     * @return an immutable list of absolute project paths in deterministic order
     */
    static List<String> discoveredProjects(Project rootProject) {
        // Enumerate identities only; never inspect or configure another project's mutable model.
        return rootProject.getAllprojects().stream()
                .map(Project::getIsolated)
                .map(IsolatedProject::getPath)
                .sorted()
                .toList();
    }

    /**
     * Builds a task dependency without looking up or configuring the owning project.
     *
     * @param path an absolute project path, including {@code :} for the root
     * @return the absolute path of that project's contribution task
     */
    static String contributionTask(String path) {
        return (path.equals(":") ? ":" : path + ":") + CONTRIBUTION_TASK;
    }
}
