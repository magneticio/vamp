---
title: Installation
type: documentation
weight: 10
aliases:
  - /getting-started/
menu:
    main:
      parent: getting-started
    
---


# Before you install...
<img src="/img/vamp_services.svg" id="get_started_overview">

Before we really get started, you should know that Vamp is truly modular and
consists of three separate services: Core, Pulse and Router. Each of these services has their own
role and purpose:

- **Vamp Core**: Core is the brains of the system. It contains the REST API you send your requests to,
it speaks to the underlying PaaS/Container Manager and ties together the other two services. 
- **Vamp Router**: All routing duties are taken care of by Vamp Router. It is a perfectly usable component
by itself, but really comes together when Vamp Core is in charge of what it routes and where.
- **Vamp Pulse**: Pulse is the metrics and event store for the whole Vamp platform. Core and Router both store data in it and Core also uses it as the input for SLA management and events storage.

Knowing that this is how Vamp works under the hood can maybe help you troubleshoot any teething problems and
in general help you better understand what's going on.


# Now let's install!

By far the easiest way to get started with Vamp is by spining up one of the pre-baked Docker images stored
in the [vamp-docker repo](https://github.com/magneticio/vamp-docker). This repo contains a number of 
`Dockerfile` files and `docker-compose.yml` to help setup Vamp in different situations. 
They should run anywhere Docker runs, but most situations will probably be on your laptop. 
Probably a recent Macbook.

## Prerequisites

You should have Docker installed which, on a Macbook with OSX, also means [Boot2Docker](http://boot2docker.io/) and its dependencies. 

Please make double sure Boot2Docker is installed correctly and up & running!

    $ boot2docker status
    running

Please make double sure the Docker command can reach Boot2Docker. You set this by having boot2docker generate the right settings:

    $ boot2docker shellinit
    
Export the settings boot2docker provides and check if you can run a simple `docker ps`

    $ docker ps

With the prerequisites sorted, pick one of the following options:

## Option 1: Run Vamp on Docker

This setup will run Vamp inside a Docker container with Vamp's Docker driver. This allows you to test drive Vamp really easily.

A typical command on Macbook running Boot2Docker would be:

{{% copyable %}}
```
docker run --net=host -v /Users/tim/.boot2docker/certs/boot2docker-vm:/certs -e "DOCKER_TLS_VERIFY=1" -e "DOCKER_HOST=tcp://`boot2docker ip`:2376" -e "DOCKER_CERT_PATH=/certs" magneticio/vamp-docker:latest
```
{{% /copyable %}}

**Please notice** the mounting (`-v /Users/tim/...`) of the boot2docker certificates. Please set this to your specific environment. You can get this info by running `boot2docker config`.

If you don't use Boot2Docker, set the `DOCKER_HOST` variable to whatever is relevant to your system.

{{% alert warn %}}
**Note:** This runs all of Vamp's components in one container. This is definitely not ideal, but works fine for kicking the tires.
You will run into cpu, memory and storage issues pretty soon though. Also, random ports are assigned by Vamp which you might not have exposed on either Docker or your Boot2Docker Vagrant box.
{{% /alert%}}

Now check if Vamp is home on port 8080 by doing a GET on the `info` endpoint, i.e.: `http://boot2docker_ip:8080/api/v1/info`

```json
{
  "message":"Hi, I'm Vamp! How are you?",
  "vitals":{
    "operating_system":{
      "name":"Linux",
      "architecture":"amd64",
      "version":"3.16.1-tinycore64",
      "available_processors":4.0,
      "system_load_average":0.06
    }
  }
}
```

## Option 2: Run Vamp with an external Mesos cluster

If you want to tweak things a bit more, grab the Vamp all-in-one Docker image and provide your own Mesosphere stack.

1. Set up a Mesosphere stack on Google Compute Engine or Digital
Ocean really easily using the great wizards at [Mesosphere.com](https://mesosphere.com/downloads/).

2. After the wizard is finished, we are going to do two things:

    a) Make a note of the Marathon endpoint, typically something like `http://10.143.22.49:8080`
    We are going to pass this in as an environment variable to our Vamp Docker container
    
    b) Deploy vamp-router to the Mesosphere stack. You can use the piece of JSON below for this. Save it as
    `vamp-router.json` and just `POST` it to the Marathon `/v2/apps` endpoint, for example using curl:

{{% copyable %}}

    curl -v -H "Content-Type: application/json" -X POST --data @vamp-router.json http://10.143.22.49:8080/v2/apps

{{% /copyable %}}

{{% copyable %}}
```json
    {
      "id": "main-router",
      "container": {
          "docker": {
              "image": "magneticio/vamp-router:latest",
              "network": "HOST"
          },
          "type": "DOCKER"
      },
      "instances": 1,
      "cpus": 0.5,
      "mem": 512,
      "env": {},
      "ports": [
          0
      ],
      "args": [
          ""
      ]
    }

```
{{% /copyable %}}        

  Now, check the IP number of the host it gets deployed to eventually. In our case this 
  was `10.16.107.232`
  
  ![](/img/marathon_router.png)

  {{% alert warn %}}
**Note**: Astute readers will instantly spot a weakness here: in this setup the IP address of our Router can change as Marathon/Mesos decides to reassign our container to some other machine. This
is 100% true. We just use this simple setup for this getting started tutorial. Any serious setup
would have Router assigned to at least a dedicated box, IP, DNS etc.
  {{% /alert %}}


3. Pull the latest Vamp image.
{{% copyable %}}<pre> docker pull magneticio/vamp-mesosphere:latest</pre>{{% /copyable %}}    

4. Start up Vamp while providing it with the necessary external inputs. Note: these are examples from our test!
{{% copyable %}}
```
export MARATHON_MASTER=10.143.22.49 
export VAMP_MARATHON_URL=http://$MARATHON_MASTER:8080
export VAMP_ROUTER_HOST=10.16.107.232
export VAMP_ROUTER_URL=http://$VAMP_ROUTER_HOST:10001 
```
{{% /copyable %}}
    Copy & paste these into a `docker run` command, like this
{{% copyable %}}

```
docker run -d --name=vamp -p 81:80 -p 8081:8080 -p 10002:10001 -p 8084:8083 -e VAMP_MARATHON_URL=http://$MARATHON_MASTER:8080 -e VAMP_ROUTER_URL=http://$VAMP_ROUTER_HOST:10001 -e VAMP_ROUTER_HOST=$VAMP_ROUTER_HOST magneticio/vamp-mesosphere:latest
```  
{{% /copyable %}}


5. You should now check the log output using `docker logs -f vamp` and check if Vamp is home by doing a GET on the `hi` endpoint, i.e.: `http://192.168.59.103:8081/api/v1/info`

```json
  {
    "message":"Hi, I'm Vamp! How are you?",
    "vitals":{
      "operating_system":{
        "name":"Linux",
        "architecture":"amd64",
        "version":"3.16.1-tinycore64",
        "available_processors":4.0,
        "system_load_average":0.06
      }
    }
  }
```
