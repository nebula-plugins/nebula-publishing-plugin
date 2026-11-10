# Experimental project-local Maven SCM metadata

## Status and release prerequisite

This branch adds project-local SCM integration to the publishing flag. Strict
Isolated Projects support also requires the corresponding SCM collector update in
`gradle-info-plugin` ([upstream PR](https://github.com/nebula-plugins/gradle-info-plugin/pull/131)).
The current production lock remains at 16.0.0, which does not
implement that collector path. Keep this change in draft until a release containing
the upstream implementation is available, then update the dependency lock and make
the strict SCM tests unconditional before merging.

No settings helper, new artifact, or manually maintained project list is needed.
The full Maven publishing facade still needs separate validation and migration of
inherited developer contacts, Shadow integration, and other components.

## Adoption

After adopting an info-plugin release with isolated SCM collection, enable both
flags build-wide in the root `gradle.properties`:

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

Enable Gradle's mode separately, using the tested Gradle 9.x command:

```shell
./gradlew :api:generatePomFileForLibraryPublication \
  -Dorg.gradle.unsafe.isolated-projects=true \
  --configuration-cache --configuration-cache-problems=fail
```

## Combined-source validation before the upstream release

Build the patched info-plugin checkout with its ordinary `jar` task. Then provide
that implementation JAR to the publishing tests:

```shell
./gradlew test --tests nebula.plugin.publishing.maven.IsolatedMavenScmTest \
  -Ppublishing.scmInfoPluginJar=/absolute/path/to/gradle-info-plugin-implementation.jar \
  --no-configuration-cache --max-workers=2
```

This substitutes the JAR only in these TestKit fixtures. It does not change the
published dependency, production lock, or normal regression tests. No artifact is
published. Without the property, the SCM tests run ordinary configuration-cache
checks against the locked release; cases requiring upstream local-provider behavior
are explicitly skipped. With the property, every SCM case runs and flag-on fixtures
enable strict Isolated Projects.

The fixtures cover Gradle 9.0.0 and 9.2.1 on Java 17, cold/warm cache reuse,
root-plus-child and child-only plugin application, local and late overrides,
nested Git repositories, changed remotes, no-Git fallback, unchanged URL conversion,
lazy `help`, and flag-off root inheritance. They generate POMs in temporary builds
without uploading artifacts.
