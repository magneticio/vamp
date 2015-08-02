![](http://vamp.io/img/vamp_logo_blue_circle.svg)

[Website](http://vamp.io) |
[Documentation](http://vamp.io/documentation/) |
[Installation Guide](http://vamp.io/installation/) |

Vamp is the Very Awesome Microservices Platform. Vamp's core features are a platform-agnostic microservices DSL, easy A-B testing/canary releasing on everything and a deep and extendable metrics engine that monitors everything and directly feeds back into your services.

Vamp is open source and mostly written in Scala, with some
parts in Go. 
> Vamp is currently in [alpha](http://en.wikipedia.org/wiki/Software_release_life_cycle#Alpha).

Of course, Vamp is made of multiple services itself. Monolith bad, services good...

component   | purpose
------------|--------
[Vamp-core](https://github.com/magneticio/vamp-core)   | Main API endpoint, business logic and service coordinator. Talks to the configured container manager (Docker, Marathon etc.) and synchronizes it with Vamp Router. Reads metrics from Vamp Pulse and archives life cycle events to Vamp Pulse. Uses a standard JDBC database for persistence (H2 and MySQL are tested).      
[Vamp-pulse](https://github.com/magneticio/vamp-pulse) | Consumes metrics from Vamp Router (using SSE or Kafka feeds) and consumes events from Vamp Core through REST. Makes everything searchable and actionable. Runs on Elasticsearch.
[Vamp-router](https://github.com/magneticio/vamp-router)| Controls HAproxy, creates data feeds from routing information. Gets instructions from Vamp Core through REST and offers SSE and/or Kafka feeds of metric data to Vamp Pulse.

![](http://vamp.io/img/vamp_arch.svg)

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
