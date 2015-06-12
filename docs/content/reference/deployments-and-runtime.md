---
title: Deployments
weight: 50
menu:
  main:
    parent: reference
---

# Deployments

A deployment is a "running" blueprint. Over time, new blueprints can be merged with existing deployments or parts of the running blueprint can be removed from it. Each deployment can be exported as a blueprint and 
copy & pasted to another environment or even to the same environment to function as a clone.

Creating a deployment is done by sending a POST request to the `/deployments` endpoint.
Here is an example of a blueprint:

```yaml
---
name: my_monarch_blueprint

endpoints:
  crown.ports.port: 80

clusters:
  a_bunch_of_monarchs:
    # This blueprint has a reference to breed "crown" 
    # This breed needs to exist in your collection of breeds at deployment time otherwise
    # an error (4xx) will be reported back.
    breed: crown
    # Scale and routing are not specified: a default
    # environment configuration will be used.
```

The name of the deployment is automatically assigned as a UUID (e.g. `123e4567-e89b-12d3-a456-426655440000`).
Here is an example with two versions of the same service within the one cluster. 
{{% alert info %}}
Notice that we have distributed the weight between the two services.
{{% /alert%}}

Example with multiple service version (within the same cluster):

```yaml
---
name: my_monarch_blueprint

endpoints:
  monarch.ports.port: 80/http

clusters:
  a_bunch_of_monarchs:
    services:
      -
        breed: crown1
        routing:
          weight: 80        # 80% of traffic is handled by this service.
      -
        breed: crown2
        routing:                    
          weight: 20        # 20% of traffic handled by this service.
```

## Canary Releases & A/B Testing

A common scenario is to introduce a new version of the service to an existing cluster, this is what we call a **merge**. After testing/migration is done, the old or new version can be removed from the cluster, simply called a **removal**. Let's look at each in turn.

### Merge

Merging of new services is performed as a deployment update. Using the REST API `PUT` request together with the new blueprint as a request body will trigger the merge.

If a service already exists then only the routing and scale will be updated. Otherwise a new service will be added. If a new cluster doesn't exist in the deployment, it will be added.

Let's deploy a simple service:

```yaml
---
name: monarch_1.0

clusters:
  monarch:
    # Specifying only a reference to the breed.
    breed: monarch_1.0   
```

After this point we may have another version ready for deployment and now instead of only one service, we have added another one:

```yaml
---
name: monarch_1.1

environment_variables:
  # Some variable needed for our new recommendation engine,
  # just as an example.
  recommendation.route: "/api/v1"

clusters:

  monarch:
    # Just a reference and this breed has one dependency:
    # recommendation_1.0
    breed: monarch_1.1

  recommendation:
    breed: recommendation_1.0
 
```

Now our deployment (in simplified blueprint format) looks like this:

```yaml
---
name: monarch_1.0

environment_variables:
  recommendation.route: "/api/v1"

clusters:

  monarch:
    services:
      - 
        breed: monarch_1.0
        routing: 
          weight: 100
      - 
        breed: monarch_1.1
        routing: 
          weight: 0

  recommendation:
    services:
      - 
        breed: recommendation_1.0
        routing: 
          weight: 100

```

Note that the route weight for monarch_1.1 is 0, i.e. no traffic is sent to it.
Let's redirect some traffic to our new monarch_1.1 (e.g. 10%):

```yaml
---
clusters:
  monarch:
    services:
      - 
        breed: monarch_1.0
        routing: 
          weight: 90
      - 
        breed: monarch_1.1
        routing: 
          weight: 10
```

Note that we can omit other fields like name, parameters and even other clusters (e.g. recommendation) if the change is not relevant to them. In this example we just wanted to update the weights.

In the last few examples we have shown the following:

* A fresh new deployment.
* A canary release with a cluster update and change of the topology (a new cluster was added).
* An update of the routings for a cluster - similar to a cluster scale update (instances, cpu, memory).

### Removal

Removal is done using the REST API `DELETE` request together with the new blueprint as request body.
If a service exists it will be removed, otherwise the request is ignored. If a cluster has no more services left the cluster will be removed completely. Lastly, if a deployment has no more clusters it will be completely removed (destroyed).

Let's use the example from the previous section. Notice the weight is evenly distributed (50/50). 

```yaml
---
name: monarch_1.0

environment_variables:
  recommendation.route: "/api/v1"

clusters:

  monarch:
    services:
      - 
        breed: monarch_1.0
        routing: 
          weight: 50
      - 
        breed: monarch_1.1
        routing: 
          weight: 50

  recommendation:
    services:
      - 
        breed: recommendation_1.0
        routing: 
          weight: 100
```

If we are happy with the new monarch version 1.1, we can proceed with the removal of the old version.
This change is applied on the running deployment. We send the following YAML as the body of the `DELETE` request
to the `/deployments/<deployment_UUID>` endpoint.

```yaml
---
name: monarch_1.0

clusters:
  monarch:
    breed: monarch_1.0

```

Note that this is the same original blueprint we started with. What we are doing here is basically "subtracting" one blueprint from the other, although "the other" is a running deployment.
After this operation our deployment is:

```yaml
---
name: monarch_1.0

environment_variables:
  recommendation.route: "/api/v1"

clusters:

  monarch:
    services:
      breed: monarch_1.1
        routing: 
          weight: 100

  recommendation:
    services:
        breed: recommendation_1.0
        routing: 
          weight: 100

```

In a nutshell: If we say that the first version was A and the second B, then we just did the migration from A to B without downtime:
* **A** -> A + B -> A + B - A -> **B**

We could also remove the newer version (monarch_1.1 with/without recommendation cluster) in case that it didn't perform as we expected.