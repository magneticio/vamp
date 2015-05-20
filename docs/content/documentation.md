---
title: Documentation
type: documentation
url: /documentation
---

# What is Vamp ?

Vamp, the _Very Awesome Microservices Platform_, is aimed at anyone who wants to adopt a microservices model for 
developing their online services, with some awesome features thrown in for free.  
Vamp's core features are a platform-agnostic microservices DSL, A-B testing/canary releasing on everything
and a deep and extendable metrics engine that monitors everything and directly feeds back into your services.

* Vamp is not a PaaS, but uses the power of PaaS systems under the hood.
* Vamp is functionality agnostic, but functions well in an API centric, event driven and stateless environment. 
* Vamp is inspired by [Netflix OSS](http://netflix.github.io/) but also by Reactive programming patterns. Vamp comes in an open source flavour and an Enterprise™ flavour.
* Vamp strongly focusses on giving you the benefit of microservices, without the downsides.

The following links give a general background on why Vamp exists and what problems it is trying to solve: 

* [Making Netflix API More Resilient](http://techblog.netflix.com/2011/12/making-netflix-api-more-resilient.html)
* [Netflix Canary Releasing](http://techblog.netflix.com/2013/08/deploying-netflix-api.html)
* [Linkedin real time monitoring data](http://engineering.linkedin.com/distributed-systems/log-what-every-software-engineer-should-know-about-real-time-datas-unifying)

## Purpose of Vamp

* Provide a model for describing microservices and their dependencies in blueprints.
* Provide a runtime/execution engine for deploying these blueprints, similar to [AWS Cloudformation](http://aws.amazon.com/cloudformation/)
* Allow full A/B testing and [canary releasing](http://martinfowler.com/bliki/CanaryRelease.html) on all microservices.
* Allow "Big Data" type analysis- and prediction patterns on service behaviour, performance and lifecycle.
* Provide clear SLA management and service level enforcement on services.

## Problem Definition

Developing applications using a microservices architecture has many benefits with regard to:

* speed of development & deployment
* separation of concerns
* scalability & resiliency

However, __using a microservices architecture raises complexity__: they have dozens of dependencies, each of which
will inevitably fail at some point. Furthermore, deployments and upgrades need coordination and orchestration. 
Security concerns multiply. Data feeds and monitoring/logs need to be aggregated and zipped together.
All of this has to be managed in such a way that users __get the benefit of microservices__, instead of only 
feeling the extra complexity and pain.

## Vision

1. Microservices as an architectural model will be the dominant model for all serious online companies.
2. Containers are going to be the next VM's: the common currency for compute power.
3. Managing complex microservices architectures brings completely new challenges.
4. Automatic deployment of containers is going to be a commodity: this is not our focus.
5. Basic health monitoring of containers is going to be a commodity: this is not our focus.

Please check our [Getting Started](/getting-started/) to get up to speed on how to use Vamp