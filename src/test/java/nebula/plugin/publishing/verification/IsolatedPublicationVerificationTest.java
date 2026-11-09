package nebula.plugin.publishing.verification;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IsolatedPublicationVerificationTest {
    private static final String FLAG = "nebula.publishing.isolatedProjectsSupport";
    private static final String PARTICIPANTS = "nebula.publishing.verification.projects";
    private static final String PUBLISH = "publishLibraryPublicationToFixtureRepository";

    @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path directory;

    @ParameterizedTest
    @CsvSource({"8.0,false", "8.14.3,true", "9.2.1,false", "9.2.1,true"})
    void publishingOneProjectVerifiesEveryParticipant(String version, boolean isolated) throws IOException {
        fixture(":api,:platform:api", "api", "platform:api");
        BuildResult first = run(version, isolated, false, ":api:" + PUBLISH);
        stored(first);
        success(first, ":api:verifyPublication", ":platform:api:verifyPublication", ":verifyPublicationReport", ":api:" + PUBLISH);
        assertTrue(Files.isRegularFile(artifact("api", "jar")));
        assertFalse(Files.exists(artifact("platform:api", "jar")));

        BuildResult reused = run(version, isolated, false, ":api:" + PUBLISH);
        assertTrue(reused.getOutput().contains("Reusing configuration cache."));
        success(reused, ":api:verifyPublication", ":platform:api:verifyPublication", ":verifyPublicationReport");
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.0", "9.2.1"})
    void rootCanAlsoBeTheOnlyParticipant(String version) throws IOException {
        fixture(":", "");
        write("gradle.properties", FLAG + "=true\n"); // A single-project build defaults to the root.
        BuildResult first = run(version, true, false, ":" + PUBLISH);
        stored(first);
        success(first, ":verifyPublication", ":verifyPublicationReport", ":" + PUBLISH);
        assertTrue(Files.exists(artifact("", "pom")));
        assertTrue(run(version, true, false, ":" + PUBLISH).getOutput().contains("Reusing configuration cache."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.0", "9.2.1"})
    void gradleCoreTaskPathIsolationControl(String version) throws IOException {
        write("settings.gradle", "include 'api'\n");
        write("build.gradle", "tasks.register('report') { dependsOn(':api:verify') }\n");
        write("api/build.gradle", "tasks.register('verify')\ntasks.register('upload') { dependsOn(':report') }\n");
        BuildResult result = run(version, true, version.equals("8.0"), ":api:upload");
        if (version.equals("8.0")) {
            assertTrue(result.getOutput().contains("Cannot access project ':' from project ':api'"));
            assertTrue(result.getOutput().contains("Cannot access project ':api' from project ':'"));
            assertFalse(result.getOutput().contains("Configuration cache entry stored."));
        } else {
            stored(result);
        }
    }

    @Test
    void gradleEightMultiProjectIsolationHasTheSameCoreLimitation() throws IOException {
        fixture(":api,:app", "api", "app");
        BuildResult result = run("8.0", true, true, ":verifyPublicationReport");
        assertTrue(result.getOutput().contains("Cannot access project ':api' from project ':'"));
        assertFalse(result.getOutput().contains("Configuration cache entry stored."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.0", "9.2.1"})
    void flagOffRetainsImplicitRootAggregation(String version) throws IOException {
        fixture(":ignored", "api", "app");
        write("build.gradle", ""); // Legacy children create the report; root does not apply the plugin.
        BuildResult result = run(version, false, false, ":api:" + PUBLISH, "-P" + FLAG + "=false");
        stored(result);
        success(result, ":api:verifyPublication", ":app:verifyPublication", ":verifyPublicationReport", ":api:" + PUBLISH);

        unstableDependency("app");
        BuildResult rejected = run(version, false, true, ":api:" + PUBLISH, "-P" + FLAG + "=false");
        assertEquals(TaskOutcome.FAILED, rejected.task(":verifyPublicationReport").getOutcome());
        assertTrue(rejected.getOutput().contains("fixture:unstable"));
        assertNull(rejected.task(":api:" + PUBLISH));
    }

    @ParameterizedTest
    @CsvSource({"8.0,false", "9.2.1,true"})
    void statusAndIgnoreChangesInvalidateCacheAndNeverUploadOnFailure(String version, boolean isolated) throws IOException {
        fixture(":api,:platform:api", "api", "platform:api");
        unstableDependency("platform:api");
        append("api/build.gradle", "status = 'milestone'\n");
        String publish = ":api:" + PUBLISH;
        // The unpublished participant has a real invalid dependency; publishing the other project must fail.
        BuildResult rejected = run(version, isolated, true, publish);
        assertRejected(rejected, ":platform:api", "release");
        assertFalse(Files.exists(artifact("api", "jar")));
        BuildResult rejectedAgain = run(version, isolated, true, publish);
        assertTrue(rejectedAgain.getOutput().contains("Reusing configuration cache."));
        assertRejected(rejectedAgain, ":platform:api", "release");

        // Changing status changes the verification input, then the same task graph reuses safely.
        BuildResult changed = run(version, isolated, false, publish, "-PtargetStatus=integration");
        stored(changed);
        assertFalse(changed.getOutput().contains("Reusing configuration cache."));
        success(changed, ":verifyPublicationReport", ":api:" + PUBLISH);
        assertTrue(run(version, isolated, false, publish, "-PtargetStatus=integration")
                .getOutput().contains("Reusing configuration cache."));

        for (String ignore : List.of("module", "map", "group")) {
            BuildResult ignored = run(version, isolated, false, publish, "-PignorePolicy=" + ignore);
            stored(ignored);
            success(ignored, ":verifyPublicationReport", ":api:" + PUBLISH);
        }
        BuildResult reused = run(version, isolated, false, publish, "-PignorePolicy=group");
        assertTrue(reused.getOutput().contains("Reusing configuration cache."));
        success(reused, ":platform:api:verifyPublication", ":verifyPublicationReport");

        // Reusing the old failing graph after successful runs must not retain their service results.
        assertRejected(run(version, isolated, true, publish), ":platform:api", "release");
    }

    @Test
    void duplicateLeafNamesAndMixedStatusesProduceDeterministicReports() throws IOException {
        fixture(":platform:api,:api", "api", "platform:api");
        unstableDependency("api");
        unstableDependency("platform:api");
        append("api/build.gradle", "status = 'milestone'\n");
        BuildResult result = run("9.2.1", true, true, ":verifyPublicationReport");
        assertRejected(result, ":api", "milestone");
        assertRejected(result, ":platform:api", "release");
        assertTrue(result.getOutput().indexOf("Dependencies for :api:") <
                result.getOutput().indexOf("Dependencies for :platform:api:"));
        BuildResult reused = run("9.2.1", true, true, ":verifyPublicationReport");
        assertTrue(reused.getOutput().contains("Reusing configuration cache."));
        assertRejected(reused, ":api", "milestone");
        assertRejected(reused, ":platform:api", "release");
    }

    @Test
    void invalidVersionSelectorsAreStillRejected() throws IOException {
        fixture(":api,:app", "api", "app");
        module("library", "1.01");
        write("repo/fixture/library/maven-metadata.xml", """
            <metadata><groupId>fixture</groupId><artifactId>library</artifactId>
            <versioning><versions><version>1.01</version></versions></versioning></metadata>
            """);
        append("app/build.gradle", "repositories { maven { url = uri('" + directory.resolve("repo").toUri() + "') } }\n" +
                "dependencies { runtimeOnly 'fixture:library:1.0+' }\n");
        BuildResult result = run("9.2.1", true, true, ":api:" + PUBLISH);
        assertTrue(result.getOutput().contains("with requested version '1.0+'"));
        assertFalse(Files.exists(artifact("api", "jar")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "api", ":api,", ":api,:api", ":api, :app", ":api:", ":api::app", ":../api"})
    void invalidParticipantListsFailEarly(String participants) throws IOException {
        fixture(participants, "api");
        BuildResult result = run("9.2.1", true, true, "help");
        assertTrue(result.getOutput().contains(PARTICIPANTS));
        assertFalse(Files.exists(artifact("api", "jar")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unenrolled", "root-plugin", "participant-plugin", "participant-java", "missing-project", "excluded", "disabled", "replacement-task"})
    void incompleteEnrollmentFailsBeforeUpload(String problem) throws IOException {
        fixture(":api,:app", "api", "app");
        List<String> arguments = new ArrayList<>(List.of(":api:" + PUBLISH));
        switch (problem) {
            case "unenrolled" -> write("gradle.properties", FLAG + "=true\n" + PARTICIPANTS + "=:app\n");
            case "root-plugin" -> write("build.gradle", "");
            case "participant-plugin" -> write("app/build.gradle", "plugins { id 'java-library' }\n");
            case "participant-java" -> write("app/build.gradle", "plugins { id 'com.netflix.nebula.publish-verification' }\n");
            case "missing-project" -> write("gradle.properties", FLAG + "=true\n" + PARTICIPANTS + "=:api,:missing\n");
            case "excluded" -> arguments.addAll(List.of("-x", ":app:verifyPublication"));
            case "disabled" -> append("app/build.gradle", "afterEvaluate { tasks.named('verifyPublication') { enabled = false } }\n");
            case "replacement-task" -> write("app/build.gradle", "tasks.register('verifyPublication')\n");
            default -> throw new AssertionError(problem);
        }
        BuildResult result = run("9.2.1", true, true, arguments.toArray(String[]::new));
        assertFalse(Files.exists(artifact("api", "jar")), result.getOutput());
        assertNull(result.task(":api:" + PUBLISH));
        if (problem.equals("excluded") || problem.equals("disabled") || problem.equals("replacement-task")) {
            assertTrue(result.getOutput().contains("Publication verification did not run for: :app"));
        }
    }

    @Test
    void changingParticipantsInvalidatesAggregation() throws IOException {
        fixture(":api", "api", "app");
        unstableDependency("app");
        String publish = ":api:" + PUBLISH;
        BuildResult first = run("9.2.1", true, false, publish);
        stored(first);
        assertNull(first.task(":app:verifyPublication"));

        write("gradle.properties", FLAG + "=true\n" + PARTICIPANTS + "=:api,:app\n");
        BuildResult changed = run("9.2.1", true, true, publish);
        assertFalse(changed.getOutput().contains("Reusing configuration cache."));
        assertRejected(changed, ":app", "release");
        assertNull(changed.task(publish));
    }

    @Test
    void ivyRepositoryPublishingUsesTheSameGate() throws IOException {
        fixture(":api,:app", "api", "app");
        Path apiBuild = directory.resolve("api/build.gradle");
        Files.writeString(apiBuild, Files.readString(apiBuild).replace("id 'maven-publish'", "id 'ivy-publish'")
                .replace("library(MavenPublication)", "library(IvyPublication)")
                .replace("repositories { maven { name", "repositories { ivy { name"));
        unstableDependency("app");
        String publish = ":api:publishLibraryPublicationToFixtureRepository";
        BuildResult rejected = run("9.2.1", true, true, publish);
        assertRejected(rejected, ":app", "release");
        assertFalse(Files.exists(directory.resolve("api/build/repository")));

        BuildResult accepted = run("9.2.1", true, false, publish, "-PignorePolicy=group");
        stored(accepted);
        success(accepted, ":verifyPublicationReport", publish);
        assertTrue(Files.isRegularFile(directory.resolve("api/build/repository/fixture/api/1.0/ivy-1.0.xml")));
        assertTrue(run("9.2.1", true, false, publish, "-PignorePolicy=group")
                .getOutput().contains("Reusing configuration cache."));
    }

    @Test
    void rootAndChildParticipantsSupportProjectDependencies() throws IOException {
        fixture(":,:api", "", "api");
        append("build.gradle", "dependencies { implementation project(':api') }\n");
        BuildResult result = run("9.2.1", true, false, ":" + PUBLISH);
        stored(result);
        success(result, ":verifyPublication", ":api:verifyPublication", ":verifyPublicationReport", ":" + PUBLISH);
        assertTrue(Files.readString(artifact("", "pom")).contains("<artifactId>api</artifactId>"));
        assertTrue(run("9.2.1", true, false, ":" + PUBLISH).getOutput().contains("Reusing configuration cache."));
    }

    @Test
    void helpAndUnrelatedTasksDoNotResolveDependenciesOrRequireNonparticipants() throws IOException {
        fixture(":api", "api", "app");
        append("api/build.gradle", """
            configurations.configureEach {
                incoming.beforeResolve { throw new GradleException('Unexpected dependency resolution') }
            }
            tasks.register('unrelated')
            """);
        BuildResult first = run("9.2.1", true, false, "help", ":app:tasks", ":api:unrelated");
        stored(first);
        assertFalse(first.getOutput().contains("Unexpected dependency resolution"));
        assertNull(first.task(":api:verifyPublication"));
        assertTrue(run("9.2.1", true, false, "help", ":app:tasks", ":api:unrelated").getOutput().contains("Reusing configuration cache."));
    }

    private void unstableDependency(String project) throws IOException {
        module("unstable", "1.0-SNAPSHOT");
        append(projectDirectory(project) + "build.gradle", "repositories { maven { url = uri('" + directory.resolve("repo").toUri() + "') } }\n" + """
            dependencies { runtimeOnly 'fixture:unstable:1.0-SNAPSHOT' }
            switch (providers.gradleProperty('ignorePolicy').getOrElse('none')) {
                case 'module': nebulaPublishVerification.ignore('fixture:unstable'); break
                case 'map': nebulaPublishVerification.ignore(group: 'fixture', name: 'unstable'); break
                case 'group': nebulaPublishVerification.ignoreGroup('fixture'); break
            }
            """);
    }

    private void module(String name, String version) throws IOException {
        write("repo/fixture/" + name + "/" + version + "/" + name + "-" + version + ".pom",
                "<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>" + name +
                        "</artifactId><version>" + version + "</version><packaging>pom</packaging></project>");
    }

    private static void assertRejected(BuildResult result, String project, String status) {
        assertEquals(TaskOutcome.FAILED, result.task(":verifyPublicationReport").getOutcome(), result.getOutput());
        assertTrue(result.getOutput().contains("Dependencies for " + project + ":"), result.getOutput());
        assertTrue(result.getOutput().contains("current project status '" + status + "'"), result.getOutput());
        assertTrue(result.getOutput().contains("fixture:unstable"), result.getOutput());
    }

    private void fixture(String participants, String... projects) throws IOException {
        write("settings.gradle", "rootProject.name = 'verification-fixture'\n" +
                Arrays.stream(projects).filter(p -> !p.isEmpty()).map(p -> "include '" + p + "'\n").reduce("", String::concat));
        write("gradle.properties", FLAG + "=true\n" + PARTICIPANTS + "=" + participants + "\n");
        write("build.gradle", "plugins { id 'com.netflix.nebula.publish-verification' }\n");
        for (String project : projects) {
            write(projectDirectory(project) + "build.gradle", """
                plugins {
                    id 'java-library'
                    id 'maven-publish'
                    id 'com.netflix.nebula.publish-verification'
                }
                group = 'fixture'
                version = '1.0'
                status = providers.gradleProperty('targetStatus').getOrElse('release')
                publishing {
                    publications { library(MavenPublication) { from components.java } }
                    repositories { maven { name = 'fixture'; url = layout.buildDirectory.dir('repository') } }
                }
                """);
        }
    }

    private BuildResult run(String version, boolean isolated, boolean fails, String... arguments) {
        List<String> args = new ArrayList<>(List.of(arguments));
        args.addAll(List.of("--configuration-cache", "--configuration-cache-problems=fail",
                "-Dorg.gradle.unsafe.isolated-projects=" + isolated,
                "-Dorg.gradle.java.home=" + System.getProperty("publishing.verificationTestJavaHome"),
                "--parallel", "--max-workers=2", "--stacktrace"));
        GradleRunner runner = GradleRunner.create().withProjectDir(directory.toFile())
                .withPluginClasspath().withGradleVersion(version).withArguments(args);
        return fails ? runner.buildAndFail() : runner.build();
    }

    private Path artifact(String project, String extension) {
        String name = project.isEmpty() ? "verification-fixture" : project.substring(project.lastIndexOf(':') + 1);
        return directory.resolve(projectDirectory(project) + "build/repository/fixture/" + name + "/1.0/" + name + "-1.0." + extension);
    }

    private static String projectDirectory(String project) {
        return project.isEmpty() ? "" : project.replace(':', '/') + "/";
    }

    private void write(String path, String content) throws IOException {
        Path target = directory.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private void append(String path, String content) throws IOException {
        Path target = directory.resolve(path);
        Files.writeString(target, Files.readString(target) + content);
    }

    private static void stored(BuildResult result) {
        assertTrue(result.getOutput().contains("Configuration cache entry stored."), result.getOutput());
    }

    private static void success(BuildResult result, String... tasks) {
        for (String task : tasks) {
            assertNotNull(result.task(task), task);
            assertEquals(TaskOutcome.SUCCESS, result.task(task).getOutcome(), task);
        }
    }
}
