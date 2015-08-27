---
title: 4. Merging and deleting services
type: documentation
slug: /getting-started-tutorial/4-merge-delete/
---

# 4. Merging a changed topology

In the [previous part](/documentation/guides/getting-started-tutorial/3-splitting-services/) we "over-engineered" our
service based solution a bit: on purpose of course. We don't really need two backends services.
So in this part we will introduce our newly engineered solution and transition to it using Vamp's
blueprints and canary releasing methods.

## Step 1: Some background and theory

What we are going to do is create a new blueprint that is completely valid by itself and merge it
with the already running deployment. This might sound strange at first, but it makes sense. Why? Because
this will enable us to slowly move from the previous solution to the next solution. Once moved over, we can
remove parts we no longer need, i.e. the former "over-engineered" topology.

![](http://vamp.io/img/services_atob.svg)

In the diagram above, this is visualized as follows:

1. We have a running deployment (the blue circle with the "1"). To this we introduce a new blueprint
which is merged with the running deployment (the pink circle with the "2").
2. At a point, both are active as we are transitioning from blue to pink.
3. Once we are fully on pink, we actively remove/decomission the blue part.

Is this the same as a blue/green release? Yes, but we like pink better ;o)

## Step 2: Prepping our blueprint

The following blueprint describes our more reasonable service topology. Again, this blueprint is completely
valid by itself. You could just deploy it somewhere separately and not merge it with our over-engineered
topology. Notice the following:

- The blueprint only has one backend cluster with one service.
- The blueprint does not specify an endpoint using the `endpoints` key because we are going to use the endpoint already present and configured in the running deployment.

{{% copyable %}}
```yaml
---
name: sava:1.3
clusters:
  sava:
    services:
      breed:
        name: sava-frontend:1.3.0
        deployable: magneticio/sava-frontend:1.3.0
        ports:
          port: 8080/http
        environment_variables:
          BACKEND: http://$backend.host:$backend.ports.port/api/message
        dependencies:
          backend: sava-backend:1.3.0
      scale:
        cpu: 0.2
        memory: 256
        instances: 1
  backend:
    services:
      breed:
        name: sava-backend:1.3.0
        deployable: magneticio/sava-backend:1.3.0
        ports:
          port: 8080/http
      scale:
        cpu: 0.2
        memory: 256
        instances: 1
```
{{% /copyable %}}

Updating our deployment using the UI or a `PUT` to the `/api/v1/deployments/:deployment_id` should yield a deployment with the following properties (we left some irrelevant
parts out):

1. Two `services` in the sava `cluster`: the old one at 100% and the new one at 0% weight.
2. Three backends in the `cluster` list: two old ones and one new one.


So what happened here? Vamp has worked out what parts were already there and what parts should be merged or added. This is done based on naming, i.e. the sava cluster already existed, so Vamp added a service to it at 0% weight. A cluster named "backend" didn't exist yet, so it was created. Effectively, we have merged
the running deployment with a new blueprint.

## Step 3: Transitioning from blueprints to deployments and back

Moving from the old to the new topology is now just a question of "turning the weight dial". You
could do this in one go, or slowly adjust it. The easiest and neatest way is to just update the deployment as you go.

Vamp's API has a convenient option for this: you can export any deployment as a blueprint! By appending `?as_blueprint=true` to any deployment URI, Vamp strips all runtime info and outputs a perfectly valid blueprint of that specific deployment.

The default output will be in JSON format, but you can also get a YAML format. Just set the header `Accept: application/x-yaml` and Vamp will give you a YAML format blueprint of that deployment.

> **Note**: When using the graphical UI, this is all taken care of.




In this specific example, we could export the deployment as a blueprint and update the weight to a 50% to
50% split. Then we could do this again, but with a 80% to 20% split and so on. See the abbreviated example
below where we set the `weight` keys to `50` in both `routing` sections.

