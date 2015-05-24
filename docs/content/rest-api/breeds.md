---
title: Breeds
weight: 20
menu:
  main:
    parent: rest-api
---

# Breeds

## List breeds

Lists all breeds without any pagination or filtering.

    GET /api/v1/breeds

## Get a single breed

Lists all details for one specific breed.

    GET /api/v1/breeds/:name

## Create breed

Creates a new breed

    POST /api/v1/breeds

Accepts JSON or YAML formatted breeds. Set the `Content-Type` request header to `application/json` or `application/x-yaml` accordingly.    

## Update a breed

Updates the content of a specific breed.

    PUT /api/v1/breeds/:name

## Delete a breed

Deletes a breed.        

    DELETE /api/v1/breeds/:name