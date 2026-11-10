# Experimental isolated publication verification

## Scope

The opt-in implementation of `com.netflix.nebula.publish-verification` supports
project-local configuration with automatic project discovery. Publishing one project
to a Maven or Ivy repository verifies the whole build before uploading. The default
implementation remains unchanged when `nebula.publishing.isolatedProjectsSupport`
is absent or false.

This guide covers publication verification. The same flag also selects an
[experimental project-local SCM path](isolated-scm.md), backed by the included
info-plugin 17.1.0 dependency. Inherited developer contacts,
shadow-component detection, and other publishing integrations still require their
own isolated-projects adoption work.

## Adoption

Enable the feature build-wide in the root `gradle.properties`:

```properties
nebula.publishing.isolatedProjectsSupport=true
```

Declare projects normally in `settings.gradle`:

```groovy
rootProject.name = 'example'
include 'api', 'platform:app'
```

The root automatically discovers the project paths, including intermediate projects
and the root itself. No settings helper or module-list property is required. Adding,
removing, or renaming a project updates aggregation on the next build.

Apply verification in **every project's own build file**, including the root and
non-Java intermediate projects. A base project convention plugin should normally
apply it locally, so consumers need only apply their usual convention.

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

## Read-only project discovery

Only the root enumerates project references using `Project.getAllprojects()`, maps
each reference to its immutable `IsolatedProject.path`, and stores the sorted paths:

```groovy
def projectPaths = project.getAllprojects()
    .collect { it.isolated.path }
    .sort()
```

The Java implementation uses the same boundary explicitly:

```java
List<String> projectPaths = project.getAllprojects().stream()
    .map(Project::getIsolated)
    .map(IsolatedProject::getPath)
    .sorted()
    .toList();
```

Discovery reads only project identities, without accessing another project's
mutable model. It uses no `allprojects { ... }` or `subprojects { ... }`
configuration blocks. Only strings enter the report's inputs and task dependencies;
project references are not retained in task execution state. Discovery adds no
settings plugin, metadata protocol, artifact, or dependency.

Gradle's [IsolatedProject JavaDoc](https://docs.gradle.org/9.0.0/javadoc/org/gradle/api/project/IsolatedProject.html)
defines the view as exposing properties safe to access from another project. The
API has been available since Gradle 8.8; this plugin's supported release line starts
at Gradle 9. The [isolated-project views guide](https://docs.gradle.org/9.0.0/userguide/isolated_projects.html#isolated_project_views)
describes that boundary. The enumeration and task wiring are tested on Gradle 9.0.0
and 9.2.1 with strict isolation checks.

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

No project reads or configures another project's mutable model. There are no
cross-project configuration blocks in this path. Gradle's immutable `IsolatedProject`
view supplies project identities; absolute task dependencies handle execution ordering.

| Task | Owner | Responsibility |
| --- | --- | --- |
| `verifyPublication` | Each Java project with a main source set | Produce dependency-verification results. |
| `collectPublicationVerification` | Every project | Confirm verification or contribute an empty non-Java result, then record completion. |
| `verifyPublicationReport` | Root | Require every contribution and report violations before upload. |

For Java projects the execution order is verification, contribution, root report,
then repository publication. Verification results and contribution completion are
tracked separately: a verifier may have run even when its contribution task was
disabled. Checking both states prevents that partial execution from passing the gate.

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
ignore policies, missing or skipped contributions, a 50-module build, lazy dependency
resolution for `help` and unrelated tasks, and legacy behavior with the flag disabled.
Tests never publish to a remote repository.

Source JavaDoc and Groovydoc describe the discovery contract, public task inputs,
legacy defaults, and execution-only collector lifecycle. Generate the public Groovy
API documentation with `./gradlew groovydoc`; output is under `build/docs/groovydoc`.
The Java discovery helper is package-private implementation code and is documented
in source rather than exposed as public API.
