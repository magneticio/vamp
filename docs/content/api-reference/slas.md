---
title: Slas
weight: 80
menu:
  main:
    parent: api-reference
---

# SLA's

Please check the notes on using [pagination](/documentation/api-reference/#pagination) and [json and yaml content types](/documentation/api-reference/#content-types) on how to effectively use the REST api.

## List SLA's

Lists all slas without any pagination or filtering.

    GET /api/v1/slas

## Get a single SLA

Lists all details for one specific breed.

    GET /api/v1/slas/:name

## Create an SLA

Creates a new SLA

    POST /api/v1/slas   

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| -----------------:|
| validate_only | true or false     | false            | validates the SLA and returns a `201 Created` if the SLA is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Update an SLA

Updates the content of a specific SLA.

    PUT /api/v1/slas/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | validates the SLA and returns a `200 OK` if the SLA is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Delete an SLA

Deletes an SLA.        

    DELETE /api/v1/slas/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | returns a `204 No Content` without actual delete of the SLA.


