# Docker Driver

## Implementation direction

Main goal: checking status, deploying, undeploying and scaling

We should use an existing Docker client [library](https://docs.docker.com/engine/reference/api/remote_api_client_libraries/) if possible:

- must be actively developed - seems that Scala libraries are not maintained as Java
- doesn't need to implement all features but it should be compatible between Docker versions - at least 1.9 & 1.10, ideally even from 1.5. Reason for this is tha we can't force users to use Docker version supported by Vamp.

## Running Vamp

Main class: `io.vamp.bootstrap.Boot`
Configuration: 
```
-Dlogback.configurationFile=conf/logback.xml -Dconfig.file=conf/application.conf
```

Example log [configuration](https://github.com/magneticio/vamp/blob/master/conf/logback.xml).

In order to use Vamp with other dependencies, it's highly recommended to use [vamp-docker](https://github.com/magneticio/vamp-docker) project and run dependencies as Docker containers.
Building images:

Base image: `./build.sh -b -i=clique-base`

Other depending on KV store: 
- etcd: `./build.sh -b -i=clique-etcd` 
- Consul: `./build.sh -b -i=clique-consul` 
- ZooKeeper: `./build.sh -b -i=clique-zookeeper`

### Configurations suitable for development and debugging

application.conf: 

```yaml

vamp {

  persistence {

    database {
      type: "elasticsearch" # elasticsearch or in-memory
      elasticsearch.url = ${vamp.pulse.elasticsearch.url}
    }

    key-value-store {
      type = "zookeeper"  # zookeeper, etcd or consul
      base-path = "/vamp"

      zookeeper {
        servers = "192.168.99.100:2181"
      }

      etcd {
        url = "http://192.168.99.100:2379"
      }

      consul {
        url = "http://192.168.99.100:8500"
      }
    }
  }

  container-driver {
    type = "marathon" # docker or marathon
    url = "http://192.168.99.100:9090"
    response-timeout = 180 # seconds, timeout for container operations
  }

  rest-api {
    response-timeout = 180 # seconds, HTTP response timeout
  }

  gateway-driver {
    host = "192.168.99.100"
    response-timeout = 180 # seconds, timeout for gateway operations
    
    kibana {
      enabled = false
      elasticsearch.url = ${vamp.pulse.elasticsearch.url}
      synchronization.period  = 0 # seconds, synchronization will be active only if period is greater than 0
    }

    aggregation {
      window = 30 # seconds
      period = 0  # refresh period in seconds, aggregation will be active only if greater than 0
    }
  }

  pulse {
    elasticsearch {
      url = "http://192.168.99.100:9200"
    }
  }

  operation {
    synchronization {
      period = 4 # seconds, synchronization will be active only if period is greater than 0
      timeout {
        ready-for-deployment: 86400 # seconds
        ready-for-undeployment: 86400 # seconds
      }
    }

    deployment {

      scale {
        instances: 1
        cpu: 0.1
        memory: 64MB
      }

      arguments: []
    }

    sla.period = 0 # seconds, sla monitor period
    escalation.period = 0 # seconds, escalation monitor period

    workflow {
      container-image: "magneticio/vamp-workflow-agent:0.9.0"
      command: ""
      scale {
        instances: 1
        cpu: 0.1
        memory: 64MB
      }
    }
  }
}

akka {

  loglevel = "DEBUG"

  log-dead-letters = 0
  log-dead-letters-during-shutdown = off
  actor.debug {
    fsm = on
    receive = on
    lifecycle = on
    autoreceive = on
    event-stream = on
  }
}
```

`vamp.operation.synchronization.period` is time period between 1 step in checking current state of containers, gateways etc.
In order to avoid constant sync, just `vamp.operation.synchronization.period=0`, this is suitable for working on modules involved in sync (e.g. container drivers).
Manual sync (single step) can be triggered by `api/v1/sync` GET request.

In configuration example `192.168.99.100` is IP of container running dependencies.
In order to run dependency containers, simple command are (depending on chosen KV store `vamp.persistence.key-value-store.type`):
- `./run.sh clique-etcd`
- `./run.sh clique-consul`
- `./run.sh clique-zookeeper`

Running using Docker driver: `vamp.container-driver.type="docker"`

## Container driver API

```scala

case class ContainerInfo(`type`: String, container: Any)

// depending on container orchestration engine, containers may have different IDs, 'matching' is a function that should provide
// information to Vamp (by driver) if certain ContainerService correspond to specified deployment & service. 
case class ContainerService(matching: (Deployment, Breed) ⇒ Boolean, scale: DefaultScale, instances: List[ContainerInstance])

case class ContainerInstance(name: String, host: String, ports: List[Int], deployed: Boolean)

trait ContainerDriver {

  // api/v1/info
  def info: Future[ContainerInfo]

  // getting all deployed containers
  def all: Future[List[ContainerService]]

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any]

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any]
}

```
