---
title: Vamp Pulse
weight: 950
menu:
  main:
    parent: rest-api
---

# Pulse API


## Query metrics & events using tags

Getting specific metrics or sets of metrics out of Pulse is done using tags. You can use specific time ranges 
designated by timestamp or use special range query operators, described [on the elastic.co site](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-range-query.html).

**Note:** the default page size for a set or returned metrics is 30.

For example, this query gets the most recent current session metrics for the "frontend" cluster in the "d9b42796-d8f6-431b-9230-9d316defaf6d" deployment.

`POST /api/v1/events/get`

```JSON
{
  "tags": ["routes:d9b42796-d8f6-431b-9230-9d316defaf6d_frontend_8080","metrics:rtime","route"]
    "timestamp" : {
      "lte" : "now"
    }
}
```

The response will echo back the events in the time range with the original set of tags associated with the events. 

```JSON
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

Another example is getting the response time for a specific service, in this case the `monarch_front:0.2` service that is part
of the `214615ec-d5e4-473e-a98e-8aa4998b16f4` deployment and lives in the `frontend` cluster.

`POST /api/v1/events/get`


```JSON
{
  "tags": ["routes:214615ec-d5e4-473e-a98e-8aa4998b16f4_frontend_8080","metrics:scur","services:monarch_front:0.2","service"]
    "timestamp" : {
      "lte" : "now"
    }
}
```


## Aggregations
Supported aggregations are:
average, min, max, count

- aggregated metric events (i.e. events with simple numeric value)

Simple example of getting an average value

`POST /api/v1/events/get`

```JSON
{
   "tags" : ["metric_name"],
   "aggregator" :  
    {
       "type" : "average"
    },
    "time" : {
      "from" : "2015-02-18T04:57:56+00:00",
      "to" : "2015-02-18T04:57:56+00:00"
    }
}
```

response

```JSON

{
  "value" : 65.3
}

```

- aggregated events

Simple example of getting an average value of a custom event, where the event structure is as follows:

```JSON
{
    "tags": ["deployment"],
    "type": "io.vamp_core.notification.DeploymentNotification",
    "value": { 
        "blueprint" : "my_blueprint_id",
        "metrics" : {
           "startup_time" : 300,
           "setup_time" : 100 
        }
    },
    "timestamp": "2015-02-18T04:57:56+00:00"
}

```


`POST /api/v1/events/get`

```JSON
{
   "tags" : ["metric_name"],
   "type" : "io.vamp_core.notification.DeploymentNotification",
   "aggregator" : {
       "type" : "average", 
       "field" : "metrics.setup_time"
    },
    "time" : {
      "from" : "2015-02-18T04:57:56+00:00",
      "to" : "2015-02-18T04:58:56+00:00"
    }
}
```

response

```JSON

{
  "value" : 35.6
}

```


A more complex example
This one is still WIP, we only support simple examples now.

`POST /api/v1/events/get`

```JSON
{
    "tags" : ["whatever.metric"],
   "aggregator" : {
      "type" : "percentile",
      "percents" : [95, 99, 99.9] 
   }, 
   "time" : { 
     "from" : "2015-02-18T04:57:56+00:00",
     "to" : "2015-02-18T04:57:56+00:00"
   }
}
```

response is specific for this aggregator type:

```JSON
{    
   "95.0": 60,
   "99.0": 150,
   "99,9": 20
}
```


### Add metrics/events

- single metric

Simple example of a metric event:   
If you also post a type it would be ignored, since the numeric value is assumed to be a metric type.
`POST /events`   
```json
 {
   "tags": ["some.metric"],
    "value": 123,
   "timestamp": "2015-02-18T04:57:56+00:00"
} 
```

More complex example of an event:   
`POST /api/v1/events`   
```json
{
    "tags": ["deployment"],
    "type": "io.vamp_core.notification.DeploymentNotification",
    "value": { 
        "blueprint" : "my_blueprint_id",
        "started_at" : "some_time",
        "finished_at" : "some_time_later",
        "environment" : "production" 
    },
    "timestamp": "2015-02-18T04:57:56+00:00"
}

```

Example of an event blob, treated as string, could not be aggregated later on:   
You simply omit type and the value should be non-numeric. 
`POST /api/v1/events`   
```json
{
    "tags": ["deployment"],
    "value": { 
        "blueprint" : "my_blueprint_id",
        "started_at" : "some_time",
        "finished_at" : "some_time_later",
        "environment" : "production" 
    },
    "timestamp": "2015-02-18T04:57:56+00:00"
}

```
