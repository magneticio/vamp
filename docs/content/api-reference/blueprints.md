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

| parameter         | options           | default          | description       |
| ----------------- |:-----------------:|:----------------:| -----------------:|
| expand_references | true or false     | false            | all breed references will be replaced (recursively) with full breed definitions. `400 Bad Request` in case some breeds are not yet fully defined.
| only_references   | true or false     | false            | all breeds will be replaced with their references.

## Get a single blueprint

Lists all details for one specific blueprint.

    GET /api/v1/blueprint/:name

| parameter         | options           | default          | description       |
| ----------------- |:-----------------:|:----------------:| -----------------:|
| expand_references | true or false     | false            | all breed references will be replaced (recursively) with full breed definitions. `400 Bad Request` in case some breeds are not yet fully defined.
| only_references   | true or false     | false            | all breeds will be replaced with their references.

## Create blueprint

Creates a new blueprint.

    POST /api/v1/blueprint

Accepts JSON or YAML formatted blueprints. Set the `Content-Type` request header to `application/json` or `application/x-yaml` accordingly.

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | validates the blueprint and returns a `201 Created` if the blueprint is valid.This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON.     

## Update a blueprint

Updates the content of a specific blueprint.

    PUT /api/v1/blueprints/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | validates the blueprint and returns a `200 OK` if the blueprint is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Delete a blueprint

Deletes a blueprint.        

    DELETE /api/v1/blueprints/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | returns a `204 No Content` without actual delete of the blueprint.
