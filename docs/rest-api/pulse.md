---
title: Pulse
weight: 40
menu:
  main:
    parent: rest-api
---

# Pulse API

## Query metrics & events

The default page size for a set or returned metrics is 30.

- single event

- events in time range

`POST /api/v1/events/get`

```json

{
   "tags" : ["metric_name"],
   "time" : { 
     "from" : "2015-02-18T04:57:56+00:00",
     "to" : "2015-02-18T04:57:56+00:00"
   }
}
```

response will echo back the events in the time range with the original set of tags associated with the events. 

```json
[
 {
  "tags": ["metric_name","some_original_tag"],
  "value": 123,
  "timestamp": "2015-02-18T04:57:56+00:00",
  "type": ""
 } ,
 {
  "tags": ["metric_name","also_some_original_tag"],
  "value": 421,
  "timestamp": "2015-02-18T04:57:56+00:00",
  "type": ""
 } 
]
```

### Aggregations
Supported aggregations are:
average, min, max, count

- aggregated metric events (i.e. events with simple numeric value)

Simple example of getting an average value

`POST /api/v1/events/get`

```json
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

```json

{
  "value" : 65.3
}

```

- aggregated events

Simple example of getting an average value of a custom event, where the event structure is as follows:

```json
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

```json
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

```json

{
  "value" : 35.6
}

```


A more complex example
This one is still WIP, we only support simple examples now.
`POST /api/v1/events/get`


```json
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

```json
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
