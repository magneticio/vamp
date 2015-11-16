---
title: Referencing artefacts
weight: 80
menu:
  main:
    parent: using-vamp
---

# Referencing artefacts

With any artefact, Vamp allows you to either use an inline notation or reference the artefact by name. For references, you use the `reference` keyword or its shorter version `ref`. Think of it like either using actual values or pointers to a value, i.e:

**inline notation**

```yaml
---
name: my_blueprint
  clusters:
    my_cluster:
      services:
        breed:
          name: my_breed
          deployable: registry.magnetic.io/app:1.0
        scale:
          cpu: 2
          memory: 1024
          instances: 4
```
**reference**

```yaml
---
name: my_blueprint
  clusters:
    my_cluster:
      services:
        breed:
          reference: my_breed
        scale:
          reference: medium  
```

This has a big impact on how complex or simple you can make any blueprint, breed or deployment. It also impacts how much knowledge you need to have of all the different artefacts that are used in a typical deployment or blueprint.

In the second example, Vamp assumes that the breed called `my_breed` is available to load from its datastore at deploy time. This goes for all basic artefacts in Vamp: SLA's, routings, filters, escalations, etc.

# How will I use this?

Starting with Vamp, you will probably start with inline artefacts. You have everything in one place and you can directly see what properties each artefact has. Later, you can start specializing and basically build a library of often used architectural components. Let's look at some use cases:

## Example 1: a library of containers

You have a Redis container you have tweaked and setup exactly the way you want it. You want to use that exact container in all your environments (dev, test, prod etc.). You put all that info inside a breed and either use the UI or API to save it, i.e:

`POST /api/v1/breeds`

```yaml
---
name: redis:1.0
deployable: docker://redis
ports: 6379/tcp
```

Now you can just use the `ref: redis:1.0` notation anywhere in a blueprint.

## Example 2: fixing scales per environment

You want to have a predetermined set of scales you can use per deployment per environment. For instance, a "medium_production" should be something else than a "medium_test".

`POST /api/v1/scales`

```yaml
---
name: medium_prod
cpu: 2
memory: 4096
instances: 3
```

```yaml
---
name: medium_test
cpu: 0.5
memory: 1024
instances: 1
```

now you can use the `ref: medium_test` notation anywhere a `scale` type is required.

## Example 3: reusing a complex filter

You have created a complex filter to target a specific part of your traffic. In this case users with a cookie that have a specific session variable set in that cookie. You want to use that filter now and then to do some testing. You can just save that filter to the `/filters`

```POST /api/v1/filters```

```yaml
---
name: filter_empty_shopping_cart
condition: Cookie SHOPSESSION Contains shopping_basket_items=0 
```

