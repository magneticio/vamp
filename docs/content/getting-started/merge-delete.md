---
title: 4. Merging and deleting services
type: documentation
weight: 50
menu:
    main:
      parent: getting-started
    
---

# 4. Merging a changed topology

In the [previous part](/documentation/getting-started/splitting-services/) we "over-engineered" our
service based solution a bit: on purpose of course. We don't really need two backends services.
So in this part we will introduce our newly engineered solution and transition to it using Vamp's
blueprints and canary releasing methods.

## Step 1: Some background and theory

What we are going to do is create a new blueprint that is completely valid by itself and merge it
with the already running deployment. This might sound strange at first, but it makes sense. Why? Because
this will enable us to slowly move from the previous solution to the next solution. Once moved over, we can
remove parts we no longer need, i.e. the former "over-engineered" topology.

![](/img/services_atob.svg)

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
- The blueprint does not specify and endpoint using the `endpoints` key because we are going to use the endpoint already present and configured in the running deployment.

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
          port: 80/http        
        environment_variables:
          # using alias feature, instead of only "BACKEND: http://..."
          backend[BACKEND]: http://$backend.host:$backend.ports.port/api/message

        dependencies:
          backend: sava-backend:1.3.0
      scale:
        cpu: 0.5       
        memory: 512  
        instances: 1            

  backend:
    services:
      breed:
        name: sava-backend:1.3.0
        deployable: magneticio/sava-backend:1.3.0         
        ports:
          port: 80/http
      scale:
        cpu: 0.5       
        memory: 512  
        instances: 1            
