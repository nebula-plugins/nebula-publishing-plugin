1.9.1 / 2014-01-14
=================

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

