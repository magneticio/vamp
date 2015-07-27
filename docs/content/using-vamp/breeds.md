---
title: Breeds & Blueprints
weight: 20
menu:
  main:
    parent: using-vamp
---
# Breeds

Breeds are static descriptions of applications and services available for deployment. Each breed is described by the DSL in YAML notation or JSON, whatever you like. This description includes name, version, available parameters, dependencies etc.
To a certain degree, you could compare a breed to a Maven artefact or a Ruby Gem description.

> **Note:** Breeds can be referenced by name. This means you can describe a breed in detail just once, and then use its name in a blueprint.

## Deployables

Deployables are pointers to the actual artefacts that get deployed. Vamp supports Docker containers or can support any other artefacts supported by your container manager. Let's start with an example breed that deploys a Docker container:

```yaml
---
name: my_breed:0.1
deployable: company/my_frontend_service:0.1

ports:
  web: 8080/http   
```

This breed, with a unique name, describes a deployable and the port it works on. By default, the deployable is a Docker container. We could make this also explicit by adding `docker://`. The following statements are equivalent.

```yaml
deployable: company/my_frontend_service:0.1
```

```yaml
deployable: docker://company/my_frontend_service:0.1
```
Docker images are pulled by your container manager from any of the repositories configured. This can be private repo's but by default are the public Docker hub.

