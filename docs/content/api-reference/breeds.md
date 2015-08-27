---
title: Breeds
weight: 20
menu:
  main:
    parent: api-reference
---

# Breeds

## List breeds

Lists all breeds without any pagination or filtering.

    GET /api/v1/breeds

| parameter         | options           | default          | description       |
| ----------------- |:-----------------:|:----------------:| -----------------:|
| expand_references | true or false     | false            | all breed dependencies will be replaced (recursively) with full breed definitions. `400 Bad Request` in case some dependencies are not yet fully defined.
| only_references   | true or false     | false            | all full breed dependencies will be replaced with their references.

## Get a single breed

Lists all details for one specific breed.

    GET /api/v1/breeds/:name

| parameter         | options           | default          | description       |
| ----------------- |:-----------------:|:----------------:| -----------------:|
| expand_references | true or false     | false            | all breed dependencies will be replaced (recursively) with full breed definitions. `400 Bad Request` in case some dependencies are not yet fully defined.
| only_references   | true or false     | false            | all full breed dependencies will be replaced with their references.

## Create breed

Creates a new breed

    POST /api/v1/breeds

Accepts JSON or YAML formatted breeds. Set the `Content-Type` request header to `application/json` or `application/x-yaml` accordingly.    

| parameter     | options           | default          | description       |
| ------------- |:-----------------:|:----------------:| -----------------:|
| validate_only | true or false     | false            | validates the breed and returns a `201 Created` if the breed is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Update a breed

Updates the content of a specific breed.

    PUT /api/v1/breeds/:name

| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | validates the breed and returns a `200 OK` if the breed is valid. This can be used together with the header `Accept: application/x-yaml` to return the result in YAML format instead of the default JSON. 

## Delete a breed

Deletes a breed.        

    DELETE /api/v1/breeds/:name
    
| parameter     | options           | default          | description      |
| ------------- |:-----------------:|:----------------:| ----------------:|
| validate_only | true or false     | false            | returns a `204 No Content` without actual delete of the breed.
