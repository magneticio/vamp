---
title: Environment variables 
weight: 41
menu:
  main:
    parent: using-vamp
    identifier: using-breeds-env-vars   
---

# Environment variables & dependencies

Breeds and blueprints can have a lists of environment variables that will be injected into the container at runtime.You set environment variables with the `environment_variables` keyword or its shorter version `env`, e.g. both examples below are equivalent.

```yaml
---
environment_variables:
  PORT: 8080
```

```yaml
---
env:
  PORT: 8080
```

Breeds can also have dependencies on other breeds. These dependencies should be stated explicitly, similar to how you would do in a Maven pom.xml, a Ruby Gemfile or similar package dependency systems, i.e:

```yaml
---
dependencies:
  cache: redis:1.1
``` 

In a lot of cases, dependencies coexist with interpolated environment variables or constants because exact values are not known untill deploy time.

## 'Hard' setting a variable

You want to "hard set" an environment variable, just like doing an `export MY_VAR=some_value` in a shell. This  variable could be some external dependency you have no direct control over: the endpoint of some service you use that is out of your control. It can also be some setting you want to tweak, like `JVM_HEAP_SIZE` or `AWS_REGION`.

```yaml
---
name: java_aws_app:1.2.1
deployable: acmecorp/tomcat:1.2.1

environment_variables:
    JVM_HEAP_SIZE: 1200
    AWS_REGION: 'eu-west-1'     
```

## Using place holders

You may also want to define a place holder for a variable of which you do not know the actual value yet, but it should be filled in at runtime, i.e. when this breed actually gets deployed. This place holder is designated with a `~` character.

In the below example we designated the `ORACLE_PASSWORD` as a place holder. We require this variable to provided later.

```yaml
---
name: java_aws_app:1.2.1
deployable: acmecorp/tomcat:1.2.1
environment_variables:
    JVM_HEAP_SIZE: 1200
    AWS_REGION: 'eu-west-1' 
    ORACLE_PASSWORD: ~    
```

> **Note**: A typical use case for this would be when different roles in a company work on the same project. Developers can create place holders for variables that operations should fill in: it helps with separating responsibilities.

## Resolving variables

Using the `$` character, you can reference other statements in a breed/blueprint. This allows you to dynamically resolve ports and hosts names we don't know till a deployment is done. You can also resolve to hard coded and published constants from some other part of the blueprint or breed, typically a dependency, i.e.:

```yaml
---
    breed:
      name: frontend_app:1.0
      deployable: acmecorp/tomcat:1.2.1      
    environment_variables:
        MYSQL_HOST: $backend.host        # resolves to a host at runtime
        MYSQL_PORT: $backend.ports.port  # resolves to a port at runtime
      dependencies:
        backend: mysql:1.0
  backend:
    breed:
      name: mysql:1.0
```

Vamp provides just one *magic** variable: the `host`. This resolves to the host or ip address of the referenced service. Strictly speaking the `host` reference resolves to the router endpoint, but users do need to concern themselves with this. Users can think of *one-on-one* connections where Vamp actually does server-side service discovery to decouple services.

> **Note**: The `$` value is escaped by `$$`. A more strict notation is `${some_reference}`

We could even extend this further. What if the backend is configured through some environment variable, but the frontend also needs that information? Let's say the encoding type for our database. We just reference that environment variable using the exact same syntax:

```yaml
---
    breed:
      name: frontend_app:1.0
      deployable: acmecorp/tomcat:1.2.1      
    environment_variables:
        MYSQL_HOST: $backend.host       
        MYSQL_PORT: $backend.ports.port 
        BACKEND_ENCODING: $backend.environment_variables.ENCODING_TYPE
      dependencies:
        backend: mysql:1.0
  backend:
    breed:
      name: mysql:1.0
    environment_variables:
      ENCODING_TYPE: 'UTF8'   # injected into the backend MySQL container
```

You can do everything with `environment_variables` but `constants` allow you to just be a little bit cleaner with regard to what you want to expose and what not.

## Environment variable scope

Environment variables can live on different scopes and can be overridden by scopes higher in the scope hierarchy.
A scope is an area of your breed or blueprint definition that limits the visibility of variables and references inside that scope.

1. **Breed scope**: The scope we used in all the above examples is the default scope. If you never define any `environment_variables` in any other place, this will be used.

2. **Service scope**: This scope can override breed scope and is part of the blueprint artefact. Use this to override environment variables in a breed when using references to breeds.

3. **Cluster scope**: This scope can override the service scope and breed scope and is part of the blueprint artefact. Use this to override all environment variables in all services that belong to a cluster.

