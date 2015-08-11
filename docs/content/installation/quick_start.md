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

- Docker 1.7.x
- Boot2Docker 1.7.x if on Mac OSX

> **Note 2**: As of Vamp 0.7.9, we don't support Docker 1.6 anymore when using the Docker driver.

## Step 2: Run Vamp

Start the `magneticio/vamp-docker:latest` container, taking care to pass in the right parameters. A typical command on Macbook running Boot2Docker would be:
{{% copyable %}}
```
docker run --net=host -v ~/.boot2docker/certs/boot2docker-vm:/certs -e "DOCKER_TLS_VERIFY=1" -e "DOCKER_HOST=tcp://`boot2docker ip`:2376" -e "DOCKER_CERT_PATH=/certs" magneticio/vamp-docker:latest
```
{{% /copyable %}}

Please notice the mounting of the boot2docker certificates. Please set this to your specific environment. You can get this info by running `boot2docker config`. If you don't use Boot2Docker, set the `DOCKER_HOST` variable to whatever is relevant to your system.

After some downloading and booting, your Docker log should say something like:

```
...Bound to /0.0.0.0:8080
```

Now check if Vamp is home on `http://boot2docker_ip:8080/` and proceed to our [getting started tutorial](/documentation/guides/)

![](/img/screenshots/vamp_ui_home.gif)


> **Note 1:** This runs all of Vamp's components in one container. This is definitely not ideal, but works fine for kicking the tires.
You will run into cpu, memory and storage issues pretty soon though. Also, random ports are assigned by Vamp which you might not have exposed on either Docker or your Boot2Docker Vagrant box.  

Things still not running? [We're here to help →](https://github.com/magneticio/vamp/issues)