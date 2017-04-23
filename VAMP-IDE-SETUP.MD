# VAMP IDE Setup

This document describes on how to configure and run from the IntelliJ IDE for developing
VAMP. All the core developers are using IntelliJ as their main IDE for Scala
development, if you want to use another IDE that's fine however we cannot create
a read-me for each possible IDE. If you have any questions or remarks, please shoot
us a message at [Gitter](https://gitter.im/magneticio/vamp).

Our current back-end setup consists of domain specific modules that are
pluggable to the vamp-core module. The current modules are:

* vamp (core)        - [GitHub Repository](https://github.com/magneticio/vamp)
* vamp-runner        - [GitHub Repository](https://github.com/magneticio/vamp-runner)
* vamp-lifter        - [GitHub Repository](https://github.com/magneticio/vamp-lifter)
* vamp-kubernetes    - [GitHub Repository](https://github.com/magneticio/vamp-kubernetes)
* vamp-rancher       - [GitHub Repository](https://github.com/magneticio/vamp-rancher)
* vamp-docker        - [GitHub Repository](https://github.com/magneticio/vamp-docker)
* vamp-zookeeper     - [GitHub Repository](https://github.com/magneticio/vamp-zookeeper)
* vamp-etc           - [GitHub Repository](https://github.com/magneticio/vamp-etcd)
* vamp-consul        - [GitHub Repository](https://github.com/magneticio/vamp-consul)
* vamp-haproxy       - [GitHub Repository](https://github.com/magneticio/vamp-haproxy)
* vamp-elasticsearch - [GitHub Repository](https://github.com/magneticio/vamp-elasticsearch)
* vamp-redis         - [GitHub Repository](https://github.com/magneticio/vamp-redis)
* vamp-mysql         - [GitHub Repository](https://github.com/magneticio/vamp-mysql)
* vamp-dcos          - [GitHub Repository](https://github.com/magneticio/vamp-dcos)

All these modules are pluggable for the certain VAMP stack you are developing for.
You could load up all the modules and create all the dependencies, but also just
the modules that you need, for developing a specific feature.

See [Dependency trees](#dependency-trees) to see which modules you need for which stack.

# Setting up IntelliJ

We assume that you have a running IntelliJ with the Scala-plugin enabled. First we
need to import vamp-core into IntelliJ.

## Loading vamp-core

TODO

## Loading a vamp-module

TODO

`File > New > Module...` Scala Module -> select the existing source.

## Configuring project-settings

Right click on `vamp [root] > module settings`

* adding modules
* adding libraries

IntelliJ will figure out the dependencies for reloading and running, which will
be explained in the next section.

TODO

# Running VAMP

TODO

(Boot class)
(config values) `-Dlogback.configurationFile=/path/to/vamp/conf/logback.xml -Dconfig.file=/path/to/vamp/conf/application.conf `
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

# Contributing to this document
