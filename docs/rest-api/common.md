# Common API principles

Each Vamp service (Core, Router and Pulse) has its own REST API. All API's stick to a set of common principles:

* API request & responses are in JSON format. Core requests are possible in YAML format.
* Simple create, read, update & delete (CRUD) operations on resources.
* Create & Delete operations are idempotent: sending the second request with the same content will not result to an error response (4xx).
* An update will fail (4xx) if a resource does not exist.
* A successful create operation has status code 201 (Created) and the response body contains the created resource (JSON).
* A successful update operation has status code 200 (OK) or 202 (Accepted) and the response body contains the updated resource (JSON).
* A successful delete operation has status code 204 (No Content) or 202 (Accepted) with an empty response body.