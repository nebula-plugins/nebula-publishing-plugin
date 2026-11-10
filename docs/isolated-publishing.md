# Experimental project-local publishing

The Maven and Ivy publishing facades can publish Java libraries and WARs with strict
Isolated Projects and configuration-cache checks using the opt-ins below. The Maven
facade also supports Java-platform BOMs. Plugins and metadata are configured in
each project, usually through a shared project
convention. No settings helper or manually maintained module list is required.

## Opt-ins

Set the publishing and info properties in the root `gradle.properties`:

```properties
nebula.publishing.isolatedProjectsSupport=true
nebula.info.isolatedProjectsSupport=true
```

The settings are independent:

| Setting | Default | Responsibility |
| --- | --- | --- |
| `nebula.publishing.isolatedProjectsSupport` | `false` | Isolated publication verification, local Maven SCM lookup, local Shadow-flag lookup, and Shadow artifact replacement without resolving task dependencies during configuration. |
| `nebula.info.isolatedProjectsSupport` | `false` | Local SCM collection in the included info plugin 17.1.1. |
| `contacts.inheritFromParent` | `true` | Set to `false` in each project's contacts extension to use only local contacts, using the included contacts plugin 8.2.0. |
| `nebula.publishing.features.detectShadowComponent.enabled` | `false` | Select the Shadow component for Maven metadata. This is a separate publishing choice, not required for isolation. |

The publishing flag does **not** disable contact inheritance. Ownership declarations
can also feed other plugins; the consuming convention chooses whether those
declarations are local. Leaving inheritance enabled still traverses parent project
extensions and fails strict isolation when those accessors run.

Upgrading from the previously included info-plugin 16 to 17 also changes dependency
reporting independently of these flags. See the [SCM migration guide](isolated-scm.md)
for that compatibility impact. The flags do not revert upstream dependency changes.

## Local application and developer contacts

For a Java library, apply this convention locally:

```groovy
plugins {
    id 'java-library'
    id 'com.netflix.nebula.maven-publish'
    id 'com.netflix.nebula.publish-verification'
    id 'com.netflix.nebula.info'
    id 'com.netflix.nebula.contacts'
}

group = 'example'
version = '1.0'
status = 'release'

contacts {
    inheritFromParent.set(false)
    contact('maintainers@example.org') {
        moniker 'Library maintainers'
        github 'example-team'
        role 'owner'
    }
}

publishing.repositories {
    maven {
        name = 'localTest'
        url = layout.buildDirectory.dir('test-repository')
    }
}
```

For Ivy, substitute `com.netflix.nebula.ivy-publish` and an `ivy` repository.
Apply `com.netflix.nebula.publish-verification` locally to **every project**, including
the root and non-Java intermediate projects. The publishing facades do not apply
verification automatically. See the [verification guide](isolated-publication-verification.md)
for root discovery and the upload gate.

Declare shared contacts once in the project convention, then apply that convention
to each consumer. Each project gets its own complete declaration; root-only contacts
and SCM overrides do not propagate in the migrated layout. Local empty contact lists
produce no developer entries. Maven developer metadata preserves ordering, fields,
GitHub-before-Twitter ID selection, and roles. Info's `Module-Owner` and `Module-Email`
also use the local contacts, filtering by their existing roles.

