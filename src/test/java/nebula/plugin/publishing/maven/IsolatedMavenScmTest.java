package nebula.plugin.publishing.maven;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Strict SCM isolation and cache checks against the released plugin dependencies. */
class IsolatedMavenScmTest {
    private static final String ORIGIN = "git@example.org:team/shared.git";
    private static final String POM = "generatePomFileForLibraryPublication";
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({"9.0.0,true", "9.0.0,false", "9.2.1,true", "9.2.1,false"})
    void sharedRepositorySupportsRootAndChildOnlyApplication(String version, boolean root) throws Exception {
        fixture(root);
        repository(project(), ORIGIN);
        checkTwice(version, ":api:" + POM, ":platform:app:" + POM);
        assertPom("api", ORIGIN, "https://example.org/team/shared");
        assertPom("platform/app", ORIGIN, "https://example.org/team/shared");
    }

    @Test
    void lateLocalOverrideIsUsedForChildPom() throws Exception {
        fixture(true);
        repository(project(), ORIGIN);
        append("build.gradle", "scminfo.origin.set('https://example.org/root.git')\n");
        append("api/build.gradle", "afterEvaluate { scminfo.origin.set('https://example.org/api.git') }\n");
        checkTwice("9.2.1", ":" + POM, ":api:" + POM);
        assertPom("", "https://example.org/root.git", "https://example.org/root");
        assertPom("api", "https://example.org/api.git", "https://example.org/api");
    }

    @Test
    void rootOverrideDoesNotLeakIntoChild() throws Exception {
        fixture(true);
        repository(project(), ORIGIN);
        append("build.gradle", "scminfo.origin.set('https://example.org/root.git')\n");
        checkTwice("9.2.1", ":platform:app:" + POM);
        assertPom("platform/app", ORIGIN, "https://example.org/team/shared");
    }

    @Test
    void nestedRepositoryUsesItsOwnOrigin() throws Exception {
        fixture(true);
        repository(project(), ORIGIN);
        repository(project().resolve("platform/app"), "https://example.org/nested.git");
        checkTwice("9.2.1", ":api:" + POM, ":platform:app:" + POM);
        assertPom("api", ORIGIN, "https://example.org/team/shared");
        assertPom("platform/app", "https://example.org/nested.git", "https://example.org/nested");
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.0.0", "9.2.1"})
    void noGitFallbackSurvivesCacheReuse(String version) throws Exception {
        fixture(true);
        checkTwice(version, ":api:" + POM, ":platform:app:" + POM);
        assertPom("api", "LOCAL", "");
        assertPom("platform/app", "LOCAL", "");
    }

    @Test
    void changingRemoteRefreshesPomWithConfigurationCache() throws Exception {
        fixture(true);
        repository(project(), ORIGIN);
        checkTwice("9.2.1", ":api:" + POM);
        git(project(), "remote", "set-url", "origin", "https://example.org/changed.git");
        run("9.2.1", true, ":api:" + POM);
        assertPom("api", "https://example.org/changed.git", "https://example.org/changed");
    }

    @Test
    void helpDoesNotQueryOrigin() throws Exception {
        fixture(false);
        repository(project(), ORIGIN);
        write("api/build.gradle", """
            plugins {
                id 'com.netflix.nebula.info-scm'
                id 'maven-publish'
            }
            scminfo.origin.set(providers.provider { throw new GradleException('origin queried') })
            apply plugin: 'com.netflix.nebula.maven-scm'
            publishing.publications.create('library', MavenPublication)
            """);
        checkTwice("9.2.1", ":api:help");
    }

    @Test
    void nonConvertibleGitOriginIsPreserved() throws Exception {
        fixture(true);
        repository(project(), ORIGIN);
        // Override after collector normalization to exercise the existing URL fallback.
        append("api/build.gradle", "scminfo.origin.set('https://example.org/team/shared')\n");
        checkTwice("9.2.1", ":api:" + POM);
        assertPom("api", "https://example.org/team/shared", "https://example.org/team/shared");
    }

    @Test
    void flagOffKeepsRootOverrideInheritance() throws Exception {
        fixture(true);
        repository(project(), ORIGIN);
        write("gradle.properties", "nebula.publishing.isolatedProjectsSupport=false\nnebula.info.isolatedProjectsSupport=false\n");
        append("build.gradle", "scminfo.origin.set('https://example.org/root.git')\n");
        append("api/build.gradle", "scminfo.origin.set('https://example.org/api.git')\n");
        run("9.2.1", false, ":api:" + POM);
        assertPom("api", "https://example.org/root.git", "https://example.org/root");
    }

    private void checkTwice(String version, String... tasks) {
        assertTrue(run(version, true, tasks).getOutput().contains("Configuration cache entry stored."));
        assertTrue(run(version, true, tasks).getOutput().contains("Reusing configuration cache."));
    }

    private BuildResult run(String version, boolean isolated, String... tasks) {
        List<String> args = new ArrayList<>(List.of(tasks));
        args.addAll(List.of("--configuration-cache", "--configuration-cache-problems=fail", "--parallel", "--max-workers=2",
                "-Dorg.gradle.unsafe.isolated-projects=" + isolated,
                "-Dorg.gradle.java.home=" + System.getProperty("publishing.verificationTestJavaHome"), "--stacktrace"));
        Map<String, String> environment = new HashMap<>(System.getenv());
        for (String name : List.of("GIT_COMMIT", "P4CONFIG", "P4CLIENT", "WORKSPACE", "P4_CHANGELIST")) environment.remove(name);
        return GradleRunner.create().withProjectDir(project().toFile()).withPluginClasspath().withGradleVersion(version)
                .withEnvironment(environment).withArguments(args).build();
    }

    private void fixture(boolean root) throws IOException {
        write("settings.gradle", "rootProject.name='scm-fixture'\ninclude 'api', 'platform:app'\n");
        write("gradle.properties", "nebula.publishing.isolatedProjectsSupport=true\nnebula.info.isolatedProjectsSupport=true\n");
        write("build.gradle", root ? script() : "");
        write("api/build.gradle", script());
        write("platform/build.gradle", "");
        write("platform/app/build.gradle", script());
    }

    private String script() {
        return """
            plugins {
                id 'com.netflix.nebula.maven-scm'
                id 'com.netflix.nebula.info-scm'
            }
            group = 'example'
            version = '1.0'
            publishing.publications.create('library', MavenPublication)
            """;
    }

    private void assertPom(String path, String origin, String url) throws Exception {
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(project().resolve(path).resolve("build/publications/library/pom-default.xml").toFile());
        var scm = (org.w3c.dom.Element) document.getElementsByTagName("scm").item(0);
        assertNotNull(scm);
        assertEquals(origin, scm.getElementsByTagName("url").item(0).getTextContent());
        var urls = document.getDocumentElement().getElementsByTagName("url");
        // The first URL belongs to the project only when that element is present.
        assertEquals(url.isEmpty() ? 1 : 2, urls.getLength());
        if (!url.isEmpty()) assertEquals(url, urls.item(0).getTextContent());
    }

    private Path project() { return directory.resolve("project"); }

    private void write(String path, String contents) throws IOException {
        Path target = project().resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, contents);
    }

    private void append(String path, String contents) throws IOException {
        Files.writeString(project().resolve(path), Files.readString(project().resolve(path)) + contents);
    }

    private void repository(Path path, String origin) throws Exception {
        Files.createDirectories(path);
        git(path, "init", "-b", "main");
        git(path, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.org", "commit", "--allow-empty", "-m", "Fixture");
        git(path, "remote", "add", "origin", origin);
    }

    private void git(Path path, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", path.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
    }
}