```
{{% /copyable %}}

A `PUT` to our deployment (e.g. `/api/v1/deployments/1abf809e-dbbd-42a6-87a2-e25ddede67cb`) that was based on [the blueprint from the previous part of the tutorial](/documentation/getting-started/splitting-services/) should yield a deployment with the following properties (we left some irrelevant
parts out):

1. Two `services` in the sava `cluster`: the old one at 100% and the new one at 0% weight.
2. Three backends in the `cluster` list: two old ones and one new one.

```json
{
    "name": "1abf809e-dbbd-42a6-87a2-e25ddede67cb",
    "endpoints": {
        "sava.port": "9060"
    },
    "clusters": {
        "backend1": {
            "services": [
                {
                    "state": {
                        "name": "Deployed",
                        "started_at": "2015-04-23T09:57:20.794Z"
                    },
                    "breed": {
                        "name": "sava-backend1:1.2.0",
                        "deployable": "magneticio/sava-backend1:1.2.0",
                        "ports": {
                            "port": "80/http"
                        },
                        "environment_variables": {},
                        "constants": {},
                        "dependencies": {}
                    },
                    "scale": {
                        "cpu": 0.5,
                        "memory": 512,
                        "instances": 1
                    },
                    "routing": {
                        "weight": 100,
                        "filters": []
                    },
                    "servers": [
                        {
                            "name": "1abf809e-dbbd-42a6-87a2-e25ddede67cb_2c8fb1128ab9a09fda8f.18c590cc-e99f-11e4-b3c2-56847afe9799",
                            "host": "10.26.184.254",
                            "ports": {
                                "80": 31983
                            },
                            "deployed": true
                        }
                    ],
                    "dependencies": {}
                }
            ],
            "routes": {
                "80": 33019
            }
        },
        "backend2": {
            "services": [
                {
                    "state": {
                        "name": "Deployed",
                        "started_at": "2015-04-23T09:57:20.794Z"
                    },
                    "breed": {
                        "name": "sava-backend2:1.2.0",
                        "deployable": "magneticio/sava-backend2:1.2.0",
                        "ports": {
                            "port": "80/http"
                        },
                        "environment_variables": {},
                        "constants": {},
                        "dependencies": {}
                    },
                    "scale": {
                        "cpu": 0.5,
                        "memory": 512,
                        "instances": 1
                    },
                    "routing": {
                        "weight": 100,
                        "filters": []
                    },
                    "servers": [
                        {
                            "name": "1abf809e-dbbd-42a6-87a2-e25ddede67cb_43f71c7977ba711c70f4.19615b9d-e99f-11e4-b3c2-56847afe9799",
                            "host": "10.16.107.232",
                            "ports": {
                                "80": 31000
                            },
                            "deployed": true
                        }
                    ],
                    "dependencies": {}
                }
            ],
            "routes": {
                "80": 33020
            }
        },
        "sava": {
            "services": [
                {
                    "state": {
                        "name": "Deployed",
                        "started_at": "2015-04-23T09:57:43.282Z"
                    },
                    "breed": {
                        "name": "sava-frontend:1.2.0",
                        "deployable": "magneticio/sava-frontend:1.2.0",
                        "ports": {
                            "port": "80/http"
                        },
                        "environment_variables": {
                            "BACKEND_1": "http://$backend1.host:$backend1.ports.port/api/message",
                            "BACKEND_2": "http://$backend2.host:$backend2.ports.port/api/message"
                        },
                        "constants": {},
                        "dependencies": {
                            "backend1": {
                                "name": "sava-backend1:1.2.0"
                            },
                            "backend2": {
                                "name": "sava-backend2:1.2.0"
                            }
                        }
                    },
                    "scale": {
                        "cpu": 0.5,
                        "memory": 512,
                        "instances": 1
                    },
                    "routing": {
                        "weight": 100,
                        "filters": []
                    },
                    "servers": [
                        {
                            "name": "1abf809e-dbbd-42a6-87a2-e25ddede67cb_ae785ad2bf8eace4bbb7.24b375ae-e99f-11e4-b3c2-56847afe9799",
                            "host": "10.16.107.232",
                            "ports": {
                                "80": 31001
                            },
                            "deployed": true
                        }
                    ],
                    "dependencies": {
                        "backend1": "backend1",
                        "backend2": "backend2"
                    }
                },
                {
                    "state": {
                        "name": "Deployed",
                        "started_at": "2015-04-23T10:10:56.714Z"
                    },
                    "breed": {
                        "name": "sava-frontend:1.3.0",
                        "deployable": "magneticio/sava-frontend:1.3.0",
                        "ports": {
                            "port": "80/http"
                        },
                        "environment_variables": {
                            "backend[BACKEND]": "http://$backend.host:$backend.ports.port/api/message"
                        },
                        "constants": {},
                        "dependencies": {
                            "backend": {
                                "name": "sava-backend:1.3.0"
                            }
                        }
                    },
                    "scale": {
                        "cpu": 0.5,
                        "memory": 512,
                        "instances": 1
                    },
                    "routing": {
                        "weight": 0,
                        "filters": []
                    },
                    "servers": [
                        {
                            "name": "1abf809e-dbbd-42a6-87a2-e25ddede67cb_ba4b1d252493eea2c068.3ee68070-e9a0-11e4-b3c2-56847afe9799",
                            "host": "10.155.196.225",
                            "ports": {
                                "80": 31000
                            },
                            "deployed": true
                        }
                    ],
                    "dependencies": {
                        "backend": "backend"
                    }
                }
            ],
            "routes": {
                "80": 33018
            }
        },
        "backend": {
            "services": [
                {
                    "state": {
                        "name": "Deployed",
                        "started_at": "2015-04-23T10:10:56.714Z"
                    },
                    "breed": {
                        "name": "sava-backend:1.3.0",
                        "deployable": "magneticio/sava-backend:1.3.0",
                        "ports": {
                            "port": "80/http"
                        },
                        "environment_variables": {},
                        "constants": {},
                        "dependencies": {}
                    },
                    "scale": {
                        "cpu": 0.5,
                        "memory": 512,
                        "instances": 1
                    },
                    "routing": {
                        "weight": 100,
                        "filters": []
                    },
                    "servers": [
                        {
                            "name": "1abf809e-dbbd-42a6-87a2-e25ddede67cb_134f5e46292fb314ae10.32f73bff-e9a0-11e4-b3c2-56847afe9799",
                            "host": "10.16.107.232",
                            "ports": {
                                "80": 31002
                            },
                            "deployed": true
                        }
                    ],
                    "dependencies": {}
                }
            ],
            "routes": {
                "80": 33022
            }
        }
    },
    "ports": {
        "sava.port": "33018",
        "backend.port": "33022"
    },
    "environment_variables": {
        "sava.BACKEND": "http://$backend.host:$backend.ports.port/api/message"
    },
    "constants": {},
    "hosts": {
        "sava": "10.26.184.254",
        "backend": "10.26.184.254"
    }
}
```

So what happened here? Vamp has worked out what parts were already there and what parts should be merged or added. This is done based on naming, i.e. the sava cluster already existed, so Vamp added a service to it at 0% weight. A cluster named "backend" didn't exist yet, so it was created. Effectively, we have merged
the running deployment with a new blueprint.

## Step 3: Transitioning from blueprints to deployments and back

Moving from the old to the new topology is now just a question of "turning the weight dial". You 
could do this in one go, or slowly adjust it. The easiest and neatest way is to just update the blueprint
as you go and `PUT` it to the deployment. 

Vamp has a convenient option for this: you can export any deployment as a blueprint! It will be in JSON format but is functionally 100% equivalent to the YAML version. By appending `?as_blueprint=true` to any deployment URI, Vamp strips all runtime info and output a perfectly valid
blueprint of that specific deployment. You can then use that to update any values as you see fit and re-`PUT` it again for changes to take effect. 

![](/img/screencap_asblueprint.gif)

{{% alert info %}}
**Tip**: By appending `?as_blueprint=true` to any deployment URI, Vamp spits out a perfectly valid
blueprint of that specific deployment. This way you can clone whole deployments in seconds. Pretty awesome.  
{{% /alert %}}

In this specific example, we could export the deployment as a blueprint and update the weight to a 95% to 
5% split. Then we could do this again, but with a 80% to 20% split and so on. See the abbreviated example
below:

```json
 ... 
 "sava": {
      "services": [
        {
          "breed": {
            "name": "sava-frontend:1.2.0",
            "deployable": "magneticio/sava-frontend:1.2.0"
          },
          "routing": {
            "weight": 95
          }
        },
        {
          "breed": {
            "name": "sava-frontend:1.3.0",
            "deployable": "magneticio/sava-frontend:1.3.0"
          },
          "routing": {
            "weight": 5
          }
        }
      ]
    }
