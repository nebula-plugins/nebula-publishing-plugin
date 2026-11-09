package nebula.plugin.publishing.settings;

import org.gradle.api.IsolatedAction;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Settings-only discovery. Project conventions apply the project plugins locally. */
public final class PublishingSettingsPlugin implements Plugin<Settings> {
    private static final String FLAG = "nebula.publishing.isolatedProjectsSupport";

    @Override
    public void apply(Settings settings) {
        if (!settings.getProviders().gradleProperty(FLAG).map(Boolean::parseBoolean).getOrElse(false)) {
            return;
        }
        settings.getGradle().settingsEvaluated(evaluated -> {
            List<String> paths = new ArrayList<>();
            collectProjects(evaluated.getRootProject(), paths);
            Collections.sort(paths);
            evaluated.getGradle().getLifecycle().beforeProject(new ConfigureProject(List.copyOf(paths)));
        });
    }

    private static void collectProjects(ProjectDescriptor project, List<String> paths) {
        paths.add(project.getPath());
        for (ProjectDescriptor child : project.getChildren()) {
            collectProjects(child, paths);
        }
    }

    /** Only JDK values cross the settings/project classpath boundary. */
    public record ConfigureProject(List<String> paths) implements IsolatedAction<Project> {
        @Override
        public void execute(Project project) {
            var extras = project.getExtensions().getExtraProperties();
            extras.set("__nebulaPublishingSettingsProtocol", 1);
            var isolated = project.getIsolated();
            if (isolated.equals(isolated.getRootProject())) {
                extras.set("__nebulaPublishingProjectPaths", paths);
            }
        }
    }
}
