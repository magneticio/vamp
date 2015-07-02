---
title: Routing & filters
weight: 30
menu:
  main:
    parent: reference
---
# Routing & Filters

A routing defines a set of rules for routing traffic between different services within the same cluster.
Vamp allows you to determine this in two ways:

1. by setting a **weight** in the percentage of traffic.
2. by setting a **filter** condition to target specific traffic.

You can define routings inline in a blueprint or store them separately under a unique name and just use that name to reference them from a blueprint. 

Let's have a look at a simple, inline, routing. This would be used directly inside a blueprint. 

```yaml
---           
weight: 10  # Amount of traffic for this service in percents.
filters:    
  - condition: User-Agent = IOS
```

The example above could be reused by just giving it a name and storing it by using a `POST` request to the `/routings` endpoint.

```yaml
name: cool_routing   # Custom name, can be referenced later on.
weight: 10
filters: 
  - condition: user-agent = ios
  - really_cool_filter
```

> **Notice:** we added a filter named `really_cool_filter` here. This filter is actually a reference to a separately stored filter definition we stored under a unique name on the `/filters` endpoint.

## Defining weights

When defining weights, please make sure the total weight always adds up to 100%. This means that when doing a straight three-way split you give one service 34% as `33+33+34=100`. Vamp has to account for all traffic and 1% can be a lot in high volume environments.

## Defining filters
Creating filters is quite eas y. Checking Headers, Cookies, Hosts etc. is all possible. Under the hood, Vamp uses [Haproxy's ACL's](http://cbonte.github.io/haproxy-dconv/configuration-1.5.html#7.1) and you can use the exact ACL definition right in the blueprint in the `condition` field of a filter.

However, ACL's can be somewhat opaque and cryptic. That's why Vamp has a set of convenient "short codes"
to address common use cases. Currently, we support the following:

```
User-Agent = *string*
Host = *string*
Cookie *cookie name* Contains *string*
Has Cookie *cookie name*
Misses Cookie *cookie name*
Header *header name* Contains *string*
Has Header *header name*
Misses Header *header name*
```

We also allow simple negation of filter statements using the `!` operator on any shortcode with an `=`, i.e:

```
User-Agent != IOS
Host != www.google.com
```

Vamp is also quite flexible when it comes to the exact syntax. This means the following are all equivalent:

```
hdr_sub(user-agent) Android   # straight ACL
user-agent=Android            # lower case, no white space
user.agent=Android            # lower case, no white space, period instead of dash
User-Agent=Android            # upper case, no white space
user-agent = Android          # lower case, white space
```

Having multiple conditions in a filter is perfectly possible. In this case all filters are implicitly
"OR"-ed together, as in "if the first filter doesn't match, proceed to the next". For example, the following filter would first check whether the string "Chrome" exists in the User-Agent header of a
request. If that doesn't result in a match, it would check whether the request has the header 
"X-VAMP-MY-COOL-HEADER". So any request matching either condition would go to this service.

```yaml
---
routing:
  weight: 0
  filters:
    - condition: User-Agent = Chrome
    - condition: Has Header X-VAMP-MY-COOL-HEADER
```

Using a tool like [httpie](https://github.com/jakubroztocil/httpie) makes testing this a breeze.

    http GET http://10.26.184.254:9050/ X-VAMP-MY-COOL-HEADER:stuff

**Notice** we set the weight to 0 in the above example. This means per default no traffic is send to a service that has this routing. *Only* when one of the filter conditions is met any traffic will flow to this service.