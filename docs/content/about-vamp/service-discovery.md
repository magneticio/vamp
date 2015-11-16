---
title: Service discovery
weight: 40
menu:
  main:
    parent: about-vamp
---

# How does Vamp do service discovery?

Vamp uses a service discovery pattern called [server-side service discovery](http://microservices.io/patterns/server-side-discovery.html). This pattern allows service discovery without the need to change your code or run any other daemon or agent. The provided link explains the general pro's and cons in good detail. In addition to service discovery, Vamp also functions as a [service registry](http://microservices.io/patterns/service-registry.html).

For Vamp, we recognise the following benefits of this pattern:

* No code injection needed.
* No extra libraries or agents needed.
* platform/language agnostic: it's just HTTP.
* Easy integration using ENV variables.

## Workflow

The general workflow for creating and publishing a service is as follows.

**DSL -> Vamp Core API -> Vamp Router -> HAproxy**


1. The user describes a service and its desired endpoint port in the Vamp DSL.
2. The service is deployed to the configured container manager by Vamp Core.
3. Vamp Core instruct Vamp Router to set up service endpoints.
4. Vamp Router takes care of configuring HAproxy, making the services available.

> **Note:** services do not register themselves. They are explicitly created, registered in the Vamp database
and provisioned on the load balancer.

After this, the user is free to scale up/down or in/out the service either by hand or using Vamp's
auto scaling functionality. The endpoint is stable.

## Discovering a service

So, how does one service find a dependent service? Services are found by just referencing them in the DSL.
Take a look at the following example:

```yaml
---
name: my_blueprint:1.0
clusters:
  my_frontend_cluster:
    services:
      breed:
        name: my_frontend_service:0.1
        deployable: docker://company/frontend:0.1
        ports:
          port: 8080/http
        dependencies:
          backend: my_backend_service:0.3
        environment_variables:
         BACKEND_HOST: $backend.host
         BACKEND_PORT: $backend.ports.jdbc
      scale:
        instances: 3         
  my_backend_cluster:
    services:
      breed:
        name: my_backend_service:0.3
        deployable: docker://company/backend:0.3
        ports:
          jdbc: 8080/tcp
      scale:
        instances: 4
```          

1. We have a **frontend** cluster and a **backend** cluster. These are just organisational units.
2. The frontend cluster runs just one version of our service, consisting of **three instances**.
3. The frontend service has a hard **dependency on a backend (tcp) service**.
4. We **reference the backend** by name, `my_backend:0.3`, and assign it a label, in this case just `backend`
5. We use the label `backend` to get the **host and a specific port** (`jdbc`) from this backend.
6. We **assign these values to environment variables** that are exposed in the container runtime.
7. Any frontend service now has access to the location of the dependent backend service.

> **Note:** There is no point-to-point wiring. The `$backend.host` and `$backend.ports.jdbc` variables resolve to service endpoints Vamp automatically sets up and exposes.


