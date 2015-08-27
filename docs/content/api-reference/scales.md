---
title: Scales
weight: 50
menu:
  main:
    parent: api-reference
---

# Scales

Please check the notes on using [pagination](/documentation/api-reference/#pagination) and [json and yaml content types](/documentation/api-reference/#content-types) on how to effectively use the REST api.

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

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| -----------------:|
| validate_only | true or false     | false            | validates the scale and returns a `201 Created` if the scale is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Update a scale

Updates the content of a specific scale.

    PUT /api/v1/scales/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | validates the scale and returns a `200 OK` if the scale is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Delete a scale

Deletes a scale.        

    DELETE /api/v1/scales/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | returns a `204 No Content` without actual delete of the scale.
