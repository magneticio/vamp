# vamp-core

[![Travis build badge](https://travis-ci.org/magneticio/vamp-core.svg?branch=master)](https://travis-ci.org/magneticio/vamp-core)


Vamp-core is the brains of the whole Vamp system. It contains the REST API you send your requests to, it speaks to the underlying PaaS/Container Manager and ties together Vamp's other two services [Vamp Router](https://github.com/magneticio/vamp-router) and [Vamp Pulse](https://github.com/magneticio/vamp-pulse)

## building and running

Clone this repo and use SBT to run it. You specificy your specific config file based on [reference.conf](https://github.com/magneticio/vamp-core/blob/master/bootstrap/src/main/resources/reference.conf) and optionally your logback.xml file:
```bash
sbt -Dconfig.file=conf/application.conf -Dlogback.configurationFile=conf/logback.xml run
```
