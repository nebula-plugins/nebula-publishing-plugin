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

