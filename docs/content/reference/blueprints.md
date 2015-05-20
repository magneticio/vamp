---
title: Blueprints
weight: 30
menu:
  main:
    parent: reference
---
# Blueprints
Blueprints are execution plans - they describe how your services should be hooked up and what the topology should look like at the runtime. All dependencies and parameter values will be resolved at deployment time.

This example shows some of the key concepts of of blueprints:
 
```yaml
---
name: nomadic-frostbite

# Endpoints are stable ports mapped to concrete breed ports.
# Breed ports are resolved to port values available during runtime.
 
endpoints:
  notorious.port: 8080

# A blueprint defines collection of clusters.
# A cluster is a group of different services which 
# will appear as a single service. Common use cases 
# are service A and B in A/B testing - usually just different 
# versions of the same service (e.g. canary release).

clusters:
  notorious: # Custom cluster name.
    services: # List of service, at least a breed reference.
      -
        breed:
          name: nocturnal-viper
        # Scale for this service.
        scale:
          cpu: 2        # Number of CPUs per instance.
          memory: 2048  # Memory in MB per instance.
          instances: 2  # Number of instances.
        # Routing for this service.
        # This Makes sense only with multiple services per cluster.
        routing:
          weight: 95
          filters:
            - condition: ua = android
      -
        # Another service (breed) in the same cluster.
        breed: remote-venus
        scale: large # Notice we used a reference to a "scale". More on this later

    # SLA (reference) defined on cluster level. 
    sla: strong-mountain 
              
# List of parameters.
# Actual values will be resolved at the runtime.
# "thorium" in this example is just a key to get a 
# concrete parameter value.
# By default dictionary will resolve value as being the 
# same as key provided. 
environment_variables:
  notorious.aspect: thorium
```

## Scale

Scale is the "size" of a deployed service. Usually that means the number of instances (servers) and allocated cpu and memory. Scales can be defined inline in a blueprint or they can defined separately and given a unique name. The following example is a scale named "small". `POST`-ing this scale to the `/scales` REST API endpoint will store it under that name so it can be referenced from other blueprints.

```yaml
---
name: small   # Custom name.

cpu: 2        # Number of CPUs per instance.
memory: 2048  # Memory in MB per instance.
instances: 2  # Number of instances.
```