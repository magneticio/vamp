---
title: Escalations
weight: 90
menu:
  main:
    parent: rest-api
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

## Update an escalation

Updates the content of a specific escalation.

    PUT /api/v1/escalations/:name

## Delete an escalation

Deletes an escalation.        

    DELETE /api/v1/escalations/:name