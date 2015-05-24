---
title: Scales
weight: 50
menu:
  main:
    parent: rest-api
---

# Scales

## List scales

Lists all scales without any pagination or filtering.

    GET /api/v1/scales

## Get a single scale

Lists all details for one specific scale.

    GET /api/v1/scales/:name

## Create scale

Creates a new scale.

    POST /api/v1/scales

Accepts JSON or YAML formatted scales. Set the `Content-Type` request header to `application/json` or `application/x-yaml` accordingly.    

## Update a scale

Updates the content of a specific scale.

    PUT /api/v1/scales/:name

## Delete a scale

Deletes a scale.        

    DELETE /api/v1/scales/:name