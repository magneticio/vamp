---
title: Filters
weight: 70
menu:
  main:
    parent: api-reference
---

# Filters

Please check the notes on using [pagination](/documentation/api-reference/#pagination) and [json and yaml content types](/documentation/api-reference/#content-types) on how to effectively use the REST api.

## List filters

Lists all filters without any pagination or filtering.

    GET /api/v1/filters

## Get a single filter

Lists all details for one specific filter.

    GET /api/v1/filters/:name

## Create filter

Creates a new filter.

    POST /api/v1/filters

Accepts JSON or YAML formatted filters. Set the `Content-Type` request header to `application/json` or `application/x-yaml` accordingly.    

## Update a filter

Updates the content of a specific filter.

    PUT /api/v1/filters/:name

## Delete a filter

Deletes a filter.        

    DELETE /api/v1/filters/:name
