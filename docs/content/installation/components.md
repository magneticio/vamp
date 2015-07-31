---
title: Components
weight: 10
type: documentation
url: /installation
menu:
  main:
    parent: installation
---

# Understanding Vamp's components

To get Vamp up and running on an architecture of your choice, you first need to have a basic grip on Vamp's various components and how Vamp works with [container drivers](/documentation/installation/container_driver/). If that's all clear, then proceed to the installation steps of your choice.

- [Centos & RHEL](/installation/centos_rhel/)
- [Ubuntu](/installation/ubuntu/)
- [Debian](/installation/debian/)
- [OSX](/installation/osx/)

If that sounds to much like work, then just use our [Docker quickstart](/quick-start).

## Architecture and components

Vamp consists of multiple server and client side components that together create the Vamp platform. Before installing, it is necessary to understand the role of each component and its preferred location in typical architecture. 

Take special care to read the [configuring Vamp Router instructions](#configuring-vamp-router) as it is critical to the correct functioning of Vamp is a little bit different than all other components.

![](/img/vamp_arch.svg)

component   | purpose
------------|--------
**Vamp Core**   | Main API endpoint, business logic and service coordinator. Talks to the configured container manager (Docker, Marathon etc.) and synchronizes it with Vamp Router. Reads metrics from Vamp Pulse and archives life cycle events to Vamp Pulse. Uses a standard JDBC database for persistence (H2 and MySQL are tested).      
**Vamp Pulse**  | Consumes metrics from Vamp Router (using SSE or Kafka feeds) and consumes events from Vamp Core through REST. Makes everything searchable and actionable. Runs on Elasticsearch.
**Vamp Router** | Controls HAproxy, creates data feeds from routing information. Gets instructions from Vamp Core through REST and offers SSE and/or Kafka feeds of metric data to Vamp Pulse.
**Vamp UI**     | Graphical web interface for managing Vamp. Packaged with Vamp Core. 
**Vamp CLI**    | Command line interface for managing Vamp and integration with (shell) scripts.

## Configuring Vamp Router

Vamp Router does three things:

- Accept API requests on its API endpoint (default port: 10001)
- Instruct HAproxy how/what/where to route
- Stream feeds to Vamp Pulse

This means that Vamp Router and HAproxy should be installed on the same (V)LAN as the containers, or at least be able to transparently find the containers IP addresses. This can also be done through setting the correct gateways or setting up a local DNS.

**You need to tell Vamp Core where it can find Vamp Router's API and what Vamp Router's / HAproxy's internal IP address is**. You configure this in the `router-driver` section in `application.conf` file. See the below example:

```
# /usr/share/vamp-core/conf/application.conf
...
  router-driver {
    url = "http://104.155.30.171:10001" # Vamp Router API endpoint, external IP.
    host = "10.193.238.26"              # Vamp Router / Haproxy, internal IP.
    response-timeout = 30               # seconds
  }
...  
```  

Vamp Pulse just needs access to the API. This is configured in the `event-stream` section of Vamp Pulse's `application.conf`. See the example below:

```
# /usr/share/vamp-puls/conf/application.conf
...
  event-stream {
    driver = "sse"
    sse {
      url = "http://104.155.30.171:10001/v1/stats/stream"
      timeout {
        http.connect = 2000
        sse.connection.checkup = 3000
      }
    }
...    
```    

## Configuring Vamp Core and Vamp Pulse

Vamp Core and Vamp Pulse each ship with an `application.conf` file. This file contains all the configurable settings for both components. When installing Vamp through a package manager (yum, apt-get) you can find this file in `/usr/share/vamp-core/conf` and `/usr/share/vamp-pulse/conf` respectively.


  