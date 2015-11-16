---
title: Metrics & Events
weight: 60
menu:
  main:
    parent: using-vamp
---
# Metrics & Events

Vamp collects metrics and events on all running services. Interaction with the API also creates events, like updating blueprints or deleting a deployment. Furthermore, Vamp allows third party applications to create events and trigger Vamp actions.

All metrics and events are stored and retrieved using the Event API that is part of Vamp Pulse. Here is a JSON example of a "deployment create" event.

```JSON
{
  "tags": [
    "deployments",
    "deployments:6ce79a33-5dca-4eb2-b072-d5f65e4eef8a",
    "archiving",
    "archiving:create"
  ],
  "value": "name: sava",
  "timestamp": "2015-04-21T09:15:42Z"
}
```

All events and metrics stick to some **basic rules**:


- All data in Vamp Pulse are events. 
- Values can be any JSON object or it can be empty.
- Timestamps are in ISO8601/RFC3339.
- Timestamps are optional. If not provided, Pulse will insert the current time.
- Timestamps are inclusive for querying.
- Events can be tagged with metadata. A simple tag is just single string.
- Querying data by tag assumes "AND" behaviour when multiple tags are supplied, i.e. ["one", "two"] would only fetch records that are tagged with both.
- Supported event aggregations are: `average`, `min`, `max` and `count`.

## How tags are organized

In all of Vamp's components we follow a REST (resource oriented) schema, for instance:
```
/deployments/{deployment_name} 
/deployments/{deployment_name}/clusters/{cluster_name}/services/{service_name}
```
Tagging is done using a very similar schema: "{resource_group}", "{resource_group}:{name}". Some examples:

```
"deployments", "deployments:{deployment_name}"
"deployments", "deployments:{deployment_name}", "clusters", "clusters:{cluster_name}", "services", "services",services:{service_name} "
```

This schema allows querying per group and per specific name. Getting all events related to all deployments is done by using tag "deployments". Getting events for specific deployment "deployments:{deployment_name}".

## Query metrics & events using tags

Using the tags schema and timestamps, you can do some powerful queries. Either use an exact timestamp or use special range query operators, described [on the elastic.co site](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-range-query.html).

> **Note:** the default page size for a set or returned metrics is 30. From Vamp 0.7.8 onwards, we support pagination.


### Example 1: get all metrics

This query just gets ALL metrics up till now, taking into regard the pagination.

`POST /api/v1/events/get`

```json
{
  "tags": ["metrics"],
    "timestamp" : {
      "lte" : "now"
    }
}
```

### Example 2: response time for a cluster

This query gets the most recent response time metrics for the "frontend" cluster in the "d9b42796-d8f6-431b-9230-9d316defaf6d" deployment. 

`POST /api/v1/events/get`

```json
{
  "tags": ["routes:d9b42796-d8f6-431b-9230-9d316defaf6d_frontend_8080","metrics:rtime","route"],
    "timestamp" : {
      "lte" : "now"
    }
}
```

**Notice** the "route:<UUID>", "metrics:rtime" and "routes" tags. This means "give me the response time of this specific route at the route level". The response will echo back the events in the time range with the original set of tags associated with the events. 

```json
[
    {
        "tags": [
            "routes",
            "routes:d9b42796-d8f6-431b-9230-9d316defaf6d_frontend_8080",
            "metrics:scur",
            "metrics",
            "route"
        ],
        "value": 0,
        "timestamp": "2015-06-08T10:28:35.001Z",
        "type": "router-metric"
    },
    {
        "tags": [
            "routes",
            "routes:d9b42796-d8f6-431b-9230-9d316defaf6d_frontend_8080",
            "metrics:scur",
            "metrics",
            "route"
        ],
        "value": 0,
        "timestamp": "2015-06-08T10:28:32.001Z",
        "type": "router-metric"
    }
]    
```

### Example 3: current sessions for a service

Another example is getting the current sessions for a specific service, in this case the `monarch_front:0.2` service that is part of the `214615ec-d5e4-473e-a98e-8aa4998b16f4` deployment and lives in the `frontend` cluster.

`POST /api/v1/events/get`

```json
{
  "tags": ["routes:214615ec-d5e4-473e-a98e-8aa4998b16f4_frontend_8080","metrics:scur","services:monarch_front:0.2","service"],
    "timestamp" : {
      "lte" : "now"
    }
}
```

**Notice** we made the search more specific by specifying the "services" and then "service:<SERVICE NAME>" tag.
Also, we are using relative timestamps: anything later or equal (lte) than "now".

### Example 4: all known metrics for a service

This example gives you all the metrics we have for a specific service, in this case the same service as in example 2. In this way you can get a quick "health snapshot" of service, server, cluster or deployment.

`POST /api/v1/events/get`

```json
{
  "tags": ["routes:214615ec-d5e4-473e-a98e-8aa4998b16f4_frontend_8080","metrics","services:monarch_front:0.2","service"],
    "timestamp" : {
      "lte" : "now"
    }
}
```
**Notice** we made the search less specific by just providing the "metrics" tag and not telling the API which specific one we want.

## Server-sent events (SSE)

Events and metrics can be streamed back directly from Vamp. 

`POST /api/v1/events/stream`

In order to narrow down (filter) events, list of tags could be provided in the request body.

```json
{
  "tags": ["routes:214615ec-d5e4-473e-a98e-8aa4998b16f4_frontend_8080","metrics"]
}
```

GET method can be also used (may be more convenient):

`GET /api/v1/events/stream`

Using tags:

`GET /api/v1/events/stream?tags=archiving&tags=breeds`

## Archiving

All changes in artifacts (creation, update or deletion) triggered by REST API calls are archived. We store the type of event and the original representation of the artefact. It's a bit like a Git log. 

Here is an example event:

```json
{
  "tags": [
    "deployments",
    "deployments:6ce79a33-5dca-4eb2-b072-d5f65e4eef8a",
    "archiving",
    "archiving:delete"
  ],
  "value": "",
  "timestamp": "2015-04-21T09:17:31Z",
  "type": ""
}
```

Searching through the archive is 100% the same as searching for metrics. The same tagging scheme applies. 
The following query gives back the last set of delete actions executed in the Vamp API, regardless of the artefact type.

`POST /api/v1/events/get`


```json
{
  "tags": ["archiving","archiving:delete"],
    "timestamp" : {
      "lte" : "now"
    }
}
```

