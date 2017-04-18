# VAMP IDE Setup

This document describes on how to configure and run from the IntelliJ IDE for developing
VAMP. All the core developers are using IntelliJ as their main IDE for Scala
development, if you want to use another IDE that's fine however we cannot create
a read-me for each possible IDE. If you have any questions or remarks, please shoot
us a message at [gitter](https://gitter.im/magneticio/vamp).

Our current back-end setup consists of domain specific modules that are
pluggable to the vamp-core module. The current modules are:

* vamp (core) - [github](https://github.com/magneticio/vamp)
* vamp-runner - [github](https://github.com/magneticio/vamp-runner)
* vamp-lifter - [github](https://github.com/magneticio/vamp-lifter)
* vamp-kubernetes - [github](https://github.com/magneticio/vamp-kubernetes)
* vamp-rancher - [github](https://github.com/magneticio/vamp-rancher)
* vamp-docker - [github](https://github.com/magneticio/vamp-docker)
* vamp-zookeeper - [github](https://github.com/magneticio/vamp-zookeeper)
* vamp-etc - [github](https://github.com/magneticio/vamp-etcd)
* vamp-consul - [github](https://github.com/magneticio/vamp-consul)
* vamp-haproxy - [github](https://github.com/magneticio/vamp-haproxy)
* vamp-elasticsearch - [github](https://github.com/magneticio/vamp-elasticsearch)
* vamp-redis - [github](https://github.com/magneticio/vamp-redis)
* vamp-mysql - [github](https://github.com/magneticio/vamp-mysql)
* vamp-dcos - [github](https://github.com/magneticio/vamp-dcos)

All these modules are pluggable for the certain VAMP stack you are developing for.
You could load up all the modules and create all the dependencies, but also just
the modules that you need, for developing a specific feature.
See (Dependency trees)[#Dependency trees] to see which module you need for which stack.

# Setting up IntelIJ

We presume you have a running IntelliJ with the Scala-plugin enabled. First we
need to import vamp-core into IntelliJ.

## Loading vamp-core

TODO

## Loading a vamp-module

TODO

## Configuring project-settings

TODO

# Running VAMP

TODO

(Boot class)
(config values)
(creating/editing the application.conf)

# Development stacks

TODO (describes the development stacks from vamp-docker-images)

# Dependency trees

This section shows the dependency trees for each possible stack to run and develop
vamp on.

## DCOS/Mesos stack

TODO

## Kubernetes stack

TODO

## Rancher stack

TODO

## Docker stack

TODO
