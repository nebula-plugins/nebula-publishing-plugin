package nebula.plugin.publishing.verification;

import org.gradle.api.GradleException;
import org.gradle.api.provider.ProviderFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Build-wide inputs read independently by each project; no project discovery is needed. */
final class IsolatedVerificationInputs {
    static final String FLAG = "nebula.publishing.isolatedProjectsSupport";
    static final String PROJECTS = "nebula.publishing.verification.projects";

    private IsolatedVerificationInputs() {}

    static boolean enabled(ProviderFactory providers) {
        return providers.gradleProperty(FLAG).map(Boolean::parseBoolean).getOrElse(false);
    }

    static List<String> participants(ProviderFactory providers) {
        String value = providers.gradleProperty(PROJECTS).getOrElse(":");
        List<String> paths = new ArrayList<>(Arrays.asList(value.split(",", -1)));
        for (String path : paths) {
            if (!canonical(path)) {
                throw new GradleException(PROJECTS + " must contain comma-separated absolute project paths "
                        + "without whitespace (for example :api,:platform:app); invalid path '" + path + "'.");
            }
        }
        if (new HashSet<>(paths).size() != paths.size()) {
            throw new GradleException(PROJECTS + " must not contain duplicate project paths.");
        }
        Collections.sort(paths);
        return Collections.unmodifiableList(paths);
    }

    private static boolean canonical(String path) {
        if (path.equals(":")) return true;
        if (!path.matches(":[^:\\s/\\\\<>\"?*|]+(?::[^:\\s/\\\\<>\"?*|]+)*")) return false;
        return Arrays.stream(path.substring(1).split(":"))
                .noneMatch(segment -> segment.equals(".") || segment.equals(".."));
    }

    static String verificationTask(String path) {
        return (path.equals(":") ? ":" : path + ":") + "verifyPublication";
    }
}
