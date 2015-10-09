---
title: Quick start Docker
type: documentation
weight: 20
aliases:
  - /quick-start/
  - /getting-started/
menu:
    main:
      parent: installation
    
---

# Quick start with Docker

The easiest way to get started with Vamp is by spinning up one of the Docker images stored
in the [vamp-docker repo](https://github.com/magneticio/vamp-docker) and the [public Docker hub](https://registry.hub.docker.com/repos/magneticio/).This setup will run Vamp inside a Docker container with Vamp's Docker driver.


## Step 1: Get Docker

Please install one of the following for your platform/architecture

- Docker Toolbox if on Mac OS X 10.8+ or Windows 7+, OR
- Docker 1.8.x

## Step 2: Run Vamp

Start the `magneticio/vamp-docker:latest` (or currently `magneticio/vamp-docker:0.7.10`) container, taking care to pass in the right parameters. NB If you installed Docker Toolbox please use "Docker Quickstart Terminal". At this moment we don't support Kitematic yet.

A typical command on Mac OS X running Docker Toolbox would be:
{{% copyable %}}
```
docker run --net=host -v ~/.docker/machine/machines/default:/certs -e "DOCKER_TLS_VERIFY=1" -e "DOCKER_HOST=`docker-machine url default`" -e "DOCKER_CERT_PATH=/certs" magneticio/vamp-docker:0.7.10
```
{{% /copyable %}}

Please notice the mounting of the docker machine certificates. Please set this to your specific environment. 
You can get this info by running for instance `docker-machine config default`. 
If you don't use Docker Toolbox (or Boot2Docker), set the `DOCKER_HOST` variable to whatever is relevant to your system.

> **Note 1:** When using Boot2Docker on OS X use the following command:
{{% copyable %}}
```
docker run --net=host -v ~/.boot2docker/certs/boot2docker-vm:/certs -e "DOCKER_TLS_VERIFY=1" -e "DOCKER_HOST=tcp://`boot2docker ip`:2376" -e "DOCKER_CERT_PATH=/certs" magneticio/vamp-docker:latest
```
{{% /copyable %}}


After some downloading and booting, your Docker log should say something like:

```
...Bound to /0.0.0.0:8080
```

Now check if Vamp is home on `http://{docker-machine ip default}:8080/` and proceed to our [getting started tutorial](/documentation/guides/)

![](/img/screenshots/vamp_ui_home.gif)


> **Note 2:** This runs all of Vamp's components in one container. This is definitely not ideal, but works fine for kicking the tires.
You will run into cpu, memory and storage issues pretty soon though. Also, random ports are assigned by Vamp which you might not have exposed on either Docker or your Docker Toolbox Vagrant box.  

Things still not running? [We're here to help →](https://github.com/magneticio/vamp/issues)
