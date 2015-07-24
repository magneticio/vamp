---
title: 1. Deploying a blueprint
type: documentation
slug: /getting-started-tutorial/1-deploying/
---

# 1. Deploying your first blueprint

If everything went to plan, you should have your Vamp installation up & running. If not, please check [how to install
Vamp](/getting-started/)

## Step 1: Deploying a monolith

Imagine you or the company you work for still use monolithic applications. I know, it sounds far fetched...
This application is conveniently called *Sava monolith* and is at version 1.0.  

You've managed to wrap your monolith in a Docker container, which lives in the Docker hub under `magneticio/sava:1.0.0`. Your app normaly runs on port `80` but you want to expose it under port `9050` in this case. Let's deploy this to Vamp using the following simple blueprint. Don't worry too much about what means what: we'll get there.

{{% copyable %}}
```yaml
---
name: sava:1.0

endpoints:
  sava.port: 9050

clusters:

  sava:
    services:
      breed:
        name: sava:1.0.0
        deployable: magneticio/sava:1.0.0
        ports:
          port: 8080/http
      scale:
        cpu: 0.2       
        memory: 256  
        instances: 1
```
{{% /copyable %}}

Use your favorite tools like [Postman](https://www.getpostman.com/), [HTTPie](https://github.com/jakubroztocil/httpie) or Curl to post this to the `api/v1/deployments` endpoint of Vamp. 

>**Note**: Take care to set the correct `Content-Type: application/x-yaml` header on the POST request. Vamp is kinda
strict with regard to content types, because we support JSON and YAML so we need to know what you are sending. 

Using `curl`:

```
curl -v -X POST --data-binary @sava_1.0.yaml -H "Content-Type: application/x-yaml" http://192.168.59.103:8080/api/v1/deployments
```

Using `httpie`

```
http POST http://192.168.59.103:8080/api/v1/deployments Content-Type:application/x-yaml < sava_1.0.yaml
```

After POST-ing, Vamp should respond with a `202 Accepted` message and return a JSON blob similar to the blob 
below. This means Vamp is trying to deploy your container. You'll notice some parts are filled in for you,
like a default scale, a default routing and of course a UUID as a name.

```json
{
    "name": "e1c99ca3-dc1f-4577-aa1b-27f37dba0325",
    "endpoints": {
        "sava.port": "9050"
    },
    "clusters": {
        "sava": {
            "services": [
                {
                    "state": {
                        "name": "ReadyForDeployment",
                        "started_at": "2015-04-23T08:18:03.956Z"
                    },
                    "breed": {
                        "name": "sava:1.0.0",
                        "deployable": "magneticio/sava:1.0.0",
                        "ports": {
                            "port": "8080/http"
                        },
                        "environment_variables": {},
                        "constants": {},
                        "dependencies": {}
                    },
                    "scale": {
                        "cpu": 0.2,
                        "memory": 256,
                        "instances": 1
                    },
                    "routing": {
                        "weight": 100,
                        "filters": []
                    },
                    "servers": [],
                    "dependencies": {}
                }
            ],
            "routes": {
                "80": 33003
            }
        }
    },
    "ports": {},
    "environment_variables": {},
    "constants": {},
    "hosts": {
        "sava": "10.26.184.254"
    }
}
```
## Step 2: Checking out our application

You can follow the deployment process of our container by checking the `/api/v1/deployments` endpoint and checking when the `state` field changes from `ReadyForDeployment` to `Deployed`. You can also check Marathon's GUI.

When the application is fully deployed you can check it out at Vamp Router's address + the port that was assigned in the blueprint, e.g: `http://10.26.184.254:9050/`. It should report a refreshing [hipster lorem ipsum](http://hipsterjesus.com/) upon each reload.

![](/img/screenshots/monolith1.png)

Ok, that's great, but not very exciting. Let's do a canary release in [the second part of this getting started tutorial →](/documentation/guides/getting-started-tutorial/2-canary-release/)


