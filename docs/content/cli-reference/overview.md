---
title: Overview
weight: 10
url: /documentation/cli-reference/
menu:
  main:
    parent: cli-reference
    identifier: cli-overview
---

# CLI reference overview

Vamp's command line interface (CLI) can be used to perform basic actions against the Vamp API. The CLI was
primarily developed to work in continuous delivery situations. In these setups, the CLI takes care of automating (canary) releasing new artefacts to Vamp deployments and clusters.

## Simple commands

The basic commands of the CLI, like `list`, allow you to do exactly what you would expect:

```
> vamp list breeds
NAME                     DEPLOYABLE
catalog                  docker://zutherb/catalog-frontend
checkout                 docker://zutherb/monolithic-shop
product                  docker://zutherb/product-service
navigation               docker://magneticio/navigation-service:latest
cart                     docker://zutherb/cart-service
redis                    docker://redis:latest
mongodb                  docker://mongo:latest
monarch_front:0.1        docker://magneticio/monarch:0.1
monarch_front:0.2        docker://magneticio/monarch:0.2
monarch_backend:0.3      docker://magneticio/monarch:0.3
```

```
> vamp list deployments
NAME                                    CLUSTERS
1272c91b-ba29-4ad1-8d09-33cbaa8f6ac2    frontend, backend
```


## CI & Chaining

In more complex continuous integration situations you can use the CLI with the `--stdin` flag to chain a bunch of commands together. You could for instance:
* get a blueprint with `inspect`
* generate a new blueprint with `generate` while inserting a new breed
* merging it with an existing deployment with `merge`
* deploying the result with `deploy`

```
vamp inspect blueprint monarchs:1.0 | \
vamp generate blueprint --cluster frontend --breed frontend:0.2 --stdin | \
vamp merge --deployment $DEPLOYMENT --stdin | \
vamp deploy --deployment $DEPLOYMENT --stdin
```

Or even shorter, just use the target deployment as the basis for your new deployment. There is no need for the merge in this situation:
* get a blueprint from a running deployment with `inspect` and `--as-blueprint`
* generate a new blueprint with `generate` while inserting a new breed
* deploying the result with `deploy`

```
vamp inspect deployment $DEPLOYMENT --as-blueprint | \
vamp generate blueprint --cluster frontend --breed frontend:0.2 --stdin | \
vamp deploy --deployment $DEPLOYMENT --stdin
```

To start working with the CLI, please read [the installation instructions](/documentation/cli-reference/installation/) and start [exploring all the commands](/documentation/cli-reference/commands/)