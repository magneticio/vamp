---
title: Container driver
weight: 11
type: documentation
menu:
  main:
    parent: installation
---

# Configuring a container driver

Vamp supports multiple container platforms and will expand this selection in the future. When installing Vamp, you choose your container platform and configure the appropriate driver. Think of how ORM's work: a single DSL/language with support for multiple databases through a driver system.

Currently supported:

- [Docker](#docker)
- [Mesosphere Marathon](#mesosphere-marathon)

> **Note:** As mentioned in the overview, Vamp Router **should always** be able to find and route traffic to addresses of any of the containers deployed, regardless of the container driver.

## Docker

Vamp can directly talk to a Docker daemon and its driver is configured by default. This is useful for local testing.
Vamp can even run inside Docker while deploying to Docker: check out our [quick start](/quick-start/).

1. Install Docker as per [Docker's installation manual](https://docs.docker.com/installation/)

2. Check the `DOCKER_*` environment variables Vamp uses to connect to Docker, i.e.

    ```
    DOCKER_HOST=tcp://192.168.59.103:2376
    DOCKER_TLS_VERIFY=1
    DOCKER_CERT_PATH=/Users/tim/.boot2docker/certs/boot2docker-vm
    ```

3. If Vamp can't find these environment variables, it falls back to using the `unix:///var/run/docker.sock` Unix socket for communicating with Docker.

4. Update the container-driver section in Vamp's config file. If you use a package installer like `yum` or `apt-get` you can find this file in `/usr/share/vamp-core/conf/application.conf`:

    ```
    ...
    container-driver {
      type = "docker"
      response-timeout = 30 # seconds, timeout for container operations
    }
    ...
    ```
5. (Re)start Vamp Core with `service vamp-core restart` or by restarting the Java process by hand.   


> **Note:** as of release 0.7.9, Vamp's Docker driver only supports Docker 1.7+. Please see [Github issue](https://github.com/docker/docker/issues/14365) in Docker's repo on why this is necessary.


## Mesosphere Marathon

Vamp can use the full power of Marathon running on either a DCOS cluster or custom Mesos cluster.

1. Set up a DCOS cluster using Mesosphere's [assisted install](https://mesosphere.com/product/) on AWS.

2. Alternatively, build your own Mesos/Marathon cluster. We've listed some tutorials and scripts here to help you get started:

  - [Mesos Ansible playbook](https://github.com/mhamrah/ansible-mesos-playbook)
  - [Mesos Vagrant](https://github.com/everpeace/vagrant-mesos)
  - [Mesos Terraform](https://github.com/ContainerSolutions/terraform-mesos)

3. Whichever way you set up Marathon, in the end you should be able to see something like this:

    ![](/img/marathon-screenshot.png)

4. Make a note of Marathon endpoint (host:port) and update the container-driver section in Vamp's config file. If you use a package installer like `yum` or `apt-get` you can find this file in `/usr/share/vamp-core/conf/application.conf`. Set the "url" option to the Marathon endpoint.

    ```
    ...
    container-driver {
      type = "marathon"
      url = "http://<marathon_host>:<marathon_port>" 
      response-timeout = 30 # seconds, timeout for container operations
    }
    ...
    ```    
5. (Re)start Vamp Core with `service vamp-core restart` or by restarting the Java process by hand.   

