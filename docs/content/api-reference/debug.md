---
title: Debug
weight: 1000
menu:
  main:
    parent: api-reference
---

# Debug endpoints

Vamp provides a set of API endpoints that help with getting general health/configuration status and some explicit debugging functions.

**Note:** use these endpoints at your own risk. The `/reset` endpoint for instance completely resets the state of Vamp. Some of these endpoints maybe removed at a later stage.

## Get runtime and config info

Lists information about Vamp's JVM environment and runtime status. Also lists info on the configured persistence layer, router, pulse and container driver status.

## Force sync

Forces Vamp to perform a synchronization cycle, regardless of the configured default interval.

	GET /api/v1/sync
	
## Force SLA check	

Forces Vamp to perform an SLA check, regardless of the configured default interval.

	GET /api/v1/sla

## Force escalation	

Forces Vamp to perform an escalation check, regardless of the configured default interval.

	GET /api/v1/escalation

## Hard reset

Stops and erases	all running deployment. Use with great caution!!

	GET /api/v1/reset