> **Note:** Using scopes effectively is completely up to your use case. The various scopes help to separate
concerns when multiple people and/or teams work on Vamp artefacts and deployments and need to decouple their effor


Let's look at some examples:

### Example 1: Running two the same services with a different configuration

As a dev/ops-er you want to test one service configured in two different ways at the same time. Your service is configurable using environment variables. In this case we are testing a connection pool setting. The blueprint would look like:

```yaml
---
name: production_deployment:1.0
endpoints:
  frontend.port: 9050/http
clusters:
  frontend:
    services:
      -
        breed:
          name: frontend_app:1.0-a
          deployable: acmecorp/tomcat:1.2.1
          ports:
            port: 8080/http
          environment_variables:           
            CONNECTION_POOL: 30
        routing:
          weight: 50                 
      -
        breed:
          name: frontend_app:1.0-b              # different breed name, same deployable
          deployable: acmecorp/tomcat:1.2.1
          ports:
            port: 8080/http
          environment_variables:           
            CONNECTION_POOL: 60                 # different pool size
        routing:
          weight: 50               
```  

> **Note:** we just use the breed level environment variables. We also split the traffic into a 50/50 divide between both services.

### Example 2: Overriding the JVM_HEAP_SIZE in production

As a developer, you created your service with a default heap size you use on your development laptop and maybe on a test environment. Once your service goes "live", an ops guy/gall should be able to override this setting.

```yaml
---
name: production_deployment:1.0
endpoints:
  frontend.port: 9050/http
environment_variables:                  # cluster level variable 
  frontend.JVM_HEAP_SIZE: 2800          # overrides the breed level
clusters:
  frontend:
    services:
      -
        breed:
          name: frontend_app:1.0
          deployable: acmecorp/tomcat:1.2.1
          ports:
            port: 8080/http
          environment_variables:           
            JVM_HEAP_SIZE: 1200         # will be overridden by deployment level: 2800
```            

> **Note:** we override the variable `JVM_HEAP_SIZE` for the whole `frontend` cluster by specifically marking it with .dot-notation `cluster.variable` 

### Example 3: Using a place holder

As a developer, you might not know some value your service needs at runtime, say the Google Anaytics ID your company uses. However, your Node.js frontend needs it! You can make this explicit by demanding that a variable is to be set by a higher scope by using the `~` place holder. When this variable is NOT provided, Vamp will report an error at deploy time.

```yaml
---
name: production_deployment:1.0
endpoints:
  frontend.port: 9050/http
environment_variables:                            # cluster level variable 
  frontend.GOOGLE_ANALYTICS_KEY: 'UA-53758816-1'  # overrides the breed level
clusters:
  frontend:
    services:
      -
        breed:
          name: frontend_app:1.0
          deployable: acmecorp/node_express:1.0
          ports:
            port: 8080/http
          environment_variables:           
            GOOGLE_ANALYTICS_KEY: ~               # If not provided at higher scope, Vamp reports error
``` 

### Example 4: Combining all scopes and references

As a final example, let's combine some of the examples above and include referenced breeds. In this case, a we have two breed artefacts already stored in Vamp and include them by using the `ref` keyword.

We override all `JVM_HEAP_SIZE` variables at the top scope. However, we just want to tweak the `JVM_HEAP_SIZE` for service `frontend_app:1.0-b`. We do this by adding a `environment_variables` at the service level.

```yaml
---
name: production_deployment:1.0
endpoints:
  frontend.port: 9050/http
environment_variables:                            # cluster level variable 
  frontend.JVM_HEAP_SIZE: 2400                    # overrides the breed level
clusters:
  frontend:
    services:
      - breed:
          ref: frontend_app:1.0-a
        routing:
          weight: 50  
      - breed:
          ref: frontend_app:1.0-b
        routing:
          weight: 50          
        environment_variables:           
          JVM_HEAP_SIZE: 1800               # overrides the breed level AND cluster level
``` 



## Constants

Sometimes you just want configuration information to be available in a breed or blueprint. You don't need that information to be directly exposed as an environment variable. As a convenience, Vamp allows you to set `constants`.
These are values that cannot be changed during deploy time.

```yaml
---
breed:
  name: frontend_app:1.0
environment_variables:
    MYSQL_HOST: $backend.host       
    MYSQL_PORT: $backend.ports.port 
    BACKEND_ENCODING: $backend.environment_variables.ENCODING_TYPE
    SCHEMA: $backend.constants.SCHEMA_NAME
  dependencies:
    backend: mysql:1.0
backend:
breed:
  name: mysql:1.0
environment_variables:
  ENCODING_TYPE: 'UTF8'
constants:
  SCHEMA_NAME: 'customers'    # NOT injected into the backend MySQL container
```