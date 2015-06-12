  ---
title: Overview
weight: 10
menu:
  main:
    parent: rest-api
    identifier: rest-api-overview
---
# Overview

Vamp has three REST API's: **Core**, **Pulse** and **Router**. You will be interacting with Core 90% of the time and 10% with Pulse. In daily usage, there is no need to interact with the Router API as Core and Pulse take care of that. All API's stick to a common set of principles:

## Content types

* Core requests can be in YAML format or JSON format. Set the `Content-Type` request header to `application/x-yaml` or `application/json` accordingly.
* Core and Pulse responses can be in YAML format or JSON format. Set the `Accept` request header to `application/x-yaml` or `application/json` accordingly.

## Pagination

From release 0.7.8 onwards Core and Pulse API endpoints support pagination with the following scheme:

* Request parameters `page` (starting from 1, not 0) and `per_page` (by default 30) e.g:

```
GET http://vamp-pulse:8083/api/v1/events/get?page=5&per_page=20
```

* Response headers `X-Total-Count` giving the total amount of items (e.g. 349673) and a `Link` header for easy traversing, e.g.
```
Link → 
  <http://localhost:8083/api/v1/events/get?page=1&per_page=5>; rel=first, 
  <http://localhost:8083/api/v1/events/get?page=1&per_page=5>; rel=prev, 
  <http://localhost:8083/api/v1/events/get?page=2&per_page=5>; rel=next, 
  <http://localhost:8083/api/v1/events/get?page=19&per_page=5>; rel=last
``` 

See [Github's implementation](https://developer.github.com/guides/traversing-with-pagination/) for more info.

## Return codes

* Create & Delete operations are idempotent: sending the second request with the same content will not result to an error response (4xx).
* An update will fail (4xx) if a resource does not exist.
* A successful create operation has status code 201 `Created` and the response body contains the created resource.
* A successful update operation has status code 200 `OK` or 202 `Accepted` and the response body contains the updated resource.
* A successful delete operation has status code 204 `No Content` or 202 `Accepted` with an empty response body.