package nebula.plugin.publishing.verification;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.ProviderFactory;

import java.util.List;

/** JDK-only metadata protocol shared with the independent settings artifact. */
final class IsolatedVerificationInputs {
    static final String FLAG = "nebula.publishing.isolatedProjectsSupport";
    static final String CONTRIBUTION_TASK = "collectPublicationVerification";

    private IsolatedVerificationInputs() {}

    static boolean enabled(ProviderFactory providers) {
        return providers.gradleProperty(FLAG).map(Boolean::parseBoolean).getOrElse(false);
    }

    static void requireSettings(Project project) {
        var extras = project.getExtensions().getExtraProperties();
        if (!extras.has("__nebulaPublishingSettingsProtocol")
                || !Integer.valueOf(1).equals(extras.get("__nebulaPublishingSettingsProtocol"))) {
            throw new GradleException("Apply com.netflix.nebula.publishing.settings in settings before enabling "
                    + FLAG + ". Project conventions must apply com.netflix.nebula.publish-verification "
                    + "locally to every project, including the root.");
        }
    }

    @SuppressWarnings("unchecked")
    static List<String> discoveredProjects(Project rootProject) {
        return (List<String>) rootProject.getExtensions().getExtraProperties().get("__nebulaPublishingProjectPaths");
    }

    static String contributionTask(String path) {
        return (path.equals(":") ? ":" : path + ":") + CONTRIBUTION_TASK;
    }
}
