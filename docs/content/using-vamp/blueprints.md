---
title: Blueprints
weight: 30
menu:
  main:
    parent: using-vamp
    identifier: using-blueprints
---

# Blueprints

Blueprints are execution plans - they describe how your services should be hooked up and what their topology should look like at runtime. This means you reference your breeds (or define them inline) and add runtime configuration to them.

Blueprints allow you to add the following extra properties:

- [endpoints](#endpoints): a stable port where the service can be reached.
- [clusters & services](#clusters-services): a cluster is agrouping of services with one purpose, i.e. two versions (a/b) of one service.
- [environment variables](/documentation/using-vamp/environment_variables/): a list of variables (interpolated or not) to be made available at runtime.
- [dialects](#dialects): a dialect is a set of native commands for the underlying container platform, i.e. Docker or Mesosphere Marathon.
- [scale](#scale): the CPU and memory and the amount of instance allocate to a service.
- [routing](/documentation/using-vamp/routings-and-filters/): how much and which traffic the service should receive.
- [filters](/documentation/using-vamp/routings-and-filters/): how traffic should be directed based on HTTP and/or TCP properties.
- [sla & escalations](/documentation/using-vamp/sla-and-escalations/): SLA definition that controls autoscaling.

This example shows some of the key concepts of of blueprints:

```yaml
---
name: my_blueprint                        # Custom blueprint name
endpoints:
  my_frontend.port: 8080/http
clusters:
  my_frontend:                            # Custom cluster name.
    services:                             # List of services
      -
        breed:
          name: some_cool_breed
        scale:                            # Scale for this service.
          cpu: 2                          # Number of CPUs per instance.
          memory: 2048                    # Memory in MB per instance.
          instances: 2                    # Number of instances
        routing:                          # Routing for this service.  
          weight: 95                      # This makes sense only with multiple services per cluster.
          filters:
            - condition: User-Agent = Chrome
      -                                          
        breed: 
          ref: some_other_breed           # Another service in the same cluster.  
        scale: large                      # Notice we used a reference to a "scale". More on this later
```

## Endpoints

An endpoint is a "stable" port that almost never changes. When creating the mapping, it uses the definition (my_frontend.port in this case) from the "first" service in the cluster definition you reference. This service can of course be changed, but the endpoint port normally doesn't. Under the hood, we do a fairly simple port mapping.

Please take care of setting the `/tcp` (default) or `/http` type for the port. Using `/http` allows Vamp to records more relevant metrics like response times and metrics.

> **Note:** endpoints are optional. You can just deploy services and have a home grown method to connect them to some stable, exposable endpoint.

## Clusters & Services

In essence, blueprints define a collection of clusters.
In turn, a cluster is a group of different services which
will appear as a single service serve a single purposes.

Common use cases are service A and B in an A/B testing scenario - usually just different
versions of the same service (e.g. canary release or blue/green deployment).

As said, clusters are configured by defining an array of services. A cluster can be
given an arbitrary name. Services are just lists or arrays of breeds.

```yaml
---
my_cool_cluster
  services
   - breed: 
      ref: my_cool_service_A      # reference to an existing breed
   -
     breed:                       # shortened inline breed
       name: my_cool_service_B
       deployable: some_container
       ...
```

Clusters and services are just organisational items. Vamp uses them to order, reference and control the actual containers and routing and traffic.

> **This all seems redundant, right?** We have a reference chain of blueprints -> endpoints -> clusters -> services -> breeds -> deployable. However, you need this level of control and granularity in any serious environment where DRY principles are taken seriously and where "one size fits all" doesn't fly.


## Dialects

Vamp allows you to use container driver specific tags inside blueprints. We call this a “dialect”. We currently support:

- `docker:`
- `marathon:`

This effectively enables you to make full use of, for instance, the underlying features like mounting disks, settings commands and providing access to private Docker registries.

### Docker dialect

The following example show how you can mount a volume to a Docker container using the Docker dialect:

```yaml
---
name: busybox
clusters:
  busyboxes:
    services:
      breed:
        name: busybox-breed
        deployable: busybox:latest
      docker:
        Volumes:
          "/tmp": ~
```

Vamp will translate this into the proper API call. Inspecting the container after its deployed should show something similar to this:

```json
...
"Volumes": {
      "/tmp": "/mnt/sda1/var/lib/docker/volumes/1a3923fa6108cc3e19a7fe0eeaa2a6c0454688ca6165d1919bf647f5f370d4d5/_data"
  },
...    
```    

### Marathon dialect

This is an example with Marathon that pulls an image from private repo, mounts some volumes, sets some labels and gets run with an ad hoc command: all taken care of by Marathon.

```yaml
---
name: busy-top:1.0
clusters:
  busyboxes:
    services:
      breed:
        name: busybox
        deployable: registry.magnetic.io/busybox:latest
      marathon:
       cmd: "top"
       uris:
         -
           "https://some_host/some_path/some_file_with_docker_credentials"
       labels:
         environment: "staging"
         owner: "buffy the vamp slayer"
       container:
         volumes:
           -
             containerPath: "/tmp/"
             hostPath: "/tmp/"
             mode: "RW"
```
**Notice the following**:

1. Under the `marathon:` tag, we provide the command to run in the container by setting the `cmd:` tag.
2. We provide a url to some credentials file in the `uri` array. As described [in the Marathon docs](https://mesosphere.github.io/marathon/docs/native-docker.html#using-a-private-docker-repository) this enables Mesos
to pull from a private registry, in this case registry.magnetic.io where these credentials are set up.
3. We set some labels with some arbitrary metadata.
4. We mount the `/tmp` to in Read/Write mode.

We can provide the `marathon:` tag either on the service level, or the cluster level. Any `marathon:` tag set on the service level will override the cluster level as it is more specific. However, in 9 out of 10 cases the cluster level makes the most sense. Later, you can also mix dialects so you can prep your blueprint for multiple environments and run times within one description.


## Scale

Scale is the "size" of a deployed service. Usually that means the number of instances (servers) and allocated cpu and memory.

Scales can be defined inline in a blueprint or they can defined separately and given a unique name. The following example is a scale named "small". `POST`-ing this scale to the `/scales` REST API endpoint will store it under that name so it can be referenced from other blueprints.

```yaml
---
name: small   # Custom name.

cpu: 2        # Number of CPUs per instance.
memory: 2048  # Memory in MB per instance.
instances: 2  # Number of instances.
```