> **Note:** running "other" artefacts such as zips or jars heavily depend on the underlying container manager, configuration of these deployables are explained in the [blueprints](#blueprints) section.

## Ports

The `ports` property is an array of named ports together with their protocol. It describes on what ports the deployables is offering services to the outside world. Let's look at the following breed:

```yaml
---
name: my_breed:0.1
deployable: company/my_frontend_service:0.1

ports:
  web: 8080/http
  admin: 8081/http
  redis: 9023/tcp   
```

Notice we can give the ports sensible names. This specific deployable has `web` port for customer traffic, an `admin` port for admin access and a `redis` port for some caching probably. These names come in handy when we later compose different breeds in blueprints.

## Dependencies & environment variables


Breeds can have dependencies on other breeds. These dependencies should be stated
explicitly, similar to how you would do in a Maven pom.xml, a Ruby Gemfile or similar
package dependency systems. 

In a lot of cases, dependencies coexist with interpolated
environment variables or constants because exact values are not known untill deploy time. Have a look at the following breed and read the comments carefully.

```yaml
---
name: my_breed:0.1
deployable: company/my_frontend_service:0.1

ports:
  web: 8080/http

environment_variables:
  DB_HOST: $db.host 
  DB_PORT: $db.ports.port 
  URL: jdbc://$db.host:$db.ports.port/schema/foo

dependencies:
  db: mysql 
```
Any value starting with $ will be interpolated to a specific value at runtime. The `$` value is escaped by `$$`. A more strict notation is `${some_reference}`.

In general, the notation is:

1. `$cluster.[ports|environment_variables|constants].name`
2. `$local` (environment variable or constant)
3. `$cluster.host`

The name `db` here is a reference to a "mysql" breed that needs to exist during deployment. "db" is a custom name just in this scope. For instance if there is a need for 2 databases someone could use db1 & db2, or XX & XY etc.

### 'Hard' set a variable

You want to "hard set" an environment variable, just like doing an `export MY_VAR=some_value` in a shell. This  variable could be some external dependency you have no direct control over: the endpoint of some service you use that is out of your control. 

You may also want to define a placeholder for a variable of which you do not know the actual value yet, but should be filled in when this breed is used in a blueprint: the following deployment will not run without it and you want Vamp to check that dependency. This placeholder is designated with a `~` character.

```yaml
---
name: my_breed
deployable: repo/container:version

environment_variables:
    MY_ENV_VAR1: some_string_value  # hard set
    MY_ENV_VAR1: ~                  # placeholder
```

### Resolve a reference at deploy time

You might want to resolve a reference and set it as the value for an environment variable. This reference can either be dynamically resolved at deploy-time, like ports and hosts names we don't know till a deployment is done, or be a reference to a hardcoded and published constant from some other part of the blueprint or breed, typically a dependency.

You would use this to hook up services at runtime based on host/port combinations or to use a hard dependency that never changes but should be provided by another breed. 

>**Note:** use the `$` sign to reference other statements in a blueprint and you use the `constants` keyword
to create a list of constant values.

Have a look at this example blueprint. We are describing a frontend service that has a dependency on a backend service. We pick up the actual address of the backend service using references to variables in the blueprint that are filled in at runtime. However, we also want to pick up a value that is set by "us humans": the api path, in this case "/v2/api/customers".

```yaml
---
name: my_blueprint
clusters:

  frontend:
    breed:
      name: frontend_service
    environment_variables:
        # resolves to a host and port at runtime
        BACKEND_URL: http://$backend.host:$backend.ports.port
        # resolves to the "published" constant value
        BACKEND_URI_PATH: $backend.constants.uri_path        
      dependencies:
        backend: my_other_breed
  backend:
    breed:
      name: my_other_breed
    constants:
      uri_path: /v2/api/customers   
```

We could even extend this further. What if the backend is configured thru some environment variable, but the frontend also needs that information? Let's say the encoding type for some database.
We just reference that environment variable using the exact same syntax:

```yaml
---
name: my_blueprint
clusters:

  frontend:
    breed:
      name: frontend_service
    environment_variables:
        # resolves to a host and port at runtime
        BACKEND_URL: http://$backend.host:$backend.ports.port
        # resolves to the "published" constant value
        BACKEND_URI_PATH: $backend.constants.uri_path
        #resolves to the environment variable in the dependency
        BACKEND_ENCODING: $backend.environment_variables.ENCODING_TYPE        
      dependencies:
        backend: my_other_breed
  backend:
    breed:
      name: my_other_breed
    environment_variables:
      ENCODING_TYPE: 'UTF8'  
    constants:
      uri_path: /v2/api/customers
         
```


# Blueprints
Blueprints are execution plans - they describe how your services should be hooked up and what the topology should look like at runtime. This means you reference your breeds (or define them inline) and add runtime configuration to them.

Blueprints allow you to add the following extra properties:

- **endpoint:** a stable port where the service can be reached.
- **cluster:** a grouping of services with one purpose, i.e. two versions (a/b) of one service.
- **scale:** the CPU and memory and the amount of instance allocate to a service.
- **routing:** how much and which traffic the service should receive.
- **filter:** how traffic should be directed based on HTTP and/or TCP properties.
- **sla:** and SLA definition that controls autoscaling.


This example shows some of the key concepts of of blueprints:
 
```yaml
---
name: my_cool_blueprint # custom blueprint name
endpoints:
  my_frontend.port: 8080

clusters:
  my_frontend: # Custom cluster name.
    services: # List of services
      -
        breed:
          name: some_cool_breed
        # Scale for this service.
        scale:
          cpu: 2        # Number of CPUs per instance.
          memory: 2048  # Memory in MB per instance.
          instances: 2  # Number of instances.
        # Routing for this service.
        # This makes sense only with multiple services per cluster.
        routing:
          weight: 95
          filters:
            - condition: ua = android
      -
        # Another service in the same cluster.
        breed: some_other_breed
        scale: large # Notice we used a reference to a "scale". More on this later

    # SLA (reference) defined on cluster level. 
    sla: some_really_good_sla
```

## Endpoints

An endpoint is a "stable" port that almost never changes. It points to the "first" service in your blueprint. This service can of course be changed, but the endpoint port normally doesnt. Under the hood, we do a fairly simple port mapping.

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
   - breed: my_cool_service_A  	# reference to an existing breed
   -
     breed:						# shortened inline breed
       name: my_cool_service_B	
       deployable: some_container
       ...
```
Clusters and services are just organisational items. Vamp uses them to order, reference and control the actual containers and routing and traffic.

> **This all seems redundant, right?** We have a reference chain of blueprints -> endpoints -> clusters -> services -> breeds -> containers. However, you need this level of control and granularity in any serious environment where DRY principles are taken seriously and where "one size fits all" doesn't fly.


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