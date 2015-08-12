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

