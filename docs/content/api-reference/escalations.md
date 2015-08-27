---
title: Escalations
weight: 90
menu:
  main:
    parent: api-reference
---

# Escalations

## List escalations

Lists all escalations without any pagination or filtering.

    GET /api/v1/escalations

## Get a single escalation

Lists all details for one specific escalation.

    GET /api/v1/escalations/:name

## Create escalation

Creates a new escalation.

    POST /api/v1/escalations

Accepts JSON or YAML formatted escalations. Set the `Content-Type` request header to `application/json` or `application/x-yaml` accordingly.    

| parameter     | options           | default          | description       |
| ------------- |:-----------------:|:----------------:| -----------------:|
| validate_only | true or false     | false            | validates the escalation and returns a `201 Created` if the escalation is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Update an escalation

Updates the content of a specific escalation.

    PUT /api/v1/escalations/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | validates the escalation and returns a `200 OK` if the escalation is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Delete an escalation

Deletes an escalation.        

    DELETE /api/v1/escalations/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | returns a `204 No Content` without actual delete of the escalation.
