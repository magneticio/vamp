---
title: Core
weight: 20
menu:
  main:
    parent: rest-api
---
# Core API

## Breeds

### List breeds

Lists all breeds without any pagination or filtering.

    GET /api/v1/breeds


### Get a single breed

Lists all details for one specific breed.

    GET /api/v1/breeds/:name

### Create breed

Creates a new breed

    POST /api/v1/breeds

### Update a breed

Updates the content of a specific breed.

    PUT /api/v1/breeds/:name

### Delete a breed

Deletes a breed.        

    DELETE /api/v1/breeds/:name
    

**Blueprints**
```
- get all             : GET    /api/v1/blueprints
- create new          : POST   /api/v1/blueprints
- get by name         : GET    /api/v1/blueprints/{name}
- update by name      : PUT    /api/v1/blueprints/{name}
- delete by name      : DELETE /api/v1/blueprints/{name}
```
**Sla**
```
- get all             : GET    /api/v1/slas
- create new          : POST   /api/v1/slas
- get by name         : GET    /api/v1/slas/{name}
- update by name      : PUT    /api/v1/slas/{name}
- delete by name      : DELETE /api/v1/slas/{name}
```
**Scales**
```
- get all             : GET    /api/v1/scales
- create new          : POST   /api/v1/scales
- get by name         : GET    /api/v1/scales/{name}
- update by name      : PUT    /api/v1/scales/{name}
- delete by name      : DELETE /api/v1/scales/{name}
```
**Escalations**
```
- get all             : GET    /api/v1/escalations
- create new          : POST   /api/v1/escalations
- get by name         : GET    /api/v1/escalations/{name}
- update by name      : PUT    /api/v1/escalations/{name}
- delete by name      : DELETE /api/v1/escalations/{name}
```
**Routings**
```
- get all             : GET    /api/v1/routings
- create new          : POST   /api/v1/routings
- get by name         : GET    /api/v1/routings/{name}
- update by name      : PUT    /api/v1/routings/{name}
- delete by name      : DELETE /api/v1/routings/{name}
```
**Filters**
```
- get all             : GET    /api/v1/filters
- create new          : POST   /api/v1/filters
- get by name         : GET    /api/v1/filters/{name}
- update by name      : PUT    /api/v1/filters/{name}
- delete by name      : DELETE /api/v1/filters/{name}
```
## Deployments

### List deployments

```
GET /api/v1/deployments
```

| parameter     | options           | description      |
| ------------- |:-----------------:| ----------------:|
| as_blueprint  | true or false     | exports the deployment as a valid blueprint. This can be used together with the header `Accept: application/x-yaml` to export in YAML format instead of the default JSON. |


```
- create new          : POST   /api/v1/deployments
- get by name         : GET    /api/v1/deployments/{name}
- update by blueprint : PUT    /api/v1/deployments/{name}
- delete by blueprint : DELETE /api/v1/deployments/{name}
```
**Deployment SLA**
```
- get                 : GET      /api/v1/deployments/{name}/cluster/{name}/sla
- set                 : POST|PUT /api/v1/deployments/{name}/cluster/{name}/sla
- delete              : DELETE   /api/v1/deployments/{name}/cluster/{name}/sla
```
**Deployment Scale**
```
- get                 : GET      /api/v1/deployments/{name}/cluster/{name}/services/{name}/scale
- set                 : POST|PUT /api/v1/deployments/{name}/cluster/{name}/services/{name}/scale
```
**Deployment Routing**
```
- get                 : GET      /api/v1/deployments/{name}/cluster/{name}/services/{name}/routing
- set                 : POST|PUT /api/v1/deployments/{name}/cluster/{name}/services/{name}/routing
```

**Debug Routes**
```
- deployment sync     : GET /api/v1/sync
- delete deployments  : GET /api/v1/reset
- SLA check           : GET /api/v1/sla
- escalation check    : GET /api/v1/escalation
- runtime info        : GET /api/v1/info
```