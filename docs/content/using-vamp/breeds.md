---
title: Breeds
weight: 20
menu:
  main:
    parent: using-vamp
    identifier: using-breeds    
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

Ports come in two flavor

- `/tcp` this is the default type if none is specified. Use it for things like Redis, MySQL etc.

- `/http` HTTP ports are always recommended when dealing with HTTP-based services. Vamp can record a lot of 
interesting metrics like response times, errors etc. Of course, using `/tcp` will work but you miss out on cool data.

> **Tip:** Use the `/http` notation for ports whenever possible!

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
    MY_ENV_VAR2: ~                  # placeholder
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