Declare contacts during project evaluation, before their first accessor call. The
contacts plugin caches its instance accessor's first resolution. Disabling inheritance
does not change that timing or make late mutation after resolution effective. See
the [contacts migration guide](https://github.com/nebula-plugins/gradle-contacts-plugin/blob/main/docs/project-local-inheritance.md)
for provider-backed defaults, explicit overrides and Kotlin DSL adoption.

Enable Gradle's mode separately:

```shell
./gradlew :api:publishNebulaPublicationToLocalTestRepository \
  -Dorg.gradle.unsafe.isolated-projects=true \
  --configuration-cache --configuration-cache-problems=fail
```

## Shadow

With the publishing isolation flag enabled, the optional Shadow-component flag is
read through Gradle's property provider and defaults to `false`. It can remain
absent without triggering a parent-project lookup. Declare these switches in
`gradle.properties` or with `-P`.

The opt-in replacement path supports `com.gradleup.shadow`. When `jar` is disabled,
the plugin removes the normal JAR from `apiElements` and `runtimeElements` by matching
the configured local JAR output file, then attaches the Shadow task's artifact. It
preserves other outgoing artifacts. Gradle wires the task dependencies; the plugin
does not resolve them during configuration. With the publishing flag absent or false,
the existing dependency-based artifact replacement remains in use.

If Shadow should replace the main artifact, also configure its classifier:

```groovy
tasks.named('jar') { enabled = false }
tasks.named('shadowJar') { archiveClassifier.set('') }
```

This works with the existing Maven component-detection flag either enabled or
disabled. Component selection still determines dependency metadata: the Shadow
component omits bundled dependencies from the POM; Java-component publication retains
its existing dependency behavior. Enabling isolation does not change that choice.

## BOMs, additional artifacts and signing

For Maven BOM publication, replace `java-library` in the local convention with
`java-platform`. Declare constraints through `dependencies.constraints`; use
`javaPlatform.allowDependencies()` when importing another platform. The publication
contains a Maven POM and Gradle module metadata, with no JAR. Ivy BOM publication is
outside this coverage.

For Java libraries, apply `com.netflix.nebula.source-jar` and
`com.netflix.nebula.javadoc-jar` locally to publish those artifacts. Gradle's built-in
`signing` plugin can sign the publications, including their metadata:

```groovy
signing {
    useInMemoryPgpKeys(providers.environmentVariable('SIGNING_KEY').get(),
        providers.environmentVariable('SIGNING_PASSWORD').get())
    sign(publishing.publications)
}
```

Apply `signing` before configuring this block. Signing the publication collection
also covers Ivy publications created later during project evaluation.

For WAR publication, apply `war` locally instead of `java-library`. Both publishing
facades select the web component. The WAR includes runtime libraries and excludes
`providedCompile` dependencies, following Gradle's WAR conventions.

Publication verification retains its existing Java runtime-dependency policy. WARs
are checked through their main Java source set. A BOM-only project contributes to
the verification aggregation but its constraints are not checked by that policy.
The BOM consumer fixture verifies the resolved runtime dependencies in the consumer
library; it does not add constraint validation to the BOM itself.

## Validation and limits

The normal test suite uses released dependencies, strict isolation, parallel
configuration, and configuration-cache storage and reuse. Tests publish only into
disposable local Maven/Ivy repositories.

Coverage includes:

- Developer POMs on Gradle 9.0.0 and 9.2.1: root/child and child-only application,
  nested projects, empty contacts, both plugin orders, multiple roles, changed local
  metadata, deleted-output regeneration, legacy inherited contacts, and strict
  rejection of unmigrated inheritance.
- Full Maven and Ivy Java-library publishing on Gradle 9.0.0 and 9.2.1 with info
  17.1.1, contacts 8.2.0, project dependencies, local external dependencies, local
  SCM overrides, ownership and late broker metadata. Gradle 9.2.1 also covers the
  publishing facades without info applied.
- Shadow 8.3.9 and 9.3.1 on Gradle 9.2.1: normal plus Shadow JARs, replacement
  JARs, Maven Shadow-component selection, Ivy publication, and artifact contents.
  Replacement is also exercised on Gradle 9.0.0. Gradle 9.2.1 checks renamed main
  JAR outputs, preservation of additional artifacts, and dependency-resolution-free
  `help`.
- Maven Java-platform BOMs on Gradle 9.0.0, 9.2.1 and 9.8.2: nested projects,
  constraints, imported platforms, POM/module metadata, consumer dependency versions,
  changed constraints, deleted-metadata regeneration and dependency-resolution-free
  `help`. The optional Shadow-component flag is absent in these fixtures.
- Signed Maven/Ivy libraries on Gradle 9.0.0, 9.2.1 and 9.8.2: compiled classes,
  sources, Javadoc and metadata, with cryptographic verification of every detached
  signature. Keys are generated in disposable fixtures without external GPG or a
  user keyring. Gradle 9.2.1 also checks changed sources, re-signing, deleted-output
  regeneration and dependency-resolution-free `help`.
- Maven/Ivy WAR publication on Gradle 9.0.0, 9.2.1 and 9.8.2: web content,
  compiled classes, packaged/excluded dependencies and descriptor packaging.
  Gradle 9.2.1 also checks snapshot dependency rejection before upload on cold and
  warm configuration caches.

These compatibility fixtures use Java 17, including when the outer test suite runs
on newer JVMs. They validate these configurations, not every third-party plugin or
publication type. The older `com.github.johnrengelman.shadow` ID and additional info
reporters are outside this end-to-end matrix. Keep the feature opt-in while adopting
other combinations.

Gradle's standard Maven/Ivy publishing tasks can target an Artifactory repository.
The separate JFrog Gradle plugin (`com.jfrog.artifactory`) and its publishing tasks
are not supported.

Run the new coverage with:

```shell
./gradlew test \
  --tests nebula.plugin.publishing.maven.IsolatedMavenDeveloperTest \
  --tests nebula.plugin.publishing.IsolatedPublishingFacadeTest \
  --tests nebula.plugin.publishing.IsolatedPublicationTypesTest \
  --no-configuration-cache --max-workers=2
```

Generate the source API documentation with `./gradlew groovydoc`.
