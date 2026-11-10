package nebula.plugin.publishing;

import groovy.json.JsonSlurper;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/** Released release-plugin inference composed with signed Nebula Maven/Ivy publication. */
class IsolatedReleasePublishingTest {
    private static final String MAVEN = ":api:publishNebulaPublicationToLocalTestRepository";
    private static final String IVY = ":libraries:core:publishNebulaIvyPublicationToLocalTestRepository";
    private static final String IMMUTABLE = "-Pnebula.release.features.replaceDevWithImmutableSnapshot=true";
    private static final List<String> PROJECTS = List.of("", "api", "libraries", "libraries/core", "versionOnly");
    @TempDir Path directory;
    private TestSigningKeys keys;

    @ParameterizedTest
    @CsvSource({"9.0.0,snapshot", "9.0.0,devSnapshot", "9.2.1,snapshot", "9.2.1,devSnapshot",
            "9.8.2,snapshot", "9.8.2,devSnapshot", "9.9.0,snapshot", "9.9.0,devSnapshot"})
    void releaseWorkflowsPublishCommonInferredVersionsAndReuseCache(String gradle, String workflow) throws Exception {
        fixture();
        BuildResult cold = run(gradle, false, ":" + workflow, "recordVersion", IMMUTABLE);
        assertStored(cold);
        String inferred = recorded("");
        if (workflow.equals("snapshot")) assertEquals("1.3.0-SNAPSHOT", inferred);
        else assertTrue(inferred.startsWith("1.3.0-snapshot."), inferred);
        assertPublished(inferred);
        assertSnapshotWorkflow(cold);
        BuildResult warm = run(gradle, false, ":" + workflow, "recordVersion", IMMUTABLE);
        assertReused(warm);
        assertSnapshotWorkflow(warm);
        assertPublished(inferred);
        assertTagsUnchanged();
    }

