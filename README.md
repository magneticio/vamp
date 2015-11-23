![](http://vamp.io/img/vamp_logo_blue_circle.svg)

[Website](http://vamp.io) |
[Documentation](http://vamp.io/documentation/) |
[Installation Guide](http://vamp.io/installation/) |

Vamp is the Very Awesome Microservices Platform. Vamp's core features are a platform-agnostic microservices DSL, easy A-B testing/canary releasing on everything and a deep and extendable metrics engine that monitors everything and directly feeds back into your services.

Vamp is open source and mostly written in Scala, with some
parts in Go. 
> Vamp is currently in [alpha](http://en.wikipedia.org/wiki/Software_release_life_cycle#Alpha).

Of course, Vamp is made of multiple services itself. Monolith bad, services good...

component              | purpose
-----------------------|--------
**[Vamp](https://github.com/magneticio/vamp)**               | Main API endpoint, business logic and service coordinator. Talks to the configured container manager (Docker, Marathon etc.) and synchronizes it with Vamp Gateway Agent via ZooKeeper. Uses a standard JDBC database for persistence (H2 and MySQL are tested). Uses Elasticsearch to store Vamp events (e.g. changes in deployments).
**[Vamp Gateway Agent](https://github.com/magneticio/vamp-gateway-agent)** | Reads the HAProxy configuration from ZooKeeper and reloads the HAProxy on each configuration change with as close to zero client request interruption as possible. Reads the logs from HAProxy over socket and push them to Logstash over UDP. Handles and recovers from ZooKeeper and Logstash outages without interrupting the haproxy process and client requests.
**[Vamp UI](https://github.com/magneticio/vamp-ui)**            | Graphical web interface for managing Vamp. Packaged with Vamp. 
**Vamp CLI**           | Command line interface for managing Vamp and integration with (shell) scripts.


More about Vamp [architecture](http://vamp.io/installation/).

Vamp interfaces with container management platforms to take care of running the actual containers. Vamp uses a driver model, similar to database drivers. This means you can use Vamp with Docker on your laptop and later use the exact same scripts to deploy to a Mesosphere cluster running Marathon.


## Deploy Vamp on your laptop

Vamp can run on your laptop with one command. Check out our [Docker quick start](http://vamp.io/getting-started). This should be enough to kick the tires.

## Deploy Vamp with a Mesosphere backend

Vamp uses underlying PaaS and container management platforms like Mesosphere. This is how you run Vamp
[on a Mesosphere cluster](http://vamp.io/documentation/installation/container_drivers/#mesosphere-marathon). This should work on Google Compute Engine, Digital Ocean or anywhere where Mesos runs.

## Using Vamp and more

For documentation on using Vamp and all other info please check [vamp.io](http://vamp.io/documentation/using-vamp) and
take some time to walk through the [getting started](http://vamp.io/documentation/guides/).

## Contributing

Vamp is open source. Any contribtions are welcome. Please check [our contribution guidelines](https://github.com/magneticio/vamp/blob/master/CONTRIBUTING.md)

[![Join the chat at https://gitter.im/magneticio/vamp](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/magneticio/vamp?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
