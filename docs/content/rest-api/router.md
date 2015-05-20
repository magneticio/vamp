---
title: Router
weight: 30
menu:
  main:
    parent: rest-api
---
# Router API

For more information on how to use the Vamp Router REST API check [the Github repo (https://github.com/magneticio/vamp-router)

**Routes**
```
- get all             : GET    /v1/routes
- create new          : POST   /v1/routes
- get by name         : GET    /v1/routes/{name}
- update by name      : PUT    /v1/routes/{name}
- delete by name      : DELETE /v1/routes/{name}
```

**Services**
```
- get all             : GET    /v1/routes/{name}/services
- create new          : POST   /v1/routes/{name}/services
- get by name         : GET    /v1/routes/{name}/services/{name}
- update by name      : PUT    /v1/routes/{name}/services/{name}
- delete by name      : DELETE /v1/routes/{name}/services/{name}
```

**Servers**
```
- get all             : GET    /v1/routes/{name}/services/{name}/servers
- create new          : POST   /v1/routes/{name}/services/{name}/servers
- get by name         : GET    /v1/routes/{name}/services/{name}/servers/{name}
- update by name      : PUT    /v1/routes/{name}/services/{name}/servers/{name}
- delete by name      : DELETE /v1/routes/{name}/services/{name}/servers/{name}
```

**Full configuration**
```
- get                 : GET    /v1/config
- update              : POST   /v1/config
```

**Debug Routes**
```
- runtime info        : GET   /v1/info
- stats reset         : GET   /v1/debug/reset
```