    @Test
    void changedScopeUpdatesEveryVersionAndSignedPublication() throws Exception {
        fixture();
        assertStored(run("9.2.1", false, ":snapshot", "recordVersion"));
        assertPublished("1.3.0-SNAPSHOT");
        assertStored(run("9.2.1", false, ":snapshot", "recordVersion", "-Prelease.scope=patch"));
        assertPublished("1.2.4-SNAPSHOT");
        assertReused(run("9.2.1", false, ":snapshot", "recordVersion", "-Prelease.scope=patch"));
        assertPublished("1.2.4-SNAPSHOT");
        assertTagsUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.0.0", "9.2.1", "9.8.2", "9.9.0"})
    void finalStatusPublishesSignedInferredVersionWithoutTagging(String gradle) throws Exception {
        fixture();
        for (int attempt = 0; attempt < 2; attempt++) {
            BuildResult result = run(gradle, false, ":finalSetup", MAVEN, IVY, "recordVersion", "-Prelease.stage=final");
            if (attempt == 0) assertStored(result); else assertReused(result);
            assertPublished("1.3.0");
            assertEquals(TaskOutcome.SUCCESS, result.task(":verifyPublicationReport").getOutcome());
            assertNull(result.task(":release"));
        }
        assertTagsUnchanged();
    }

    @Test
    void newGitCommitRefreshesImmutableVersionArtifactsAndSignatures() throws Exception {
        fixture();
        assertStored(run("9.2.1", false, ":devSnapshot", "recordVersion", IMMUTABLE));
        String before = recorded("");
        assertPublished(before);
        source("api", "Api", "two");
        git("add", "api/src/main/java/example/Api.java");
        git("-c", "commit.gpgsign=false", "commit", "-m", "Change source");
        assertStored(run("9.2.1", false, ":devSnapshot", "recordVersion", IMMUTABLE));
        String after = recorded("");
        assertNotEquals(before, after);
        assertTrue(after.startsWith("1.3.0-snapshot."), after);
        assertPublished(after);
        try (var sources = new JarFile(repository("api", after).resolve("api-" + after + "-sources.jar").toFile())) {
            String content = new String(sources.getInputStream(sources.getEntry("example/Api.java")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains("return \"two\""));
        }
        assertReused(run("9.2.1", false, ":devSnapshot", "recordVersion", IMMUTABLE));
        assertPublished(after);
        assertTagsUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {MAVEN, IVY})
    void finalStageRejectsSnapshotDependencyBeforeEitherRepositoryUpload(String task) throws Exception {
        fixture();
        for (int attempt = 0; attempt < 2; attempt++) {
            BuildResult result = run("9.2.1", true, ":finalSetup", task, "-Prelease.stage=final", "-PfixtureDependency=2.0-SNAPSHOT");
            if (attempt == 0) assertStored(result); else assertReused(result);
            assertEquals(TaskOutcome.FAILED, result.task(":verifyPublicationReport").getOutcome());
            assertTrue(result.getOutput().contains("2.0-SNAPSHOT"));
            assertTrue(result.getOutput().contains("current project status 'release'"));
            assertNull(result.task(":release"));
            assertFalse(Files.exists(project().resolve("repository")));
        }
        assertTagsUnchanged();
    }

    @Test
    void helpDoesNotResolveProjectDependenciesOrPublish() throws Exception {
        fixture();
        for (String module : List.of("api", "libraries/core")) append(module + "/build.gradle", """
            configurations.configureEach {
                incoming.beforeResolve { throw new GradleException('Unexpected dependency resolution') }
            }
            """);
        for (int attempt = 0; attempt < 2; attempt++) {
            BuildResult result = run("9.2.1", false, ":help");
            if (attempt == 0) assertStored(result); else assertReused(result);
            assertNull(result.task(":release"));
            assertNull(result.task(":verifyPublicationReport"));
            assertFalse(Files.exists(project().resolve("repository")));
        }
        assertTagsUnchanged();
    }

    @Test
    void descriptorOnlyRequestsUseInferredFinalVersionWithoutReleaseOrUpload() throws Exception {
        fixture();
        for (int attempt = 0; attempt < 2; attempt++) {
            BuildResult result = run("9.2.1", false, ":finalSetup", ":api:generatePomFileForNebulaPublication",
                    ":libraries:core:generateDescriptorFileForNebulaIvyPublication", "-Prelease.stage=final");
            if (attempt == 0) assertStored(result); else assertReused(result);
            assertNull(result.task(":release"));
            assertNull(result.task(":verifyPublicationReport"));
            assertFalse(Files.exists(project().resolve("repository")));
            assertEquals("1.3.0", text(xml(project().resolve("api/build/publications/nebula/pom-default.xml")), "/project/version"));
            assertEquals("1.3.0", text(xml(project().resolve("libraries/core/build/publications/nebulaIvy/ivy.xml")),
                    "/ivy-module/info/@revision"));
        }
        assertTagsUnchanged();
    }

    private void fixture() throws Exception {
        write("settings.gradle", """
            plugins { id 'com.netflix.nebula.release-settings' }
            isolatedRelease { timestampPrecision = 'SECONDS' }
            rootProject.name = 'release-publishing-fixture'
            include 'api', 'libraries:core', 'versionOnly'
            """);
        write("gradle.properties", """
            nebula.release.isolatedProjectsSupport=true
            nebula.publishing.isolatedProjectsSupport=true
            nebula.info.isolatedProjectsSupport=true
            """);
        write(".gitignore", ".gradle/\n**/build/\nbuild/\nrepository/\nfixture-signing-private.asc\n");
        keys = new TestSigningKeys(project().resolve("fixture-signing-private.asc"));
        write("gradle/version-conventions.gradle", """
            apply plugin: 'com.netflix.nebula.publish-verification'
            group = 'example'
            def inferred = version.toString()
            tasks.register('recordVersion', WriteProperties) {
                destinationFile = layout.buildDirectory.file('inferred-version.properties')
                property 'version', inferred
            }
            """);
        write("gradle/library-conventions.gradle", """
            apply from: isolated.rootProject.projectDirectory.file('gradle/version-conventions.gradle')
            apply plugin: 'com.netflix.nebula.release'
            apply plugin: 'com.netflix.nebula.info'
            apply plugin: 'com.netflix.nebula.contacts'
            apply plugin: 'com.netflix.nebula.source-jar'
            apply plugin: 'com.netflix.nebula.javadoc-jar'
            apply plugin: 'signing'
            description = 'Release publishing integration fixture'
            def owner = project.name + '@example.org'
            contacts {
                inheritFromParent.set(false)
                contact(owner) { role 'owner' }
            }
            scminfo.origin.set('https://example.org/' + project.name + '.git')
            repositories { maven { url = isolated.rootProject.projectDirectory.dir('dependencies') } }
            dependencies { implementation 'example:external:' + providers.gradleProperty('fixtureDependency').getOrElse('1.0') }
            signing {
                useInMemoryPgpKeys(providers.fileContents(isolated.rootProject.projectDirectory
                    .file('fixture-signing-private.asc')).asText.get(), '')
                sign(publishing.publications)
            }
            """);
        write("build.gradle", """
            plugins { id 'com.netflix.nebula.release' }
            apply from: 'gradle/version-conventions.gradle'
            tasks.named('release') {
                dependsOn ':verifyPublicationReport'
                finalizedBy '%s', '%s'
            }
            """.formatted(MAVEN, IVY));
        for (String path : List.of("libraries", "versionOnly")) write(path + "/build.gradle", """
            apply from: isolated.rootProject.projectDirectory.file('gradle/version-conventions.gradle')
            """);
        library("api", "maven");
        library("libraries/core", "ivy");
        append("libraries/core/build.gradle", "dependencies { api project(':api') }\n");
        source("api", "Api", "one");
        source("libraries/core", "Core", "one");
        module("1.0");
        module("2.0-SNAPSHOT");
        git("init", "-b", "main");
        git("config", "user.name", "Publishing fixture");
        git("config", "user.email", "fixture@example.org");
        git("add", ".");
        git("-c", "commit.gpgsign=false", "commit", "-m", "Fixture");
        git("tag", "--no-sign", "v1.2.3");
    }

    private void library(String path, String format) throws IOException {
        write(path + "/build.gradle", """
            plugins { id 'java-library'; id 'com.netflix.nebula.%s-publish' }
            apply from: isolated.rootProject.projectDirectory.file('gradle/library-conventions.gradle')
            publishing.repositories {
                %s {
                    name = 'localTest'
                    url = isolated.rootProject.projectDirectory.dir('repository')
                }
            }
            """.formatted(format, format));
    }

    private void assertPublished(String version) throws Exception {
        for (String path : PROJECTS) assertEquals(version, recorded(path), path);
        for (String module : List.of("api", "core")) {
            Path repository = repository(module, version);
            String published = publishedVersion(module, version);
            for (String artifact : List.of(module + "-" + published + ".jar", module + "-" + published + "-sources.jar",
                    module + "-" + published + "-javadoc.jar", module + "-" + published + ".module",
                    module.equals("api") ? "api-" + published + ".pom" : "ivy-" + version + ".xml")) {
                keys.verify(repository.resolve(artifact));
            }
            try (var jar = new JarFile(repository.resolve(module + "-" + published + ".jar").toFile())) {
                assertNotNull(jar.getEntry("example/" + (module.equals("api") ? "Api" : "Core") + ".class"));
                assertEquals(version, jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
                assertEquals(module + "@example.org", jar.getManifest().getMainAttributes().getValue("Module-Owner"));
            }
            Map<?, ?> metadata = (Map<?, ?>) new JsonSlurper().parse(repository.resolve(module + "-" + published + ".module").toFile());
            assertEquals(version, ((Map<?, ?>) metadata.get("component")).get("version"));
            if (module.equals("core")) {
                boolean foundApi = false;
                for (Object variant : (List<?>) metadata.get("variants")) {
                    Object dependencies = ((Map<?, ?>) variant).get("dependencies");
                    if (!(dependencies instanceof List<?>)) continue;
                    for (Object item : (List<?>) dependencies) {
                        Map<?, ?> dependency = (Map<?, ?>) item;
                        if ("api".equals(dependency.get("module"))) {
                            foundApi = true;
                            assertEquals(version, ((Map<?, ?>) dependency.get("version")).get("requires"));
                        }
                    }
                }
                assertTrue(foundApi, "Missing project dependency in module metadata");
            }
        }
        Document pom = xml(repository("api", version).resolve("api-" + publishedVersion("api", version) + ".pom"));
        assertEquals(version, text(pom, "/project/version"));
        assertEquals("api@example.org", text(pom, "/project/developers/developer/email"));
        assertEquals("api@example.org", text(pom, "/project/properties/nebula_Module_Owner"));
        assertEquals("https://example.org/api.git", text(pom, "/project/scm/url"));
        Document ivy = xml(repository("core", version).resolve("ivy-" + version + ".xml"));
        assertEquals(version, text(ivy, "/ivy-module/info/@revision"));
        assertEquals(version, text(ivy, "/ivy-module/dependencies/dependency[@name='api']/@rev"));
        assertEquals("core@example.org", ivy.getElementsByTagName("nebula:Module_Owner").item(0).getTextContent());
        assertFalse(Files.exists(project().resolve("repository/example/versionOnly")));
        assertFalse(Files.exists(project().resolve("versionOnly/build/libs")));
    }

    private void assertSnapshotWorkflow(BuildResult result) {
        assertEquals(TaskOutcome.SKIPPED, result.task(":prepare").getOutcome());
        assertEquals(TaskOutcome.SKIPPED, result.task(":release").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(MAVEN).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(IVY).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyPublicationReport").getOutcome());
    }

    private void assertTagsUnchanged() throws Exception { assertEquals("v1.2.3", git("tag", "--list").trim()); }

    private String recorded(String path) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(project().resolve(path).resolve("build/inferred-version.properties"))) {
            properties.load(input);
        }
        return properties.getProperty("version");
    }

    private BuildResult run(String version, boolean fail, String... tasks) {
        Map<String, String> environment = new HashMap<>(System.getenv());
        for (String name : List.of("GIT_COMMIT", "P4CONFIG", "P4CLIENT", "WORKSPACE", "P4_CHANGELIST")) environment.remove(name);
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.addAll(List.of("--configuration-cache", "--configuration-cache-problems=fail", "--parallel", "--max-workers=2",
                "-Dorg.gradle.unsafe.isolated-projects=true",
                "-Dorg.gradle.java.home=" + System.getProperty("publishing.verificationTestJavaHome"), "--stacktrace"));
        GradleRunner runner = GradleRunner.create().withProjectDir(project().toFile()).withPluginClasspath();
        List<File> classpath = new ArrayList<>(runner.getPluginClasspath());
        Arrays.stream(System.getProperty("publishing.releaseTestClasspath").split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(File::new).filter(file -> !classpath.contains(file)).forEach(classpath::add);
        runner.withPluginClasspath(classpath).withGradleVersion(version).withEnvironment(environment).withArguments(arguments).forwardOutput();
        return fail ? runner.buildAndFail() : runner.build();
    }

    private static void assertStored(BuildResult result) { assertTrue(result.getOutput().contains("Configuration cache entry stored.")); }
    private static void assertReused(BuildResult result) { assertTrue(result.getOutput().contains("Reusing configuration cache.")); }

    private void module(String version) throws IOException {
        String path = "dependencies/example/external/" + version + "/external-" + version;
        write(path + ".pom", "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>"
                + "<artifactId>external</artifactId><version>" + version + "</version></project>");
        try (var jar = new JarOutputStream(Files.newOutputStream(project().resolve(path + ".jar")))) {
            jar.putNextEntry(new JarEntry("external.txt"));
            jar.write(version.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private void source(String path, String name, String value) throws IOException {
        write(path + "/src/main/java/example/" + name + ".java", """
            package example;
            /** Fixture library. */
            public class %s {
                /** @return the fixture value */
                public String value() { return "%s"; }
            }
            """.formatted(name, value));
    }

    private Path project() { return directory.resolve("project"); }
    private Path repository(String module, String version) { return project().resolve("repository/example/" + module + "/" + version); }

    private String publishedVersion(String module, String version) throws Exception {
        if (!module.equals("api") || !version.endsWith("-SNAPSHOT")) return version;
        // Maven assigns a timestamp/build number to mutable snapshots on every upload.
        String published = text(xml(repository(module, version).resolve("maven-metadata.xml")),
                "/metadata/versioning/snapshotVersions/snapshotVersion[extension='jar' and not(classifier)]/value");
        assertFalse(published.isEmpty());
        assertFalse(published.endsWith("-SNAPSHOT"), published);
        return published;
    }
    private static Document xml(Path path) throws Exception { return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile()); }
    private static String text(Document document, String xpath) throws Exception { return XPathFactory.newInstance().newXPath().evaluate(xpath, document); }

    private void write(String path, String contents) throws IOException {
        Path target = project().resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, contents);
    }

    private void append(String path, String contents) throws IOException { Files.writeString(project().resolve(path), contents, java.nio.file.StandardOpenOption.APPEND); }

    private String git(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", project().toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
