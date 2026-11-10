package nebula.plugin.publishing;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end, local-repository publishing with independent project configuration. */
class IsolatedPublishingFacadeTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({"9.0.0,maven,true", "9.0.0,ivy,true", "9.2.1,maven,true", "9.2.1,ivy,true",
            "9.2.1,maven,false", "9.2.1,ivy,false",
            "9.9.0,maven,true", "9.9.0,ivy,true", "9.9.0,maven,false", "9.9.0,ivy,false"})
    void publishesWithStrictIsolationAndCacheReuse(String version, String format, boolean info) throws Exception {
        fixture(format, info, "", false, false);
        checkTwice(version, publishTask(format));
        assertPublished(format, info, false, false);
    }

    @ParameterizedTest
    @CsvSource({"9.2.1,maven,8.3.9,false,false", "9.2.1,maven,8.3.9,true,false", "9.2.1,maven,8.3.9,true,true",
            "9.2.1,ivy,8.3.9,false,false", "9.2.1,ivy,8.3.9,true,false",
            "9.2.1,maven,9.3.1,false,false", "9.2.1,maven,9.3.1,true,false", "9.2.1,maven,9.3.1,true,true",
            "9.2.1,ivy,9.3.1,false,false", "9.2.1,ivy,9.3.1,true,false",
            "9.0.0,maven,8.3.9,true,false", "9.0.0,ivy,8.3.9,true,false",
            "9.0.0,maven,9.3.1,true,true", "9.0.0,ivy,9.3.1,true,false",
            "9.9.0,maven,9.3.1,false,false", "9.9.0,maven,9.3.1,true,false", "9.9.0,maven,9.3.1,true,true",
            "9.9.0,ivy,9.3.1,false,false", "9.9.0,ivy,9.3.1,true,false"})
    void publishesShadowArtifactsWithStrictIsolation(String version, String format, String shadow, boolean jarDisabled,
                                                    boolean component) throws Exception {
        fixture(format, true, shadow, jarDisabled, component);
        checkTwice(version, publishTask(format));
        assertPublished(format, true, true, jarDisabled);
        if (format.equals("maven")) {
            String pom = Files.readString(project().resolve("platform/app/build/publications/nebula/pom-default.xml"));
            assertEquals(!component, pom.contains("<artifactId>embedded</artifactId>"));
        }
    }

    @ParameterizedTest
    @CsvSource({"maven", "ivy"})
    void shadowReplacementPreservesAdditionalArtifactsAndUsesConfiguredJarOutput(String format) throws Exception {
        fixture(format, true, "8.3.9", true, false);
        append("platform/app/build.gradle", """
            tasks.named('jar') {
                archiveBaseName.set('custom-main')
                archiveClassifier.set('thin')
            }
            def extraJar = tasks.register('extraJar', Jar) {
                archiveClassifier.set('extra')
                from('src/main/resources')
            }
            configurations.apiElements.outgoing.artifact(extraJar)
            configurations.runtimeElements.outgoing.artifact(extraJar)
            """);
        checkTwice("9.2.1", publishTask(format));
        assertPublished(format, true, true, true);
        assertTrue(Files.exists(project().resolve("repository/example/app/1.0/app-1.0-extra.jar")));
        assertFalse(Files.exists(project().resolve("repository/example/app/1.0/app-1.0-thin.jar")));
    }

    @ParameterizedTest
    @CsvSource({"maven", "ivy"})
    void shadowHelpDoesNotResolveDependencies(String format) throws Exception {
        fixture(format, true, "8.3.9", true, false);
        append("platform/app/build.gradle", """
            configurations.configureEach {
                incoming.beforeResolve { throw new GradleException('Unexpected dependency resolution') }
            }
            """);
        checkTwice("9.2.1", ":platform:app:help");
    }

    private void fixture(String format, boolean info, String shadow, boolean jarDisabled, boolean component) throws Exception {
        write("settings.gradle", "rootProject.name='facade-fixture'\ninclude 'api', 'platform:app'\n");
        write("gradle.properties", "nebula.publishing.isolatedProjectsSupport=true\nnebula.info.isolatedProjectsSupport=true\n"
                + "nebula.publishing.features.detectShadowComponent.enabled=" + component + "\n");
        String script = """
            plugins {
                id 'java-library'
                id 'com.netflix.nebula.%s-publish'
                id 'com.netflix.nebula.publish-verification'
                id 'com.netflix.nebula.contacts'
            %s
            %s
            }
            group = 'example'
            version = '1.0'
            status = 'release'
            description = 'Isolated library'
            def contactEmail = project.name + '@example.org'
            def contactName = project.name + ' owner'
            contacts {
                inheritFromParent.set(false)
                contact(contactEmail) {
                    moniker contactName
                    role 'owner'
                }
            }
            repositories {
                maven { url = isolated.rootProject.projectDirectory.dir('dependencies') }
            }
            dependencies { implementation 'example:embedded:1.0' }
            publishing.repositories {
                %s {
                    name = 'localTest'
                    url = isolated.rootProject.projectDirectory.dir('repository')
                }
            }
            %s
            %s
            """.formatted(format, info ? "id 'com.netflix.nebula.info'" : "",
                shadow.isEmpty() ? "" : "id 'com.gradleup.shadow' version '" + shadow + "'", format,
                info ? "scminfo.origin.set('https://example.org/' + project.name + '.git')\n"
                        + "afterEvaluate { plugins.getPlugin(nebula.plugin.info.InfoBrokerPlugin).add('Local-Entry', project.name) }" : "",
                shadow.isEmpty() ? "" : "tasks.named('jar') { enabled = " + !jarDisabled + " }\n"
                        + "tasks.named('shadowJar') { archiveClassifier.set('" + (jarDisabled ? "" : "all") + "') }\n");
        write("build.gradle", script);
        write("api/build.gradle", script);
        write("platform/build.gradle", "plugins { id 'com.netflix.nebula.publish-verification' }\n");
        write("platform/app/build.gradle", script + "\ndependencies { api project(':api') }\n");
        for (String path : List.of("", "api/", "platform/app/")) {
            write(path + "src/main/resources/local.txt", path.isEmpty() ? "root" : path);
        }
        write("dependencies/example/embedded/1.0/embedded-1.0.pom", """
            <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>
            <artifactId>embedded</artifactId><version>1.0</version></project>
            """);
        try (var jar = new JarOutputStream(Files.newOutputStream(project().resolve("dependencies/example/embedded/1.0/embedded-1.0.jar")))) {
            jar.putNextEntry(new JarEntry("embedded.txt"));
            jar.write("embedded dependency".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        git("init", "-b", "main");
        git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.org", "-c", "commit.gpgsign=false",
                "commit", "--allow-empty", "-m", "Fixture");
        git("remote", "add", "origin", "https://example.org/fixture.git");
    }

    private void assertPublished(String format, boolean info, boolean shadow, boolean jarDisabled) throws Exception {
        Path artifact = project().resolve("repository/example/app/1.0/app-1.0.jar");
        assertTrue(Files.exists(artifact), artifact.toString());
        try (var jar = new ZipFile(artifact.toFile())) {
            assertNotNull(jar.getEntry("local.txt"));
            if (shadow && jarDisabled) assertNotNull(jar.getEntry("embedded.txt"));
        }
        if (shadow && !jarDisabled) {
            Path all = artifact.resolveSibling("app-1.0-all.jar");
            assertTrue(Files.exists(all), all.toString());
            try (var jar = new ZipFile(all.toFile())) { assertNotNull(jar.getEntry("embedded.txt")); }
        }
        if (format.equals("maven")) {
            Document pom = xml("platform/app/build/publications/nebula/pom-default.xml");
            assertEquals("app@example.org", pom.getElementsByTagName("email").item(0).getTextContent());
            if (info) {
                assertEquals("app@example.org", pom.getElementsByTagName("nebula_Module_Owner").item(0).getTextContent());
                assertEquals("app", pom.getElementsByTagName("nebula_Local_Entry").item(0).getTextContent());
                assertEquals("https://example.org/app", pom.getElementsByTagName("url").item(0).getTextContent());
            }
        } else if (info) {
            Document ivy = xml("platform/app/build/publications/nebulaIvy/ivy.xml");
            assertEquals("app@example.org", ivy.getElementsByTagName("nebula:Module_Owner").item(0).getTextContent());
            assertEquals("app", ivy.getElementsByTagName("nebula:Local_Entry").item(0).getTextContent());
        }
    }

    private String publishTask(String format) {
        return ":platform:app:publish" + (format.equals("maven") ? "Nebula" : "NebulaIvy") + "PublicationToLocalTestRepository";
    }

    private void checkTwice(String version, String task) {
        assertTrue(run(version, task).getOutput().contains("Configuration cache entry stored."));
        assertTrue(run(version, task).getOutput().contains("Reusing configuration cache."));
    }

    private BuildResult run(String version, String task) {
        Map<String, String> environment = new HashMap<>(System.getenv());
        for (String name : List.of("GIT_COMMIT", "P4CONFIG", "P4CLIENT", "WORKSPACE", "P4_CHANGELIST")) environment.remove(name);
        return GradleRunner.create().withProjectDir(project().toFile()).withPluginClasspath().withGradleVersion(version)
                .withEnvironment(environment).withArguments(task, "--configuration-cache", "--configuration-cache-problems=fail",
                        "--parallel", "--max-workers=2", "-Dorg.gradle.unsafe.isolated-projects=true",
                        "-Dorg.gradle.java.home=" + System.getProperty("publishing.verificationTestJavaHome"), "--stacktrace").build();
    }

    private Document xml(String path) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(project().resolve(path).toFile());
    }

    private Path project() { return directory.resolve("project"); }

    private void write(String path, String contents) throws IOException {
        Path target = project().resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, contents);
    }

    private void append(String path, String contents) throws IOException {
        Files.writeString(project().resolve(path), contents, java.nio.file.StandardOpenOption.APPEND);
    }

    private void git(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", project().toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
    }
}