```yaml
---
name: eb2d505e-f5cf-4aed-b4ae-326a8ca54577
endpoints:
  sava.port: '9060/http'
clusters:
  sava:
    services:
    - breed:
        name: sava-frontend:1.3.0
        deployable: magneticio/sava-frontend:1.3.0
        ports:
          port: 8080/http
        environment_variables:
          BACKEND: http://$backend.host:$backend.ports.port/api/message
        dependencies:
          backend:
            name: sava-backend:1.3.0
      scale:
        cpu: 0.2
        memory: 256.0
        instances: 1
      routing:
        weight: 50
        filters: []
    - breed:
        name: sava-frontend:1.2.0
        deployable: magneticio/sava-frontend:1.2.0
        ports:
          port: 8080/http
        environment_variables:
          BACKEND_1: http://$backend1.host:$backend1.ports.port/api/message
          BACKEND_2: http://$backend2.host:$backend2.ports.port/api/message
        dependencies:
          backend1:
            name: sava-backend1:1.2.0
          backend2:
            name: sava-backend2:1.2.0
      scale:
        cpu: 0.2
        memory: 256.0
        instances: 1
      routing:
        weight: 50
        filters: []
environment_variables:
  sava.BACKEND_1: http://$backend1.host:$backend1.ports.port/api/message
  sava.BACKEND_2: http://$backend2.host:$backend2.ports.port/api/message
  sava.backend: http://$backend.host:$backend.ports.port/api/message
```

## Step 4: Deleting parts of the deployment

Vamp helps you transition between states and avoid "hard" switches, so deleting parts of a deployment is somewhat different than you might expect.

In essence, a delete is just another update of the deployment: you specify what you want to remove using a blueprint and send it to the deployment's URI using the `DELETE`HTTP verb: yes, it is HTTP Delete with a body, not just a URI and some id.

This means you can specifically target parts of your deployment to be removed instead of deleting the whole thing. For this tutorial we are going to delete the "over-engineered" old part of our deployment.

Currently, deleting works in two steps:
- Set all routings to `weight: 0` of the services you want to delete with a simple update.
- Execute the delete.


> **Note**: You need to explicitly set the routing weight of the service you want to deploy to zero before deleting. Here is why: When you have, for example, four active services divided in a 25/25/20/30 split and you delete the one with 30%, Vamp doesn't know how you want to redistribute the "left over" 30% of traffic. For this reason the user should first explicitly divide this and then perform the delete.

**Setting to zero**

When you grab the YAML version of the deployment, just like above, you can set all the `weight` entries for the Sava 1.2.0 versions to `0` and update the deployment as usual. See the cleaned up example and make sure to adjust the name to your specific situation.

{{% copyable %}}
```yaml
---
name: 125fd95c-a756-4635-8e1a-361085037870
clusters:
  backend1:
    services:
    - breed:
        ref: sava-backend1:1.2.0
      routing:
        weight: 0
  backend2:
    services:
    - breed:
        ref: sava-backend2:1.2.0
      routing:
        weight: 0
  sava:
    services:
    - breed:
        ref: sava-frontend:1.3.0
      routing:
        weight: 100
    - breed:
        ref: sava-frontend:1.2.0
      routing:
        weight: 0
```
{{% /copyable %}}


**Doing the delete**

Now, you can take the exact same YAML blueprint or use one that's a bit cleaned up for clarity and send it in the body of the `DELETE` to the deployment resource, e.g. `/api/v1/deployments/125fd95c-a756-4635-8e1a-361085037870`.

> **Note:** The UI does not have DELETE function yet for parts of deployments, just for full deployments. This will be added later.

{{% copyable %}}
```yaml
---
name: sava:1.2
clusters:
  sava:
    services:
      breed:
        ref: sava-frontend:1.2.0
  backend1:
    services:
      breed:
        ref: sava-backend1:1.2.0
  backend2:
    services:
      breed:
        ref: sava-backend2:1.2.0
```
{{% /copyable %}}

> **Note**: We removed the `deployable`, `environment_variables`, `ports` and some other parts of the blueprint. These are actually not necessary for updating or deletion. Besides that, this is actually exactly the same blueprint we used to initially deploy the "old" topology.

You can check the result in the UI: you should be left with just one backend and one frontend:

![](/img/screenshots/tut4_after_delete.png)

## Step 5: When would I use this?

Sounds cool, but when would I use this in practice? Well, basically anytime you release something new!
For example a bugfix release for a mobile API that "didn't change anything significantly"? You could test
this separately and describe it in its own blueprint. After testing, you would merge that exact same blueprint
with your already running production version (the one without the bugfix) and slowly move over to new version.

New major release of your customer facing app? You probably also have some new dependencies that come with that
release. You create some containers and write up a blueprint that describes this new situation, run it in acceptance and test and what have you. Later, you merge it into your production setup, effectively putting it next to it and then slowly move from the old situation to the new situation, including dependencies.

This is the end of this initial getting started tutorial. We haven't done anything with Vamp's SLA's yet, scaling or dictionary system, so there is much more to come!
