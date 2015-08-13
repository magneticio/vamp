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

Take special care to read the [configuring Vamp Router instructions](/documentation/installation/configuration/#vamp-router) as it is critical to the correct functioning of Vamp is a little bit different than all other components.

![](/img/vamp_arch.svg)

component   | purpose
------------|--------
**Vamp Core**   | Main API endpoint, business logic and service coordinator. Talks to the configured container manager (Docker, Marathon etc.) and synchronizes it with Vamp Router. Reads metrics from Vamp Pulse and archives life cycle events to Vamp Pulse. Uses a standard JDBC database for persistence (H2 and MySQL are tested).      
**Vamp Pulse**  | Consumes metrics from Vamp Router (using SSE or Kafka feeds) and consumes events from Vamp Core through REST. Makes everything searchable and actionable. Runs on Elasticsearch.
**Vamp Router** | Controls HAproxy, creates data feeds from routing information. Gets instructions from Vamp Core through REST and offers SSE and/or Kafka feeds of metric data to Vamp Pulse.
**Vamp UI**     | Graphical web interface for managing Vamp. Packaged with Vamp Core. 
**Vamp CLI**    | Command line interface for managing Vamp and integration with (shell) scripts.

Please see our [configuration documentation](/documentation/configuration) on how to configure all Vamp components.

  