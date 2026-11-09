# Experimental isolated publication verification

## Scope

The opt-in implementation of `com.netflix.nebula.publish-verification` supports
project-local configuration with automatic project discovery. Publishing one project
to a Maven or Ivy repository verifies the whole build before uploading. The default
implementation remains unchanged when `nebula.publishing.isolatedProjectsSupport`
is absent or false.

This feature covers publication verification only. Inherited developer contacts,
SCM configuration, shadow-component detection, and other publishing integrations
require their own isolated-projects adoption work.

## Adoption

Enable the feature build-wide in the root `gradle.properties`:

```properties
nebula.publishing.isolatedProjectsSupport=true
```

Apply the independent settings plugin in `settings.gradle`:

```groovy
plugins {
    id 'com.netflix.nebula.publishing.settings' version '<settings-plugin-version>'
}

rootProject.name = 'example'
include 'api', 'platform:app'
```

Project discovery follows the final settings project graph, including intermediate
projects and the root. No module-list property is required. Adding, removing, or
renaming a project updates aggregation on the next build.

Apply verification in **every project's own build file**, including the root and
non-Java intermediate projects. A base project convention plugin should normally
apply it locally, so consumers need only apply their usual convention. Settings
does not apply project plugins.

```groovy
plugins {
    id 'com.netflix.nebula.publish-verification'
}
```

Java library conventions can apply it alongside the Java and publishing plugins:

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'com.netflix.nebula.publish-verification'
}

group = 'example'
version = '1.0'
status = 'release'

publishing {
    publications {
        library(MavenPublication) { from components.java }
    }
    repositories {
        maven {
            name = 'localTest'
            url = layout.buildDirectory.dir('test-repository')
        }
    }
}
```

Enable Gradle's isolated-projects mode separately. For the tested Gradle versions:

```shell
./gradlew :api:publishLibraryPublicationToLocalTestRepository \
  -Dorg.gradle.unsafe.isolated-projects=true \
  --configuration-cache --configuration-cache-problems=fail
```

The `nebulaPublishVerification` extension retains its existing `ignore`,
`ignoreGroups`, `ignore(String)`, `ignore(Map)`, and `ignoreGroup(String)` APIs.
Configure ignore policies locally in the relevant project or its convention plugin.

## Settings and project classpath boundaries

`settings-plugin/` is an independent Gradle build and artifact, not a subproject or
a runtime dependency of the project plugins. It uses Gradle and JDK APIs only, has
no external runtime dependencies, and never loads or applies a project plugin.
This is a transition that allows moving it to its own repository later without
coupling its classpath to the project plugins.

The settings plugin reads `ProjectDescriptor` objects after settings evaluation.
An isolated lifecycle action writes a small protocol marker into each project's
local metadata; only the root receives the immutable project-path catalog. Values
crossing this boundary are JDK types, with no shared implementation classes or
mutable configuration registry. Each project plugin reads only its own metadata.

The two artifacts have independent versions and release lifecycles. This draft adds
local Maven publication for tests; it does **not** publish the settings artifact to a
public repository or connect it to the project plugin's release workflow. Public
distribution must be established before consumers can use the settings plugin ID
from the plugin portal. For local development, consumers can resolve it with
`pluginManagement { includeBuild('/path/to/nebula-publishing-plugin/settings-plugin') }`
and omit the version in the settings plugin declaration.

Wrapper/init behavior, convention-plugin names and repositories, managed plugin
classpaths, and classpath locking belong to the consuming platform's higher layers.
This plugin does not implement or enforce those policies.

## Local verification and aggregation

Every project registers `collectPublicationVerification`. Java projects with a main
source set also register `verifyPublication`; their contribution depends on that
verification. Other projects contribute an empty result without resolving their
configurations. The root exclusively registers `verifyPublicationReport`, depending
on the automatically discovered contribution task paths. Local Maven and Ivy
repository publication tasks depend on the absolute root report path.

No project reads or configures another project's model. There are no cross-project
configuration blocks in this path. Gradle's immutable `IsolatedProject` view identifies
the root; absolute task dependencies handle execution ordering.

Missing project-plugin application produces a missing contribution task and fails
before upload. A skipped or disabled Java verification task produces no result and
fails its contribution. A skipped, disabled, or replaced contribution produces no
result and fails the report. These checks protect verification correctness; they
are not intended to prevent users from deliberately disabling the publishing gate.

Dependency resolution remains lazy: `help` and unrelated tasks do not resolve runtime
dependencies. Task inputs capture declared dependencies, ignore policies, and each
project's target status. Task execution retains no project model.

An execution-only build service collects results by full project path. Duplicate
leaf names remain distinct; report order and status diagnostics are deterministic.
Verification, contribution, and report tasks rerun on configuration-cache reuse to
populate a fresh collector. The service does not coordinate configuration.

Existing plugin IDs, extension methods, task types, and legacy aggregation remain
available. The concrete `verificationProjectPaths` getter defaults to an empty list
for legacy/custom report tasks; the experimental root report sets it automatically.

## Compatibility and validation

The current release line targets Gradle 9.x, as established in the
[22.0.0 breaking-change notes](https://github.com/nebula-plugins/nebula-publishing-plugin/releases/tag/v22.0.0).
Compatibility tests cover Gradle 9.0.0 and 9.2.1 in ordinary and isolated
configuration-cache modes. Java 17 runs these fixtures even when the outer test suite
runs on Java 25. The wrapper remains unchanged.

TestKit uses disposable local Maven/Ivy repositories, strict configuration-cache
checks, parallel execution, and real invalid dependencies. Coverage includes cache
reuse, changing project graphs, non-Java projects, duplicate names, mixed statuses,
ignore policies, missing or skipped contributions, a 50-module build, independent
settings-classpath resolution, and legacy behavior with the flag disabled. Tests
never publish to a remote repository.
