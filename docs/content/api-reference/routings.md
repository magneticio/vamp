---
title: Routings
weight: 60
menu:
  main:
    parent: api-reference
---

# Routings

## List routings

Lists all routings without any pagination or filtering.

    GET /api/v1/routings

## Get a single routing

Lists all details for one specific routing.

    GET /api/v1/routings/:name

## Create routing

Creates a new routing.

    POST /api/v1/routings

Accepts JSON or YAML formatted routings. Set the `Content-Type` request header to `application/json` or `application/x-yaml` accordingly.    

| parameter     | options           | default          | description       |
| ------------- |:-----------------:|:----------------:| -----------------:|
| validate_only | true or false     | false            | validates the routing and returns a `201 Created` if the routing is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Update a routing

Updates the content of a specific routing.

    PUT /api/v1/routings/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | validates the routing and returns a `200 OK` if the routing is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Delete a routing

Deletes a routing.        

    DELETE /api/v1/routings/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | returns a `204 No Content` without actual delete of the routing.
