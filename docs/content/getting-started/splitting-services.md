---
title: 3. Splitting into services
type: documentation
weight: 40
menu:
    main:
      parent: getting-started
    
---

# 3. Splitting the monolith into services

In the [previous part](/documentation/getting-started/canary-release/) of this getting started we
did some basic canary releasing on two versions of a monolithic application. Very nice, but Vamp isn't
called the *Very Awesome Microservices Platform* for nothing. For that reason, we will be splitting
our Sava application into separate services.

## Step 1: Defining a new service topology

To prove our point, we are going to slightly "over-engineer" our services solution. This will also help
us demonstrate how we can later remove parts of our solution using Vamp. For now, we'll split the 
monolith into a topology of one frontend and two separate backend services. After our engineers
are done with coding, we can catch this new topology in the following blueprint. Please notice a couple 
of things:

1. We now have three `clusters`: `sava`, `backend1` and `backend2`. Each cluster could have multiple
services on which we could do separate canary releases and set separate filters.
2. The `sava` cluster has explicit dependencies on the two backends. Vamp will make sure these dependencies
are checked and rolled out in the right order.
3. Using `environment_variables` we connect the dynamically assigned ports and hostnames of the backend
services to the "customer facing" `sava` service. 
4. We've change the endpoint port to `9060` so it doesn't collide with the  monolithic deployment.

{{% copyable %}}

```yaml
---
name: sava:1.2
endpoints:
  sava.port: 9060

clusters:

  sava:
    services:
      breed:
        name: sava-frontend:1.2.0
        deployable: magneticio/sava-frontend:1.2.0
        ports:
          port: 80/http                
        environment_variables:
          BACKEND_1: http://$backend1.host:$backend1.ports.port/api/message
          BACKEND_2: http://$backend2.host:$backend2.ports.port/api/message

        dependencies:
          backend1: sava-backend1:1.2.0
          backend2: sava-backend2:1.2.0
      scale:
        cpu: 0.5       
        memory: 512  
        instances: 1               

  backend1:
    services:
      breed:
        name: sava-backend1:1.2.0
        deployable: magneticio/sava-backend1:1.2.0
        ports:
          port: 80/http
      scale:
        cpu: 0.5       
        memory: 512  
        instances: 1              

  backend2:
    services:
      breed:
        name: sava-backend2:1.2.0
        deployable: magneticio/sava-backend2:1.2.0
        ports:
          port: 80/http
      scale:
        cpu: 0.5       
        memory: 512  
        instances: 1
```
{{% /copyable %}}

Deploy this blueprint to the `/api/v1/deployments` endpoint with a `POST` request. Again, don't forget to set the header `Content-Type: application/x-yaml`.

Checking out the new topology in your browser (on port 9060 this time) should yield something similar to:

![](/img/screenshots/services_2backends.png)

## Step 2: Learning about environment variables & service discovery

If you were to check out the Marathon console, you would see the environment variables that we set in the blueprint. Host names and ports are configured at runtime and injected in the right parts of your running deployment. Your service/app should pick up these variables to configure itself. Luckily, this is quite easy and common in almost all languages and frameworks.

![](/img/screenshots/services_envvars.png)

Good to know is that there is no "point-to-point" wiring: the exposed host and port are actually service
endpoints. The location, amount and version of containers running behind that service endpoint can vary.
Basiscally, this a simple form of service discovery without the need to change your code or run any other daemon or agent.

{{% alert warn %}}
**Note**: Vamp Router is the central hub for service discovery. For testing this is fine, but for serious production work you would want a multi-node setup. Currently, we are putting all things in place to handle this like Zookeeper support and probably support for other technologies like ETCD  and Consul.
{{% /alert %}}

Great! We just demonstrated that Vamp can handle dependencies between services and configuring these services with host and port information at runtime. Now let's do a [more complex migration to a new service based topology →](/documentation/getting-started/merge-delete/).
