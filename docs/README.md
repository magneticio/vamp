# Writing & updating Vamp docs

When working on the docs, please use the following conventions and stick to the style guide:

## File names

Use all lowercase, no special characters and connect words using `-` characters, i.e: `sla-and-escalations.md`
All files that are used in the http://vamp.io site are under the `content` folder.

## Front matter

Our static site generator [Hugo](http://gohugo.io) requires front matter to be set at the start of a page.
This links the page to correct category, gives it its html title and determines the order of the pages from top to bottom.
We use the YAML notation for this, i.e:

```
---
title: Breeds
weight: 20
menu:
  main:
    parent: reference
---
```




# Style guide

# Header 1

Use links, like this one to [YAML](http://en.wikipedia.org/wiki/YAML).  

To alert people to stuff, or show exceptions or other things that deviate from the ordinary use the
standard `blockquote`:
```
> my important message
```

## Header 2

Some Yaml code, pretty printed with the following classes ````yaml`

```yaml
## Unique designator (name) consist of group, artifact and version.
group: tutum
artifact: mysql
version: 5.5

# Traits (parameters) of the breed (input/output).
traits:
  -
    ## Name of the trait.
    name: port
    value: 3306
    ## PORT, VOLUME, VARIABLE
    type: PORT
    ## IN, OUT
    direction: OUT
  -
    name: password
    type: VARIABLE
    direction: IN
    ## Alias is the trait name expected by the breed.
    ## This is very common if we deal with 3rd party breed.
    ## In this example with an existing docker image
    ## from public Docker repository.
    alias: MYSQL_PASS
```

If you don't want any code highlighting, just use ````` tags without any extra classes.

```
$ java -version
$ java -DMem=20001 -jar ../mesk/havav-war
```

You can also add a copy-to-clipboard button when you have some code people will need to copy & paste.
This is handy in tutorials and examples. Just wrap a standard ````` tag with `{{bla% copyable %}}`
short codes. Of course, leave out the "bla":

{{% copyable %}}
```
java --version
```
{{% /copyable %}}

### Header 3

We have some straight inline code `like this`

## Blueprints

Blueprints are execution plans - they describe how you system should look like at the runtime. All dependency availability and parameter values will be resolved at the deployment time. 

{{% copyable %}}
```yaml
group: vamp
artifact: wordpress_stackable
version: 1

## Gates between the blueprint and the rest of the world.
## Could be specified by URI, for example: vamp://<custom>
## $PORT is shortened for default vamp assigned IO port.
gates:
  demo.port: $PORT
  
## Setting up all environment variables (all traits: ports, volumes etc.)
environment:
  ## In general value can be provided by URI, with predefined or 
  ## custom resolver implementation: vamp://<custom>
  db.password: secret

filials:

  # Application/services.
  demo:
    breed: tutum:wordpress-stackable:latest
    
    ## Available 'space' for the service: 
    ## cpu & memory per instance and total number of deployed instances.
    ## Could be specified by URI, for example: vamp://computing/large
    ## Based on the schema, system will resolved the actual 
    ## niche at deployment time.
    niche:
      cpu: 1
      memory: 1024
      instances: 2

  ## Database.
  db:
    breed: tutum:mysql:5.5

    niche:
      cpu: 1
      memory: 1024
      instances: 1
```
{{% /copyable %}}


### Images

You reference the image using the following code in your
markdown file: `![](/img/scaling_poc.png)`. The actual file should be in the `/static/img/` directory.
![](/img/service_chain.png)



And here is some Json:

```json
    {
        "key" : "value",
        "key1" : 132
    } 
```

Here is some Scala code

```scala
package io.magnetic.vamp_core.rest_api

import io.magnetic.vamp_core.model.Artifact
import io.magnetic.vamp_core.rest_api.notification.{InconsistentResourceName, RestApiNotificationProvider}
import io.magnetic.vamp_core.rest_api.util.ExecutionContextProvider

import scala.collection.mutable
import scala.concurrent.Future

trait ResourceStoreProvider {

  val resourceStore: ResourceStore

  trait ResourceStore {

    def all: Future[List[Artifact]]

    def find(name: String): Future[Option[Artifact]]

    def create(resource: Artifact): Future[Option[Artifact]]

    def update(name: String, resource: Artifact): Future[Option[Artifact]]

    def delete(name: String): Future[Option[Artifact]]
  }

}

trait InMemoryResourceStoreProvider extends ResourceStoreProvider with RestApiNotificationProvider {
  this: ExecutionContextProvider =>

  val resourceStore: ResourceStore = new InMemoryResourceStore()

  private class InMemoryResourceStore extends ResourceStore {

    val store: mutable.Map[String, Artifact] = new mutable.HashMap()

    def all: Future[List[Artifact]] = Future {
      store.values.toList
    }

    def find(name: String): Future[Option[Artifact]] = Future {
      store.get(name)
    }

    def create(resource: Artifact): Future[Option[Artifact]] = Future {
      store.put(resource.name, resource)
      Some(resource)
    }

    def update(name: String, resource: Artifact): Future[Option[Artifact]] = Future {
      if (name != resource.name)
        error(InconsistentResourceName(name, resource.name))

      store.put(resource.name, resource)
    }

    def delete(name: String): Future[Option[Artifact]] = Future {
      store.remove(name)
    }
  }

}
```