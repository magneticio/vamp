---
title: Blueprints
weight: 30
menu:
  main:
    parent: api-reference
---

# Blueprints

## List blueprints

Lists all blueprints without any pagination or filtering.

    GET /api/v1/blueprints

## Get a single blueprint

Lists all details for one specific blueprint.

    GET /api/v1/blueprint/:name

## Create blueprint

Creates a new blueprint.

    POST /api/v1/blueprint

Accepts JSON or YAML formatted blueprints. Set the `Content-Type` request header to `application/json` or `application/x-yaml` accordingly.

| parameter     | options           | description      |
| ------------- |:-----------------:| ----------------:|
| validate_only | true or false     | validates the blueprint and returns a `201` if the blueprint is valid.This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON.     

### Update a blueprint

Updates the content of a specific blueprint.

    PUT /api/v1/blueprints/:name

## Delete a blueprint

Deletes a blueprint.        

    DELETE /api/v1/blueprints/:name