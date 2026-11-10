# Experimental project-local Maven SCM metadata

## Scope and dependency

The publishing flag selects project-local Maven SCM integration, using the included
`gradle-info-plugin` 17.1.1 dependency. Both the publishing and info flags must be
enabled for strict Isolated Projects support.

Upgrading from the previously bundled info-plugin 16 also brings the
[info-plugin 17 dependency-reporting changes](https://github.com/nebula-plugins/gradle-info-plugin/releases/tag/v17.0.0).
Legacy dependency maps are read-only and project-local, and legacy report reads
under configuration cache require migration to report task outputs. Those upstream
changes apply independently of the SCM flags; account for them when upgrading or
choosing a publishing-plugin release version.

No settings helper, new artifact, or manually maintained project list is needed.
For full Maven/Ivy Java-library publishing, local developer contacts and Shadow
adoption, see the [project-local publishing guide](isolated-publishing.md).

## Adoption

Enable both flags build-wide in the root `gradle.properties`:

```properties
nebula.publishing.isolatedProjectsSupport=true
nebula.info.isolatedProjectsSupport=true
```

Apply these plugins in each project's own build file, or in a locally applied
project convention plugin:

```groovy
plugins {
    id 'com.netflix.nebula.maven-scm'
    id 'com.netflix.nebula.info-scm'
}

group = 'example'
version = '1.0'
publishing.publications.create('library', MavenPublication)
```

Root application is optional for this SCM integration. Each project owns its
`scminfo` extension. Shared repositories are still discovered through immutable
directory identities, while nested repositories select their own origin. Root
extension overrides no longer propagate to children; apply shared overrides locally
through conventions.

The publishing plugin wires `scminfo.origin` directly into the native POM SCM URL.
For Git repositories it also wires a provider that converts the origin into the
project's HTTPS URL using the existing conversion rules. Overrides made later in
configuration are reflected in the generated POM, and `help` does not query the
origin. A nonconvertible Git origin retains its original value and warning. Non-Git
providers supply the SCM URL without inventing a Git project URL.

The publishing flag defaults to false, retaining existing root-extension lookup
and eager conversion. The info flag independently controls collector isolation;
enabling only publishing's flag does not isolate the old info collector.

Enable Gradle's mode separately. Prefer Gradle 9.9.0+ for the experimental path;
see the [scheduler limitation and version recommendation](isolated-publishing.md#recommended-gradle-version).
The command for the tested Gradle 9.x versions is:

```shell
./gradlew :api:generatePomFileForLibraryPublication \
  -Dorg.gradle.unsafe.isolated-projects=true \
  --configuration-cache --configuration-cache-problems=fail
```

## Validation against released dependencies

Run the SCM integration suite with the standard released dependency classpath:

```shell
./gradlew test --tests nebula.plugin.publishing.maven.IsolatedMavenScmTest \
  --no-configuration-cache --max-workers=2
```

All SCM cases run in the normal test suite without a local JAR override or a
prerelease opt-in. Flag-on fixtures enable strict Isolated Projects and fail on
configuration-cache problems. The flag-off fixture verifies legacy root inheritance
with ordinary configuration cache. No artifact is published by these tests.

The fixtures cover Gradle 9.0.0, 9.2.1 and 9.9.0 on Java 17, cold/warm cache reuse,
root-plus-child and child-only plugin application, local and late overrides,
nested Git repositories, changed remotes, no-Git fallback, unchanged URL conversion,
lazy `help`, and flag-off root inheritance. They generate POMs in temporary builds
without uploading artifacts.
