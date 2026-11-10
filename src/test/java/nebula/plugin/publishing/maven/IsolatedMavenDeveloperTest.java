package nebula.plugin.publishing.maven;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the released contacts plugin through generated Maven developer metadata. */
class IsolatedMavenDeveloperTest {
    private static final String POM = "generatePomFileForLibraryPublication";
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({"9.0.0,true", "9.0.0,false", "9.2.1,true", "9.2.1,false"})
    void localContactsSupportRootAndChildOnlyApplication(String version, boolean root) throws Exception {
        fixture(root);
        List<String> tasks = new ArrayList<>(List.of(":api:" + POM, ":platform:app:" + POM, ":empty:" + POM));
        if (root) tasks.add(":" + POM);
        checkTwice(version, tasks.toArray(String[]::new));
        assertDevelopers("api", List.of("api@example.org", "notify@example.org"));
        assertDeveloper("api", 0, "Api owner", "api-github", Set.of("owner", "developer"));
        assertDeveloper("api", 1, "", "notify-twitter", Set.of("notify"));
        assertDevelopers("platform/app", List.of("app@example.org"));
        assertDevelopers("empty", List.of());
        if (root) assertDevelopers("", List.of("root@example.org"));
    }

    @Test
    void changedLocalContactsRefreshMetadataAndCacheCanRegenerateDeletedPom() throws Exception {
        fixture(true);
        checkTwice("9.2.1", ":api:" + POM, ":platform:app:" + POM);
        Path siblingPom = pom("platform/app");
        String sibling = Files.readString(siblingPom);
        write("api/build.gradle", Files.readString(project().resolve("api/build.gradle"))
                .replace("Api owner", "Changed owner").replace("api@example.org", "changed@example.org"));
        run("9.2.1", true, false, ":api:" + POM, ":platform:app:" + POM);
        assertDevelopers("api", List.of("changed@example.org", "notify@example.org"));
        assertDeveloper("api", 0, "Changed owner", "api-github", Set.of("owner", "developer"));
        assertEquals(sibling, Files.readString(siblingPom));
        Files.delete(pom("api"));
        assertTrue(run("9.2.1", true, false, ":api:" + POM, ":platform:app:" + POM)
                .getOutput().contains("Reusing configuration cache."));
        assertDevelopers("api", List.of("changed@example.org", "notify@example.org"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "nebula.publishing.isolatedProjectsSupport=false\n"})
    void defaultInheritanceRemainsAvailable(String flag) throws Exception {
        fixture(true);
        write("gradle.properties", flag);
        write("api/build.gradle", script("", false));
        run("9.2.1", false, false, ":api:" + POM);
        assertDevelopers("api", List.of("root@example.org"));
    }

    @Test
    void publishingFlagDoesNotSilentlyDisableContactInheritance() throws Exception {
        fixture(true);
        write("api/build.gradle", script("", false));
        BuildResult result = run("9.2.1", true, true, ":api:" + POM);
        assertTrue(result.getOutput().contains("cannot access") && result.getOutput().contains("extensions"), result.getOutput());
    }

    @Test
    void pluginWithoutContactsProducesNoDeveloperEntries() throws Exception {
        fixture(false);
        write("api/build.gradle", """
            plugins { id 'com.netflix.nebula.maven-developer' }
            publishing.publications.create('library', MavenPublication)
            """);
        checkTwice("9.2.1", ":api:" + POM);
        assertDevelopers("api", List.of());
    }

    private void fixture(boolean root) throws IOException {
        write("settings.gradle", "rootProject.name='developer-fixture'\ninclude 'api', 'platform:app', 'empty'\n");
        write("gradle.properties", "nebula.publishing.isolatedProjectsSupport=true\n");
        write("build.gradle", root ? script("contact('root@example.org')", true) : "");
        write("api/build.gradle", script("""
            contact('api@example.org') {
                moniker 'Api owner'
                github 'api-github'
                twitter 'unused-twitter'
                roles 'owner', 'developer'
            }
            contact('notify@example.org') {
                twitter 'notify-twitter'
                role 'notify'
            }
            """, true));
        // Reverse plugin order and create the publication after the contacts have been configured.
        write("platform/build.gradle", "");
        write("platform/app/build.gradle", """
            plugins {
                id 'com.netflix.nebula.contacts'
                id 'com.netflix.nebula.maven-developer'
            }
            contacts {
                inheritFromParent.set(false)
                contact('app@example.org')
            }
            afterEvaluate { publishing.publications.create('library', MavenPublication) }
            """);
        write("empty/build.gradle", script("", true));
    }

    private String script(String contacts, boolean local) {
        return """
            plugins {
                id 'com.netflix.nebula.maven-developer'
                id 'com.netflix.nebula.contacts'
            }
            publishing.publications.create('library', MavenPublication)
            contacts {
            """ + (local ? "inheritFromParent.set(false)\n" : "") + contacts + "\n}\n";
    }

    private void checkTwice(String version, String... tasks) {
        assertTrue(run(version, true, false, tasks).getOutput().contains("Configuration cache entry stored."));
        assertTrue(run(version, true, false, tasks).getOutput().contains("Reusing configuration cache."));
    }

    private BuildResult run(String version, boolean isolated, boolean fail, String... tasks) {
        List<String> args = new ArrayList<>(List.of(tasks));
        args.addAll(List.of("--configuration-cache", "--configuration-cache-problems=fail", "--parallel", "--max-workers=2",
                "-Dorg.gradle.unsafe.isolated-projects=" + isolated,
                "-Dorg.gradle.java.home=" + System.getProperty("publishing.verificationTestJavaHome"), "--stacktrace"));
        GradleRunner runner = GradleRunner.create().withProjectDir(project().toFile()).withPluginClasspath()
                .withGradleVersion(version).withArguments(args);
        return fail ? runner.buildAndFail() : runner.build();
    }

    private List<Element> developers(String path) throws Exception {
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom(path).toFile());
        var nodes = document.getElementsByTagName("developer");
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) result.add((Element) nodes.item(i));
        return result;
    }

    private void assertDevelopers(String path, List<String> emails) throws Exception {
        assertEquals(emails, developers(path).stream().map(dev -> text(dev, "email")).toList());
    }

    private void assertDeveloper(String path, int index, String name, String id, Set<String> roles) throws Exception {
        Element developer = developers(path).get(index);
        assertEquals(name, text(developer, "name"));
        assertEquals(id, text(developer, "id"));
        Set<String> actualRoles = new TreeSet<>();
        var nodes = developer.getElementsByTagName("role");
        for (int i = 0; i < nodes.getLength(); i++) actualRoles.add(nodes.item(i).getTextContent());
        assertEquals(roles, actualRoles);
    }

    private static String text(Element element, String tag) {
        var nodes = element.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    private Path project() { return directory.resolve("project"); }
    private Path pom(String path) { return project().resolve(path).resolve("build/publications/library/pom-default.xml"); }

    private void write(String path, String contents) throws IOException {
        Path target = project().resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, contents);
    }
}
