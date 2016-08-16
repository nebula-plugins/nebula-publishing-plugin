4.9.1 / 2016-08-16
==================

* Republish after adding `nebula.compileApi` to gradle plugin portal block

4.9.0 / 2016-08-16
==================

* Add scope to gradle to automatically put those dependencies in the compile scope of the pom and ivy files

4.5.0 / 2016-03-21
==================

* Make ivy-excludes conditional on Gradle version to avoid duplicate excludes due to Gradle 2.11's support for Ivy excludes

4.4.4 / 2015-11-30
==================

* Add conf to test jar and add safety checks to origin URL manipulation

4.4.3 / 2015-11-18
==================

* Bugfix to add conf and type to source and javadoc jar artifacts published to Ivy repositories

4.4.2 / 2015-11-12
==================

* Bugfix to guard against trying to apply dependency excludes on transitive deps

4.4.1 / 2015-11-04
==================

* Fix build.gradle to publish to plugin portal

4.4.0 / 2015-11-04
==================

* Removed nebula.maven-dependencies and nebula.ivy-dependencies because we no longer care about publishing WARs and building insight from their dependencies
* Added the minimal set of configurations to Ivy files necessary to make them Maven interoperable.
* BUGFIX: Update nebula.test-jar to behave as documented during interproject dependency

4.3.2 / 2015-11-03
==================

* BUGFIX: so that if these plugins are applied without a language plugin they do not fail the project

4.3.1 / 2015-10-28
==================

* Partial bugfix for applying without a language plugin

4.3.0 / 2015-10-26
==================

* Get selection rules into place before after evaluate, this should allow the plugin to work with other plugins that use
  project.properties or otherwise resolve the publishing model

4.2.2 / 2015-10-20
==================

* Fix issue where a changed status was not being reflected in the published ivy file
* move to gradle 2.8

4.2.1 / 2015-09-15
==================

* Fix nebula.maven-dependencies-jar plugin, update test to actually test plugin

4.2.0 / 2015-09-15
==================

* Add nebula.maven-resolved-dependencies-jar for projects that cannot use nebula.maven-resolved-dependencies due to other plugins resolving publications early

4.1.0 / 2015-09-14
==================

* add nebula.maven-dependencies-jar for projects that cannot use nebula.maven-dependencies because another plugin is resolving publishing before we can add our component
* move to gradle 2.7

4.0.1 / 2015-08-28
==================

* Release with fix for Gradle Plugin Portal so that v4.0.1 shows up there

4.0.0 / 2015-08-28
==================

* renames of multiple classes and plugins
* deprecation of nebula.test-jar, we feel this is best met by a project for common testing helpers
* Move to gradle-2.6 as our base environment  

3.0.2 / 2015-08-13
==================

* Deprecation warnings for old plugin nebula-javadoc-jar, nebula-source-jar, nebula-test-jar
* Bug fix for multiproject builds with project dependencies via Viacheslav Shvets (slavashvets)                                                                                            
* Add in name and description to pom

3.0.1 / 2015-08-12
==================

* Fix handling of direct dependencies with omitted versions

3.0.0 / 2015-08-07
==================

* Move to semantic versioning
* Fix issue detecting optional dependencies

2.4.0 / 2015-07-23
==================

* Complete rewrite

2.2.0 / 2015-01-30
===================

* Move to gradle 2.2.1

2.0.1 / 2015-01-07
==================

* Fix a bug caused by gradle-2.x or groovy-2.x where on creating a pom for a war project that references another project in a multiproject build a plugin method couldn't be found

2.0.0 / 2014-09-17
==================

* Uses gradle 2.0

1.12.1 / 2014-06-11
===================

* Upgradle nebula-plugin-plugin to 1.12.1

1.9.7 / 2014-05-06
==================

* Remove semantic version sorting of ivy dependencies, to make it work with non semantic version strings

1.9.6 / 2014-01-24
==================

* Introduce a CustomComponent

1.9.5 / 2014-01-21
==================

* Make publish valid
* Include name in pom

1.9.4 / 2014-01-21
==================

* Upgrade nebula-plugin-plugin

1.9.3 / 2014-01-20
==================

* Ensuring packaging is set correct for normal projects

1.9.2 / 2014-01-20
==================

* bug fixes

1.9.1 / 2014-01-14
==================

* Refactor into separate maven and ivy packages
* Add NebulaBaseIvyPublishingPlugin for good patterns for generating ivy files
* Add CustomComponentPlugin, for extending the default SoftwareComponent model, which is needed in ivy files
* resolved-ivy plugin, for producing resolved descriptors
* resolved-maven plugin, for replacing dynamic version ranges with their resolved values
* Add groovy.util.Node classes to help updating a node easily
* Add confs-visible plugin to make runtime and compile confs visible
* Add nebula-sign plugin, to hide the details of signing while also supporting maven-publish

1.9.0 / 2014-01-10
=================

* Initial release

