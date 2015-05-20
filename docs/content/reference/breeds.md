---
title: Breeds
weight: 20
menu:
  main:
    parent: reference
---
# Breeds

Breeds are applications and services available for deployment. Each breed is described by the DSL in YAML notation or JSON, whatever you like. This description includes name, version, available parameters, dependencies etc.
To a certain degree, you could compare a breed to a Maven artefact.

Let's start with a some examples. First a simple one, with no dependencies. Please read the inline comments carefully.

```yaml
---
name: monarch                         # Unique designator (name).
deployable: magneticio/monarch:latest # Deployable, by default a Docker image.
                                      
# Map of available ports.
ports:
  web: 8080/http    

  # Notice the custom port name "web"
  # Number together with type http or tcp. 
  # If not specified, tcp is the default.

# Map of environment variables.
environment_variables:
  password[DB_HOST]: ~  

  # An alias is defined between brackets.
  # This alias is used when it's passed to a deployable  
  # (e.g. Docker).
  # Aliases are optional.
  # ~ means no value (null, as per YAML spec).

  MY_VAR: "any"         

  # This is just basic key/value pair. 
  # It gets passed into a container's environment as is.

# Map of constants.
constants:
  username: SA          

  # Constants are traits which may be required
  # by other breeds but are not configurable.
  # In other words, a constant may be used
  # inside the container as a hard-coded value 
  # and it's exposed to any other breed as is.
  # This will come in handy when we start composing breeds in blueprints.

```

Now, let's look at a breed with dependencies:

```yaml
---
name: monarch
deployable: magneticio/monarch:latest

ports:
  port: 8080/http

environment_variables:
  DB_HOST: $db.host 

  # Any value starting with $ will be interpolated  
  # to a specific value at runtime. 
  # In general, the notation is:
  # 1) $cluster.[ports|environment_variables|constants].name
  # 2) $local (environment variable or constant)
  # 3) $cluster.host
  # In this example "host" is provided by the system and 
  # is not specified by a user.
  # $ value is escaped by $$
  # More strict notation is ${some_reference}

  DB_PORT: $db.ports.port 

  # References to the "db" dependency 
  # and its port with the name "port". 
  # The variable will get the provided value automatically 
  # at runtime either by Vamp or the user.

  URL: jdbc://$db.host:$db.ports.port/schema/foo
    
  # Example of a value that will be interpolated before deployment.
      
dependencies:

  db: mysql 

  # This is shortened for db: name: mysql. 
  # Here we could have an inline dependency definition. 
  # "mysql" needs to exist during deployment. 
  # "db" is a custom name just in this scope. 
  # For instance if there is a need for 2 databases 
  # someone could use db1 & db2, or XX & XY etc.
```

## Environment variables

The above examples should give you an example of what is possible using breeds. Here are some more use cases for the way Vamp handles environment variables and interpolation of those variables

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

**Notice**: you use the `$` sign to reference other statements in a blueprint and you use the `constants` keyword
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