...
```

## Step 4: Deleting parts of the deployment

Vamp helps you transition between states and avoid "hard" switches, so deleting parts of a deployment is somewhat different than you might expect. In essence, a delete is just another update of the deployment: you specify what you want to remove using a blueprint and send it to the deployment's URI using the `DELETE`HTTP verb: yes, it is HTTP Delete with a body, not just a URI and some id.

This means you can specifically target parts of your deployment to be removed instead of deleting the whole thing. For this tutorial we are going to delete the "over-engineered" old part of our deployment by grabbing the "old" blueprint, cleaning it up a bit (see below) and sending it in the body of the `DELETE` to the deployment resource, e.g. `/api/v1/deployments/125fd95c-a756-4635-8e1a-361085037870`

{{% copyable %}}
```yaml
---
name: sava:1.2
clusters:
  sava:
    services:
      breed:
        name: sava-frontend:1.2.0
  backend1:
    services:
      breed:
        name: sava-backend1:1.2.0
  backend2:
    services:
      breed:
        name: sava-backend2:1.2.0
```
{{% /copyable %}}

{{% alert info %}}
**Note**: We removed the `deployable`, `environment_variables`, `ports` and some other parts of the blueprint. These are actually not necessary for deletion. Besides that, this is actually exactly the same blueprint we used to initially deploy
the "old" topology.
{{% /alert %}}

## Step 5: When would I use this?

Sounds cool, but when would I use this in practice? Well, basically anytime you release something new!
For example a bugfix release for a mobile API that "didn't change anything significantly"? You could test
this separately and describe it in its own blueprint. After testing, you would merge that exact same blueprint
with your already running production version (the one without the bugfix) and slowly move over to new version.

New major release of your customer facing app? You probably also have some new dependencies that come with that
release. You create some containers and write up a blueprint that describes this new situation, run it in acceptance and test and what have you. Later, you merge it into your production setup, effectively putting it next to it and then slowly move from the old situation to the new situation, including dependencies.

This is the end of this initial getting started tutorial. We haven't done anything with Vamp's SLA's yet, scaling or dictionary system, so there is much more to come!





