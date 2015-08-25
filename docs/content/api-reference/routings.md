---
title: Routings
weight: 60
menu:
  main:
    parent: api-reference
---

# Routings

Please check the notes on using [pagination](/documentation/api-reference/#pagination) and [json and yaml content types](/documentation/api-reference/#content-types) on how to effectively use the REST api.

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

## Update a routing

Updates the content of a specific routing.

    PUT /api/v1/routings/:name

## Delete a routing

Deletes a routing.        

    DELETE /api/v1/routings/:name