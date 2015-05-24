---
title: Common API principles
weight: 10
menu:
  main:
    parent: rest-api
---
# Common API principles

Each Vamp service (Core, Router and Pulse) has its own REST API. All API's stick to a set of common principles:

* Core requests can be in YAML format or JSON format. Set the `Content-Type` request header to `application/x-yaml` or `application/json` accordingly
* Create & Delete operations are idempotent: sending the second request with the same content will not result to an error response (4xx).
* An update will fail (4xx) if a resource does not exist.
* A successful create operation has status code 201 (Created) and the response body contains the created resource (JSON).
* A successful update operation has status code 200 (OK) or 202 (Accepted) and the response body contains the updated resource (JSON).
* A successful delete operation has status code 204 (No Content) or 202 (Accepted) with an empty response body.