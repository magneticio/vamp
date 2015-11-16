---
title: Configuration
weight: 11
type: documentation
menu:
  main:
    parent: installation
---

# Configuring Vamp

Vamp Core and Vamp Pulse each ship with an application.conf file. This file contains all the configurable settings for both components. When installing Vamp through a package manager (yum, apt-get) you can find this file in `/usr/share/vamp-core/conf` and `/usr/share/vamp-pulse/conf` respectively. The config files are [HOCON type](https://github.com/typesafehub/config) files.

- [Vamp Core](#vamp-core)
- [Vamp Pulse](#vamp-pulse)
- [Vamp Router](#vamp-router)


**Vamp Router** is configured slightly differently because it isn't a Scala application.

## Vamp Core

The Core `application.conf` consists of the following sections. All sections are nested inside a parent `vamp.core {}` tag.

### rest-api
Configure the port, host name and interface Vamp Core runs on using the `rest-api.port` 

```
vamp.core {
  rest-api {
    interface = 0.0.0.0
    host = localhost
    port = 8080
    response-timeout = 10 # seconds, HTTP response time out    
    info {
      message = "Hi, I'm Vamp! How are you?"
      timeout = 5 # seconds, response timeout for each components info point
    }
    sse {
      keep-alive-timeout = 15 # seconds, timeout after a new line ("\n")
      router-stream = ${vamp.core.router-driver.url}/v1/stats/stream
    }
  }
}    
``` 


### persistence

Vamp Core uses a JDBC compatible database for persistent storage*. By default Vamp runs a simple, in-memory, H2 database that persists to a file on disk. This comes pre-packaged and is easy for getting started.

Vamp is also tested against **Postgres**. You can configure this in the `jdbc` section of the application.conf by choosing the correct `slick-driver` and providing the typical parameters:  
**H2 config** (default)

```hocon
vamp.core {
  persistence {
    response-timeout = 5 # seconds
    storage-type: "jdbc"
    jdbc {
      slick-driver = "scala.slick.driver.H2Driver$"
      provider = {
        url = "jdbc:h2:./vamp-core-db"
        driver = org.h2.Driver
        connectionPool = disabled
        keepAliveConnection = true
      }
    }
  }
}
```
**Postgres config** 
 
```hocon
vamp.core {
  persistence {
    response-timeout = 5 # seconds
    jdbc {
      database-schema-name = ""
      slick-driver = "scala.slick.driver.PostgresDriver$"
      provider = {
        url = "jdbc:postgresql://<hostname>/<database-name>"
        user = "<db-user>"
        password = "<db-password>"
        connectionPool = disabled
        keepAliveConnection = true
      }
    } 
  }
} 
```
**vamp can actually use different storage backends, but those are still experimental* 

### container driver

Configuration for container drivers have their own page! Please look here [how to set up and use container drivers](/documentation/installation/container_drivers/)

### router-driver

The router-driver section configures where Vamp Core can find Vamp Router and how traffic should be routed through Vamp Router. See the [configuration docs for Vamp Router](#vamp-router) for more details. What is important for Vamp Core is that **you need to tell Vamp Core where it can find Vamp Router's API and what Vamp Router's / HAproxy's internal IP address is**. See the below example:

```hocon
vamp.core {
  router-driver {
    url = "http://104.155.30.171:10001" # Vamp Router API endpoint, external IP.
    host = "10.193.238.26"              # Vamp Router / Haproxy, internal IP.
    response-timeout = 30               # seconds
  }
}  
``` 

The reason for this, is that when services are deployed, they need to able to find Vamp Router in their respective network. This can be a totally different network than where Vamp Core is running.

### pulse

The pulse section tells Vamp Core where it can find Vamp Pulse and where it can find the Elasticsearch backend Vamp Pulse uses. This is necessary because Vamp Core takes care of initializing Elasticsearch with the right indeces and mappings.

```hocon
vamp.core {
  pulse {
    url = "http://localhost:8083" # Vamp Pulse API endpoint
    elasticsearch {
      url = "http://localhost:9200"
      index {
        name = "vamp-pulse"
        time-format.event = "YYYY-MM-dd" # Controls the time based indexing. 
      }                                  # By default, we create a new index every day.
    }
    response-timeout = 30 # seconds, timeout for pulse operations
  }
}  
```

### operation

The operation section holds all parameters that control how Vamp Core executes against "external" services: this also includes Vamp Pulse and Vamp Router.

```hocon
operation {
	sla.period = 5 # seconds, controls how often an SLA checks against metrics
  	escalation.period = 5 # seconds, controls how often Core checks for escalation events.
	synchronization {
		period = 1 # seconds, controls how often Core performs a sync between Vamp and the container driver.
      	timeout {
      		ready-for-deployment: 600		# seconds, controls how long Core waits for a 
        								   		# service to start. If the service is not started 
        								   		# before this time, the service is registered as "error"
        	ready-for-undeployment: 600 	# seconds, similar to "ready-for-deployment", but for
        										# the removal of services.
      }
   }
}
```  

## Vamp Pulse

### rest-api
Configure the port, host name and interface Vamp Pulse runs on using the `rest-api.port` 

```hocon
vamp.pulse {
  rest-api {
    interface = 0.0.0.0
    port = 8083
    host = "localhost"
    response-timeout = 10 # seconds

    info {
      message = "Hi, I'm Vamp Pulse! How are you?"
    }
  }
}  
```  
## elasticsearch

Configure whether to use an embedded Elasticsearch instance or an external instance. For any serious
work an external Elasticsearch cluster is recommended.

```hocon
vamp.pulse {
  elasticsearch {

    type = "embedded" # "remote" or "embedded"

    response-timeout = 10 # seconds

    tcp-port = 9300
    host = "localhost"
    cluster-name = "elasticsearch" # when using an external cluster, specify the name.
    rest-api-url = "http://localhost:9200"

    embedded.data-directory = "./data" # the directory where embedded data is stored.

  index {
        name = "vamp-pulse"
        time-format.event = "YYYY-MM-dd" # Controls the time based indexing. 
      }                                  # By default, we create a new index every day.
    }											 # This should be the same as in the Core config.
  }
}  
```  
### eventstream

The evenstream section configures how Pulse connect to Vamp Router's 
metrics stream and what transport it should use. The default transport is Server
Sent Events, but you can also configure a Kafka cluster.
This configuration should match how Vamp Router exposes the event stream.

```hocon
vamp.pulse {
  event-stream {
    driver = "sse" # "sse", "kafka" or "none"
    sse {
      url = "http://localhost:10001/v1/stats/stream" # Vamp Router SSE url.
      timeout {
        http.connect = 2000				# milliseconds, connection timeout.
        sse.connection.checkup = 3000   # milliseconds, interval for connection checks.
      }
    }
    kafka {					# Kafka connection string
      url = ""				
      topic = "metric"
      group = "vamp-pulse"
      partitions = "1"
    }
  }
}  
```


## Vamp Router

Basically Vamp Router does three things:

- Accept API requests on its API endpoint (default port: 10001)
- Instruct HAproxy how/what/where to route
- Stream feeds to Vamp Pulse

This means that Vamp Router and HAproxy should be installed on the same (V)LAN as the containers, 
or at least be able to transparently find the containers IP addresses. This can also be done through setting the correct gateways or setting up a local DNS. 

Vamp Router is configured completely by providing startup flags or setting environment variables.
The most important one is the `-binary` flag that indicates where Vamp Router can find the Haproxy 
binary.

Router's command line options are:

```
  -binary="/usr/local/sbin/haproxy":                Path to the HAproxy binary
  -customWorkDir="":                                Custom working directory for logs, configs and sockets
  -kafkaHost="":                                    The hostname or ip address of the Kafka host
  -kafkaPort=9092:                                  The port of the Kafka host
  -configFile="/haproxy_new.cfg":                   Target location of the generated HAproxy config file
  -json="/vamp_router.json":                        JSON file to store internal config.
  -template="configuration/templates/haproxy_config.template": Template file to build HAproxy config
  -logPath="/logs/vamp-router.log":                 Location of the log file
  -pidFile="/haproxy-private.pid":                  Location of the HAproxy PID file
  -port=10001:                                      Port/IP to use for the REST interface.
  -zooConKey="magneticio/vamplb":                   Zookeeper root key
  -zooConString="":                                 A zookeeper ensemble connection string
```
For more info on using Vamp Router as a standalone service, please check [github](https://github.com/magneticio/vamp-router).