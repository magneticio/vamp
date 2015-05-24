---
title: Deployments
weight: 40
menu:
  main:
    parent: rest-api
---

# Deployments

Deployments are non-static entities in the Vamp eco-system. They represent runtime structures so any changes to them will take time to execute and can possibly fail. Most API calls to the `/deployments` endpoint will therefore return a `202: Accepted` returncode, indicating the asynchronous nature of the call.

Deployments have a set of sub resources: **SLA's**, **scales** and **routings**. These are instantiations of their static counterparts.

## List deployments


	GET /api/v1/deployments

| parameter     | options           | description      |
| ------------- |:-----------------:| ----------------:|
| as_blueprint  | true or false     | exports the deployment as a valid blueprint. This can be used together with the header `Accept: application/x-yaml` to export in YAML format instead of the default JSON. |

## Get a single deployment

Lists all details for one specific deployment. You can also use the `?as_blueprint` parameter here.

    GET /api/v1/deployments/:name

## Create deployment using a blueprint

Creates a new deployment

	POST /api/v1/deployments

## Update a deployment using a blueprint

Updates the settings of a specific deployment.

    PUT /api/v1/deployments/:name

## Delete a deployment using a blueprint

Deletes all or parts of a deployment.        

    DELETE /api/v1/deployments/:name

# Deployment SLA's

## Get a deployment SLA

Lists all details for a specific SLA that's part of a specific cluster.

	GET /api/v1/deployments/:name/cluster/:name/sla
	
## Set a deployment SLA

Creates or updates a specific deployment SLA.

	POST|PUT /api/v1/deployments/:name/cluster/:name/sla
	
## Delete a deployment SLA

Deletes as specific deployment SLA.

	DELETE /api/v1/deployments/:name/cluster/:name/sla


# Deployment scales

Deployment scales are singular resources: you only have one scale per service. Deleting a scale is not a meaningfull action.

## Get a deployment scale

Lists all details for a specific deployment scale that's part of a service inside a cluster.

	GET /api/v1/deployments/:name/cluster/:name/services/:name/scale
	
## Set a deployment scale	

Updates a deployment scale.

	POST|PUT /api/v1/deployments/:name/cluster/:name/services/:name/scale

# Deployment routings

Deployment routing are singular resources: you only have one routing per service. Deleting a routing is not a meaningfull action.

## Get a deployment routing

Lists all details for a specific deployment routing that's part of a service inside a cluster.

	GET /api/v1/deployments/:name/cluster/:name/services/:name/routing
	
## Set a deployment routing	

Updates a deployment routing.

	POST|PUT /api/v1/deployments/:name/cluster/:name/services/:name/routing