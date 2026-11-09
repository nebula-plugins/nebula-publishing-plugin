# Experimental isolated publication verification

## Scope

The opt-in implementation of `com.netflix.nebula.publish-verification` supports
project-local configuration with an explicit build-wide participant list. Publishing
one participant to a Maven or Ivy repository still verifies every participant before
uploading. The default implementation remains unchanged when the flag is absent or
false.

This is a bounded first step. It does not establish isolated-projects support for all
publishing plugins. Inherited developer contacts, SCM configuration, shadow-component
detection, and other publishing integrations require their own adoption work.

## Adoption

Declare both properties in the **root `gradle.properties`**, or pass them build-wide
with `-P`. Do not override them in individual projects or set them as extra properties.

```properties
nebula.publishing.isolatedProjectsSupport=true
nebula.publishing.verification.projects=:api,:platform:app
```

The participant list contains comma-separated, unique, absolute project paths without
whitespace. Use `:` to include the root when it publishes a Java component. The
default list is `:`, so a single-project Java build needs only the feature flag.
The list is explicit: there is no implicit project discovery or enrollment of children.

Apply the plugin in the root `build.gradle`, even if the root has no Java component:

```groovy
plugins {
    id 'com.netflix.nebula.publish-verification'
}
```

Apply it in each participant's own build file, alongside its Java and publishing
plugins. A convention plugin can share this configuration without cross-project
configuration blocks.

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

Every project that publishes through the verification plugin must be declared in the
participant list. Nonparticipating intermediate projects need no Java or verification
plugin. A missing root report, missing participant task, or nonexistent participant
fails before upload. The report also refuses to pass when a declared verification
task was skipped or replaced by an unrelated task.

The `nebulaPublishVerification` extension retains its existing `ignore`,
`ignoreGroups`, `ignore(String)`, `ignore(Map)`, and `ignoreGroup(String)` API. Configure
ignore policies in the relevant participant or its convention plugin.

## Design and correctness

The implementation uses Gradle's immutable `IsolatedProject` view to identify the
root of the current build. The root exclusively registers `verifyPublicationReport`.
Each declared Java participant registers its own `verifyPublication`. The report depends on absolute
verification task paths; repository publication tasks depend on the absolute root
report path. No child reads or configures the root's tasks, and the root does not
inspect child project models.

Dependency resolution remains lazy: `help` and unrelated tasks do not resolve the
runtime classpath. Task inputs retain declared dependencies, ignore policies, and each
participant's target status. Runtime component resolution uses Gradle's resolution
provider without retaining project state in task execution.

The execution-time build service collects results keyed by full project path, so
duplicate leaf names do not overwrite each other. The report checks that every
declared participant contributed a result, then reports violations in project-path
order using each participant's own target status. Mixed statuses do not depend on
configuration order. Each build, including a configuration-cache reuse, reruns the
verification and report tasks against a fresh service. Verification results are not
task outputs that can be restored independently of the collector.

Existing plugin IDs, extension methods, task types, task names, and legacy aggregation
behavior remain available. The new task property `verificationProjectPaths` defaults
to an empty list for legacy/custom report tasks; the experimental root report sets it
to the explicit participant list. Dependency verification failures remain fatal.

## Validation and limitations

TestKit coverage uses disposable local Maven repositories, strict configuration-cache
checks, parallel execution, and real invalid dependencies. It covers single, sibling,
and nested participants; a root without Java; duplicate leaf names; mixed statuses;
ignore policies; missing enrollment; cache reuse; and legacy behavior with the flag
disabled. Nothing in these tests uploads to a remote repository.

The current plugin release line targets Gradle 9.x, as established in the
[22.0.0 breaking-change notes](https://github.com/nebula-plugins/nebula-publishing-plugin/releases/tag/v22.0.0).
Verification compatibility tests cover Gradle 9.0.0 and 9.2.1 in both ordinary and
isolated configuration-cache modes. Java 17 runs these fixtures so the Gradle 9.0
baseline can be tested even when the outer test suite runs on Java 25. The Gradle
wrapper remains unchanged.

Ivy repository tasks use the same verification gate, with local Ivy publishing
verified on Gradle 9.0.0 and 9.2.1. Other publishing integrations retain the scope
limitations described above.
