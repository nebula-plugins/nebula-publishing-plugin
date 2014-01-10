Nebula Publishing Plugin
========================
Provides publishing related plugins. Current plugins:

* 'nebula-maven-publishing' - Clean up maven output
  * Refreshes version and groupId, in case it changes after the publication is initially created
  * Creates a single publication called mavenJava
  * Sets description in the pom
  * Alias "install" task to publishMavenJavaPublicationToMavenLocal, analogous to mvn install
  * Includes Exclude Rules in the pom
  * Updates versions in the pom to resolved version, if a dynamic version was used.
* NebulaBaseMavenPublishingPlugin - Applies maven-publish plugin and provide a way of lazily contributing to a publication
* 'nebula-publishing' - Currently only applies nebula-maven-publishing plugin and can contains a ivy plugin too someday
* 'nebula-source' - Creates a sources jar, that contains the source files
* 'nebula-javadoc' - Create a javadoc jar, that contains the html files from javadoc
* 'nebula-test' - Creates a test jar, that contains test classes, and a "test" configuration that other projects can depend on

