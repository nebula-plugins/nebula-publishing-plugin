package nebula.plugin.publishing;

import groovy.json.JsonSlurper;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.zip.ZipFile;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Repository publication of platforms and additional publication types under strict isolation. */
class IsolatedPublicationTypesTest {
    private static final String MAVEN_TASK = "publishNebulaPublicationToLocalTestRepository";
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"9.0.0", "9.2.1", "9.8.2"})
    void bomPublishesConstraintsAndImportedPlatformWithCacheReuse(String version) throws Exception {
        bomFixture();
        checkTwice(version, () -> assertBom("1.0"), ":catalog:bom:" + MAVEN_TASK, ":api:" + MAVEN_TASK);
    }

    @Test
    void changedBomConstraintRefreshesConsumerAndDeletedMetadataCanBeRegenerated() throws Exception {
        bomFixture();
        String[] tasks = {":catalog:bom:" + MAVEN_TASK, ":api:" + MAVEN_TASK};
        checkTwice("9.2.1", () -> assertBom("1.0"), tasks);
        Path buildFile = project().resolve("catalog/bom/build.gradle");
        Files.writeString(buildFile, Files.readString(buildFile).replace("embedded:1.0", "embedded:2.0"));
        run("9.2.1", tasks);
        assertBom("2.0");
        Files.delete(project().resolve("catalog/bom/build/publications/nebula/pom-default.xml"));
        Files.delete(project().resolve("catalog/bom/build/publications/nebula/module.json"));
        assertTrue(run("9.2.1", tasks).getOutput().contains("Reusing configuration cache."));
        assertBom("2.0");
    }

    @Test
    void bomHelpDoesNotResolvePlatformDependencies() throws Exception {
        bomFixture();
        append("catalog/bom/build.gradle", """
            configurations.configureEach {
                incoming.beforeResolve { throw new GradleException('Unexpected platform resolution') }
            }
            """);
        checkTwice("9.2.1", () -> {}, ":catalog:bom:help");
    }

    @ParameterizedTest
    @CsvSource({"9.0.0,maven", "9.0.0,ivy", "9.2.1,maven", "9.2.1,ivy", "9.8.2,maven", "9.8.2,ivy"})
    void publishesSignedLibrarySourcesJavadocAndMetadata(String version, String format) throws Exception {
        TestSigningKeys keys = signedLibraryFixture(format);
        checkTwice(version, () -> assertSignedLibrary(format, keys, "one"), publishTask("api", format));
    }

    @ParameterizedTest
    @ValueSource(strings = {"maven", "ivy"})
    void changedSourceIsResignedAndDeletedOutputsAreRegenerated(String format) throws Exception {
        TestSigningKeys keys = signedLibraryFixture(format);
        String task = publishTask("api", format);
        checkTwice("9.2.1", () -> assertSignedLibrary(format, keys, "one"), task);
        byte[] originalJar = Files.readAllBytes(repository("api").resolve("api-1.0.jar"));
        source("api", "two");
        assertTrue(run("9.2.1", task).getOutput().contains("Reusing configuration cache."));
        assertSignedLibrary(format, keys, "two");
        assertFalse(Arrays.equals(originalJar, Files.readAllBytes(repository("api").resolve("api-1.0.jar"))));
        deleteFiles(project().resolve("api/build/libs"));
        deleteFiles(project().resolve("api/build/publications/" + publication(format)));
        deleteFiles(repository("api"));
        assertTrue(run("9.2.1", task).getOutput().contains("Reusing configuration cache."));
        assertSignedLibrary(format, keys, "two");
    }

    @ParameterizedTest
    @ValueSource(strings = {"maven", "ivy"})
    void signedLibraryHelpDoesNotResolveDependencies(String format) throws Exception {
        signedLibraryFixture(format);
        append("api/build.gradle", """
            configurations.configureEach {
                incoming.beforeResolve { throw new GradleException('Unexpected library resolution') }
            }
            """);
        checkTwice("9.2.1", () -> {}, ":api:help");
    }

    @ParameterizedTest
    @CsvSource({"9.0.0,maven", "9.0.0,ivy", "9.2.1,maven", "9.2.1,ivy", "9.8.2,maven", "9.8.2,ivy"})
    void publishesWarComponentWithCacheReuse(String version, String format) throws Exception {
        warFixture(format);
        checkTwice(version, () -> assertWar(format), publishTask("apps:web", format));
    }

    @ParameterizedTest
    @ValueSource(strings = {"maven", "ivy"})
    void warVerificationRejectsSnapshotBeforeUpload(String format) throws Exception {
        warFixture(format);
        module("embedded", "2.0-SNAPSHOT");
        Path script = project().resolve("apps/web/build.gradle");
        Files.writeString(script, Files.readString(script).replace("embedded:1.0", "embedded:2.0-SNAPSHOT"));
        String task = publishTask("apps:web", format);
        for (int i = 0; i < 2; i++) {
            BuildResult result = execute("9.2.1", true, task);
            assertEquals(TaskOutcome.FAILED, result.task(":verifyPublicationReport").getOutcome());
            assertTrue(result.getOutput().contains("2.0-SNAPSHOT"));
            assertTrue(result.getOutput().contains(i == 0 ? "Configuration cache entry stored." : "Reusing configuration cache."));
            assertFalse(Files.exists(repository("web").resolve("web-1.0.war")));
        }
    }

    private TestSigningKeys signedLibraryFixture(String format) throws Exception {
        baseFixture("'api'");
        TestSigningKeys keys = new TestSigningKeys(project().resolve("fixture-signing-private.asc"));
        write("api/build.gradle", script("java-library", format) + """
            apply plugin: 'com.netflix.nebula.source-jar'
            apply plugin: 'com.netflix.nebula.javadoc-jar'
            apply plugin: 'signing'
            signing {
                useInMemoryPgpKeys(providers.fileContents(isolated.rootProject.projectDirectory
                    .file('fixture-signing-private.asc')).asText.get(), '')
                sign(publishing.publications)
            }
            // Signing conventions may run before the dependency declarations.
            dependencies { api 'example:embedded:1.0' }
            """);
        source("api", "one");
        return keys;
    }

    private void assertSignedLibrary(String format, TestSigningKeys keys, String value) throws Exception {
        Path repository = repository("api");
        for (String artifact : List.of("api-1.0.jar", "api-1.0-sources.jar", "api-1.0-javadoc.jar", "api-1.0.module",
                format.equals("maven") ? "api-1.0.pom" : "ivy-1.0.xml")) {
            keys.verify(repository.resolve(artifact));
        }
        try (var jar = new ZipFile(repository.resolve("api-1.0.jar").toFile())) {
            assertNotNull(jar.getEntry("example/Library.class"));
        }
        try (var sources = new ZipFile(repository.resolve("api-1.0-sources.jar").toFile())) {
            String source = new String(sources.getInputStream(sources.getEntry("example/Library.java")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(source.contains("return \"" + value + "\""));
        }
        try (var javadoc = new ZipFile(repository.resolve("api-1.0-javadoc.jar").toFile())) {
            assertNotNull(javadoc.getEntry("example/Library.html"));
        }
        if (format.equals("maven")) {
            Document pom = xml(repository.resolve("api-1.0.pom"));
            assertEquals("1.0", text(pom, dependency("embedded") + "/version"));
            assertEquals("api@example.org", text(pom, "/project/developers/developer/email"));
        } else {
            Document ivy = xml(repository.resolve("ivy-1.0.xml"));
            assertEquals("1.0", text(ivy, "/ivy-module/dependencies/dependency[@name='embedded']/@rev"));
            assertEquals("jar", text(ivy, "/ivy-module/publications/artifact[@conf='sourcesElements']/@type"));
            assertEquals("jar", text(ivy, "/ivy-module/publications/artifact[@conf='javadocElements']/@type"));
        }
    }

    private void warFixture(String format) throws Exception {
        baseFixture("'apps:web'");
        write("apps/build.gradle", "plugins { id 'com.netflix.nebula.publish-verification' }\n");
        write("apps/web/build.gradle", script("war", format) + """
            dependencies {
                implementation 'example:embedded:1.0'
                providedCompile 'example:other:2.0'
            }
            """);
        source("apps/web", "web");
        write("apps/web/src/main/webapp/index.html", "<html><body>Fixture</body></html>");
    }

    private void assertWar(String format) throws Exception {
        Path repository = repository("web");
        try (var war = new ZipFile(repository.resolve("web-1.0.war").toFile())) {
            assertNotNull(war.getEntry("index.html"));
            assertNotNull(war.getEntry("WEB-INF/classes/example/Library.class"));
            assertNotNull(war.getEntry("WEB-INF/lib/embedded-1.0.jar"));
            assertNull(war.getEntry("WEB-INF/lib/other-2.0.jar"));
        }
        if (format.equals("maven")) {
            Document pom = xml(repository.resolve("web-1.0.pom"));
            assertEquals("war", text(pom, "/project/packaging"));
            assertEquals("web@example.org", text(pom, "/project/developers/developer/email"));
        } else {
            Document ivy = xml(repository.resolve("ivy-1.0.xml"));
            assertEquals("war", text(ivy, "/ivy-module/publications/artifact[@name='web']/@type"));
        }
        assertFalse(Files.exists(repository.resolve("web-1.0.jar")));
    }

    private void bomFixture() throws Exception {
        baseFixture("'catalog:bom', 'api'");
        write("catalog/build.gradle", "plugins { id 'com.netflix.nebula.publish-verification' }\n");
        write("catalog/bom/build.gradle", script("java-platform", "maven") + """
            javaPlatform.allowDependencies()
            dependencies {
                api platform('example:upstream-bom:3.0')
                constraints { api 'example:embedded:1.0' }
            }
            """);
        write("api/build.gradle", script("java-library", "maven") + """
            dependencies {
                api platform(project(':catalog:bom'))
                implementation 'example:embedded'
                implementation 'example:other'
            }
            """);
        source("api", "one");
    }

    private void assertBom(String embeddedVersion) throws Exception {
        Path published = project().resolve("repository/example/bom/1.0");
        Document bom = xml(published.resolve("bom-1.0.pom"));
        assertEquals("pom", text(bom, "/project/packaging"));
        assertEquals(embeddedVersion, text(bom, managed("embedded") + "/version"));
        assertEquals("3.0", text(bom, managed("upstream-bom") + "/version"));
        assertEquals("pom", text(bom, managed("upstream-bom") + "/type"));
        assertEquals("import", text(bom, managed("upstream-bom") + "/scope"));
        assertEquals("bom@example.org", text(bom, "/project/developers/developer/email"));
        assertEquals("bom@example.org", text(bom, "/project/properties/nebula_Module_Owner"));
        assertEquals("https://example.org/bom.git", text(bom, "/project/scm/url"));
        assertFalse(Files.exists(published.resolve("bom-1.0.jar")));

        Map<?, ?> module = (Map<?, ?>) new JsonSlurper().parse(published.resolve("bom-1.0.module").toFile());
        List<?> variants = (List<?>) module.get("variants");
        assertEquals(2, variants.size());
        for (Object item : variants) {
            Map<?, ?> variant = (Map<?, ?>) item;
            assertEquals("platform", ((Map<?, ?>) variant.get("attributes")).get("org.gradle.category"));
            assertFalse(variant.containsKey("files"));
            Map<?, ?> constraint = moduleNamed(variant, "dependencyConstraints", "embedded");
            assertEquals(embeddedVersion, ((Map<?, ?>) constraint.get("version")).get("requires"));
            Map<?, ?> imported = moduleNamed(variant, "dependencies", "upstream-bom");
            assertEquals("3.0", ((Map<?, ?>) imported.get("version")).get("requires"));
            assertEquals("platform", ((Map<?, ?>) imported.get("attributes")).get("org.gradle.category"));
        }

        Document library = xml(project().resolve("repository/example/api/1.0/api-1.0.pom"));
        assertEquals(embeddedVersion, text(library, dependency("embedded") + "/version"));
        assertEquals("2.0", text(library, dependency("other") + "/version"));
        assertEquals("1.0", text(library, managed("bom") + "/version"));
        assertEquals("import", text(library, managed("bom") + "/scope"));
    }

    private static Map<?, ?> moduleNamed(Map<?, ?> variant, String collection, String name) {
        return ((List<?>) variant.get(collection)).stream().map(item -> (Map<?, ?>) item)
                .filter(item -> "example".equals(item.get("group")) && name.equals(item.get("module")))
                .findFirst().orElseThrow();
    }

    private void baseFixture(String projects) throws Exception {
        write("settings.gradle", "rootProject.name='publication-types-fixture'\ninclude " + projects + "\n");
        write("gradle.properties", "nebula.publishing.isolatedProjectsSupport=true\nnebula.info.isolatedProjectsSupport=true\n");
        write("build.gradle", "plugins { id 'com.netflix.nebula.publish-verification' }\n");
        module("embedded", "1.0");
        module("embedded", "2.0");
        module("other", "2.0");
        write("dependencies/example/upstream-bom/3.0/upstream-bom-3.0.pom", """
            <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>
            <artifactId>upstream-bom</artifactId><version>3.0</version><packaging>pom</packaging>
            <dependencyManagement><dependencies><dependency><groupId>example</groupId>
            <artifactId>other</artifactId><version>2.0</version></dependency></dependencies></dependencyManagement></project>
            """);
        git("init", "-b", "main");
        git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.org", "-c", "commit.gpgsign=false",
                "commit", "--allow-empty", "-m", "Fixture");
        git("remote", "add", "origin", "https://example.org/fixture.git");
    }

    private String script(String type, String format) {
        return """
            plugins {
                id '%s'
                id 'com.netflix.nebula.%s-publish'
                id 'com.netflix.nebula.publish-verification'
                id 'com.netflix.nebula.info'
                id 'com.netflix.nebula.contacts'
            }
            group = 'example'
            version = '1.0'
            status = 'release'
            description = 'Isolated publication'
            def ownerEmail = project.name + '@example.org'
            contacts {
                inheritFromParent.set(false)
                contact(ownerEmail) { role 'owner' }
            }
            scminfo.origin.set('https://example.org/' + project.name + '.git')
            repositories { maven { url = isolated.rootProject.projectDirectory.dir('dependencies') } }
            publishing.repositories {
                %s {
                    name = 'localTest'
                    url = isolated.rootProject.projectDirectory.dir('repository')
                }
            }
            """.formatted(type, format, format);
    }

    private void source(String path, String value) throws IOException {
        write(path + "/src/main/java/example/Library.java", """
            package example;
            /** Library documentation. */
            public class Library {
                /**
                 * Returns the fixture value.
                 * @return the value
                 */
                public String value() { return "%s"; }
            }
            """.formatted(value));
    }

    private void module(String name, String version) throws IOException {
        String path = "dependencies/example/" + name + "/" + version + "/" + name + "-" + version;
        write(path + ".pom", "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>"
                + name + "</artifactId><version>" + version + "</version></project>");
        try (var jar = new JarOutputStream(Files.newOutputStream(project().resolve(path + ".jar")))) {
            jar.putNextEntry(new JarEntry(name + ".txt"));
            jar.write((name + version).getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private void checkTwice(String version, CheckedAssertions assertions, String... tasks) throws Exception {
        assertTrue(run(version, tasks).getOutput().contains("Configuration cache entry stored."));
        assertions.check();
        assertTrue(run(version, tasks).getOutput().contains("Reusing configuration cache."));
        assertions.check();
    }

    private BuildResult run(String version, String... tasks) {
        return execute(version, false, tasks);
    }

    private BuildResult execute(String version, boolean fail, String... tasks) {
        Map<String, String> environment = new HashMap<>(System.getenv());
        for (String name : List.of("GIT_COMMIT", "P4CONFIG", "P4CLIENT", "WORKSPACE", "P4_CHANGELIST")) environment.remove(name);
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.addAll(List.of("--configuration-cache", "--configuration-cache-problems=fail", "--parallel", "--max-workers=2",
                "-Dorg.gradle.unsafe.isolated-projects=true",
                "-Dorg.gradle.java.home=" + System.getProperty("publishing.verificationTestJavaHome"), "--stacktrace"));
        GradleRunner runner = GradleRunner.create().withProjectDir(project().toFile()).withPluginClasspath().withGradleVersion(version)
                .withEnvironment(environment).withArguments(arguments);
        BuildResult result = fail ? runner.buildAndFail() : runner.build();
        if (!fail && List.of(tasks).stream().anyMatch(task -> task.contains("publishNebula"))) {
            assertEquals(TaskOutcome.SUCCESS, result.task(":verifyPublicationReport").getOutcome());
        }
        return result;
    }

    private static String managed(String artifact) {
        return "/project/dependencyManagement/dependencies/dependency[artifactId='" + artifact + "']";
    }

    private static String dependency(String artifact) {
        return "/project/dependencies/dependency[artifactId='" + artifact + "']";
    }

    private static Document xml(Path file) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
    }

    private static String text(Document document, String expression) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(expression, document);
    }

    private Path project() { return directory.resolve("project"); }

    private Path repository(String module) { return project().resolve("repository/example/" + module + "/1.0"); }

    private static String publication(String format) { return format.equals("maven") ? "nebula" : "nebulaIvy"; }

    private static String publishTask(String path, String format) {
        return ":" + path + ":publish" + (format.equals("maven") ? "Nebula" : "NebulaIvy") + "PublicationToLocalTestRepository";
    }

    private static void deleteFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) if (Files.isRegularFile(file)) Files.delete(file);
        }
    }

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

    @FunctionalInterface
    private interface CheckedAssertions { void check() throws Exception; }
}
