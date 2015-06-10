![](http://vamp.io/img/vamp_logo_blue.svg)

[Website](http://vamp.io) |
[Documentation](http://vamp.io/documentation/) |
[Installation Guide](http://vamp.io/documentation/getting-started/installation/) |

Vamp is the Very Awesome Microservices Platform. Vamp's core features are a platform-agnostic microservices DSL, easy A-B testing/canary releasing on everything and a deep and extendable metrics engine that monitors everything and directly feeds back into your services.

Vamp is open source and mostly written in Scala, with some
parts in Go. 
> Vamp is currently in [alpha](http://en.wikipedia.org/wiki/Software_release_life_cycle#Alpha).

Of course, Vamp is made of multiple services itself. Monolith bad, services good...

1. [Vamp-core](https://github.com/magneticio/vamp-core) : the brains of the organization.
2. [Vamp-pulse](https://github.com/magneticio/vamp-pulse) : takes care of storing and retrieving metrics and events.
3. [Vamp-router](https://github.com/magneticio/vamp-router) : routes, balances and filters traffic in clever ways.

## Deploy Vamp on your laptop

Vamp can run on your laptop with one command. Check out our [Docker Setup](https://github.com/magneticio/vamp-docker#option-1-run-vamp-on-docker). This should be enough to kick the tires.

## Deploy Vamp with a Mesosphere backend

Vamp uses underlying PaaS and container management platforms like Mesosphere. This is how you run Vamp
[on a Mesosphere cluster](https://github.com/magneticio/vamp-docker#option-2-run-vamp-with-a-mesos-and-marathon-cluster). This should work on Google Compute Engine, Digital Ocean or anywhere where Mesos runs.

## Using Vamp and more

For documentation on using Vamp and all other info please check [vamp.io](http://vamp.io/documentation/) and
take some time to walk through the [getting started](http://vamp.io/getting-started).

## Contributing

Vamp is open source. Any contribtions are welcome. Please check [our contribution guidelines](https://github.com/magneticio/vamp/blob/master/CONTRIBUTING.md)
