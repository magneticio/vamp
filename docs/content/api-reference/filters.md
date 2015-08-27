---
title: Filters
weight: 70
menu:
  main:
    parent: api-reference
---

# Filters

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

| parameter     | options           | default          | description       |
| ------------- |:-----------------:|:----------------:| -----------------:|
| validate_only | true or false     | false            | validates the escalation and returns a `201 Created` if the escalation is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Update a filter

Updates the content of a specific filter.

    PUT /api/v1/filters/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | validates the escalation and returns a `200 OK` if the escalation is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Delete a filter

Deletes a filter.        

    DELETE /api/v1/filters/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | returns a `204 No Content` without actual delete of the escalation.
