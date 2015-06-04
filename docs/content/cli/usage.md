---
title: Usage
weight: 20
---
menu:
  main:
    parent: cli

# Usage

VAMP has a command line interface (CLI) which can be used to perform some basic actions.

VAMP CLI can connect to any Vamp Host, by specifying either a environment variable (VAMP_HOST) or adding a command line parameter (--host). In all the examples, the enviromment variable is used. The command line parameter will overrule the environment variable

Example:
{{% copyable %}}
```bash
export VAMP_HOST=http://192.168.59.103:8081
```
{{% /copyable %}}


VAMP CLI support the following commands:

|Command | Description |
|--------|-------------|
|  [blueprints](#blueprints)                  | List of blueprints |
|  [breeds](#breeds)                          | List of breeds |
|  [clone-breed](#clone-breed)                | Clone a breed |
|  [create-breed](#create-breed)              | Create a breed |
|  [escalations](#escalations)                | List of escalations |
|  [filters](#filters)                        | List of filters |
|  [deployments](#deployments)                | List of deployments |
|  [help](#help)                              | Help message |
|  [info](#info)                              | Information from Vamp Core |
|  [inspect-breed](#inspect-breed)            | Return details of the specified breed|
|  [inspect-blueprint](#inspect-blueprint)    | Return details of the specified blueprint |
|  [inspect-deployment](#inspect-deployment)  | Return details of the specified deployment |
|  [inspect-escalation](#inspect-escalation)  | Return details of the specified escalation |
|  [inspect-filter](#inspect-filter)          | Return details of the specified filter |
|  [inspect-routing](#inspect-routing)        | Return details of the specified routing |
|  [inspect-scale](#inspect-scale)            | Return details of the specified scale |
|  [inspect-sla](#inspect-sla)                | Return details of the specified sla |
|  [remove-breed](#remove-breed)              | Removes a breed |
|  [routings](#routings)                      | List of routings |
|  [scales](#scales)                          | List of scales |
|  [slas](#slas)                              | List of slas |
|  [version](#version)                        | Show version of the VAMP CLI client |

## <a name="blueprints"></a>Blueprints
Shows a list of blueprints

**Example:**
```bash
> vamp blueprints
NAME                                    ENDPOINTS
monarch:1.0                             monarchs.port -> 9050
sava:1.0                                sava.port -> 9050
sava:1.2                                sava.port -> 9050
```

## <a name="breeds"></a>Breeds

Shows a list of breeds

**Example:**
```bash
> vamp breeds
NAME                     DEPLOYABLE
monarch                  magneticio/monarch:latest
sava:1.0.0               magneticio/sava:1.0.0
sava-frontend:1.2.0      magneticio/sava-frontend:1.2.0
sava-backend1:1.2.0      magneticio/sava-backend1:1.2.0
sava-backend2:1.2.0      magneticio/sava-backend2:1.2.0
```

## <a name="clone-breed"></a>Clone Breed

**Example:**
```bash
```

## <a name="create-breed"></a>Create Breed

**Example:**
```bash
```

## <a name="deployments"></a>Deployments

Shows a list of deployments

**Example:**
```bash
```

## <a name="escalations"></a>Escalations

Shows a list of escalations

**Example:**
```bash
```

## <a name="filter"></a>Filters

Shows a list of filters

**Example:**
```bash
```

## <a name="help"></a>Help
```bash
> vamp help
Usage: vamp COMMAND [args..]

Commands:
  blueprints          List of blueprints
  breeds              List of breeds
  clone-breed         Clone a breed
  create-breed        Create a breed
  escalations         List of escalations
  filters             List of filters
  deployments         List of deployments
  help                This message
  info                Information from Vamp Core
  inspect-breed       Return details of the specified breed
  inspect-blueprint   Return details of the specified blueprint
  inspect-deployment  Return details of the specified deployment
  inspect-escalation  Return details of the specified escalation
  inspect-filter      Return details of the specified filter
  inspect-routing     Return details of the specified routing
  inspect-scale       Return details of the specified scale
  inspect-sla         Return details of the specified sla
  remove-breed        Removes a breed
  routings            List of routings
  scales              List of scales
  slas                List of slas
  version             Show version of the VAMP CLI client

Run vamp COMMMAND --help  for additional help about the different command options
```



## <a name="info"></a>Info

Displays the Vamp Info message

**Example:**
```bash
```

## <a name="inspect-breed"></a>Inspect Breed


**Example:**
```bash
```

## <a name="inspect-blueprint"></a>Inspect Blueprint

**Example:**
```bash
```

## <a name="inspect-deployment"></a>Inspect Deployment

**Example:**
```bash
```

## <a name="inspect-escalation"></a>Inspect Escalation

**Example:**
```bash
```

## <a name="inspect-filter"></a>Inspect Filter

**Example:**
```bash
```

## <a name="inspect-routing"></a>Inspect Routing

**Example:**
```bash
```

## <a name="inspect-scale"></a>Inspect Scale

**Example:**
```bash
```

## <a name="inspect-sla"></a>Inspect SLA

**Example:**
```bash
```

## <a name="remove-breed"></a>Remove Breed

**Example:**
```bash
```

## <a name="routings"></a>Routings

Shows a list of routings
**Example:**
```bash
```
## <a name="scales"></a>Scales

Shows a list of scales

**Example:**
```bash
```

## <a name="slas"></a>Slas

Shows a list of slas

**Example:**
```bash
```

## <a name="version"></a>Version

Displays the Vamp CLI version information 

**Example:**
```bash
```
