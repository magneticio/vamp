Vamp relies on underlying container management/PaaS system. Current support:

* [Marathon](https://mesosphere.github.io/marathon/)
* [Docker](https://www.docker.com/) - for an easy development support

## Marathon Driver

```
vamp.core.container-driver {
  type = "marathon"
  url = "" # Marathon URL
}

```

## Docker Driver

Using the Docker driver it is easy to do quick deployment of containers (micro-services) on local machine.
Simple setup, a part of Docker installation:

* run [Vamp Router](https://github.com/magneticio/vamp-router) as container, e.g.
```
  docker run --net=host magneticio/vamp-router:latest
```
* configure Vamp to use the Router:

```
  router-driver {
    url = "http://<IP>:10001" 
    host = "<IP>"
  }
```

where IP is the Docker localhost or in case of boot2docker:

```
  boot2docker ip
```

* configure Vamp to use the Docker driver:

```
  vamp.core.container-driver {
    type = "docker"
  }
```

The Docker driver looks for the location of the Docker API endpoint by looking at the following environment variables, for instance with Boot2Docker:

```
DOCKER_HOST=tcp://192.168.59.103:2376
DOCKER_TLS_VERIFY=1
DOCKER_CERT_PATH=/Users/johndoe/.boot2docker/certs/boot2docker-vm
```

Docker driver doesn't support scaling, i.e. there can be only one instance of service even though router may contain route with multiple servers for the same service.

Example:

```YAML
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
          port: 80/http
    
      scale:
          cpu: 1
          memory: 1024
          instances: 2
```

Will result in something similar to (truncated result):

```
docker ps
CONTAINER ID        IMAGE                           
f6c6651d78e7        magneticio/sava:1.0.0              
8f12a4f09c86        magneticio/vamp-router:latest    
```

and route with 2 server because scale instances is 2. Scale update will update the route only.


## Dialects

Vamp supports the features of the underlying container management/PaaS system in its own DSL as a "dialect".

This means users can use platform specific tags in a blueprint to pass on properties specific information to the container driver and eventually the container manager itself. An example would be passing the uri array used by Marathon to pass in arbitrary files to a container. Or setting volumes to be mounted in a container.

```YAML
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
          port: 80/http
      marathon:
        uris:
          -
            "http://domain/uri"
          - 
            "file://somefile.txt"
        labels:
          environment: staging
          some_key: some_value
    scale:
      cpu: 0.5       
      memory: 512  
      instances: 1
```

Dialect can be specified on cluster and service level. Service dialect will always override the cluster one. It is possible to specify different dialects for different PaaS.

For example "docker" and "marathon" on the service level and only "marathon" on cluster level. Note that due to override feature LD_LIBRARY_PATH will **NOT** be used unless "marathon" definition on service level is removed.

```YAML
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
          port: 80/http
      docker:
        cmd: echo
      marathon:
        env:
          LOCAL: "/usr/local"

    marathon:
      env:
        LD_LIBRARY_PATH: "/usr/local/lib/myLib"
```

other example:

```yaml
name: busy-top:1.0

clusters:

  busyboxes:
    services:
      breed:
        name: busybox
        deployable: registry.magnetic.io/busybox:latest
      marathon:
       uris:
         -
           "https://gist.githubusercontent.com/tnolet/a8cafb6261b9ddb696e8/raw/297739fa6bfc45cd73967364193752555a465b02/.dockercfg"
       cmd: "top"
       labels:
         environment: "staging"
         owner: "buffy the vamp slayer"
       container:  
         volumes:
           -
             containerPath: "/tmp/"
             hostPath: "/tmp/"
             mode: "RW"
      scale:
        cpu: 0.1       
        memory: 256  
        instances: 1
```        
