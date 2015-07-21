---
title: Overview
weight: 10
menu:
  main:
    parent: concepts-terminology
    identifier: concepts-terminology-overview
---
# Terminology & Concepts

Vamp has three basic entities you can work with:

-   **Breeds**: static artifacts that describe single services and their dependencies.  
-   **Blueprints**: blueprints are, well, blueprints! They describe how breeds work in runtime and what properties they should have.  
-   **Deployments**: running blueprints. You can have many of one blueprint and perform actions on them at runtime. Plus, you can turn any running deployment into a blueprint.  


> **Note**: Breeds and blueprints are static artefacts, deployment are not. This means any API actions on static artefacts are mostly synchronous. Actions on deployments are largely asychronous.

Of these three, the **blueprint** is the center of attention. Why?
 
-   Because you can inline breeds into blueprints.
-   Because you create deployments by `POST`-ing a blueprint.
-   Because they have information on scale, filters and SLA's.

This means you will probably start out just using blueprints.

## Inline or reference?

Vamp allows you to either inline or reference artefacts. This has a big impact on how complex or simple you can make any blueprint, breed or deployment. As an example, this is a totally valid blueprint:

```yaml
name: my_blueprint
  clusters:
    my_cluster:
      services:
        - my_breed
```

The breed is just **referenced** here. Vamp assumes that the breed called `my_breed` is available to load from its datastore at deploy time. This goes for all basic artefacts in Vamp: SLA's, routings, filters, escalations, etc.

But we could also define the whole breed **inline**. This means we store all details of the breed in the blueprint. Vamp will actually pick this up and create a separate breed for you once you have posted the blueprint to Vamp.

```yaml
name: my_blueprint
  clusters:
    my_cluster:
      services:
        - name: my_breed
            deployable: redis:0.1
            ...
```

## Eventual consistency

Be aware that **Vamp does not check references before deployment time**. Vamp has a very specific reason for this. For example:

- You can reference a breed in a blueprint that does not exist yet. 
- You can reference an SLA that you don't know the contents of.
- You can reference a variable that someone else should fill in.

The reason for this is that in typical larger companies, where **multiple teams are working together on a large project** you don't get all information you need all at the same time.

Vamp allows you to set placeholders, communicate with teams using simple references and gradually build up a complicated deployment.



