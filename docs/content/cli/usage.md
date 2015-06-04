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
-  [blueprints](#blueprints)                
-  [breeds](#breeds)                         
-  [clone-breed](#clone-breed)               
-  [create-breed](#create-breed)            
-  [escalations](#escalations)               
-  [filters](#filters)                       
-  [deployments](#deployments)               
-  [help](#help)                              
-  [info](#info)                              
-  [inspect-breed](#inspect-breed)            
-  [inspect-blueprint](#inspect-blueprint)   
-  [inspect-deployment](#inspect-deployment)  
-  [inspect-escalation](#inspect-escalation)  
-  [inspect-filter](#inspect-filter)          
-  [inspect-routing](#inspect-routing)        
-  [inspect-scale](#inspect-scale)            
-  [inspect-sla](#inspect-sla)                
-  [remove-breed](#remove-breed)             
-  [routings](#routings)                      
-  [scales](#scales)                         
-  [slas](#slas)                             
-  [version](#version)                       

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
> vamp deployments
```

## <a name="escalations"></a>Escalations

Shows a list of escalations

**Example:**
```bash
> vamp escalations
```

## <a name="filter"></a>Filters

Shows a list of filters

**Example:**
```bash
> vamp filters
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
> vamp info
```

## <a name="inspect-breed"></a>Inspect Breed


**Example:**
```bash
> vamp inspect-breed abc
```

## <a name="inspect-blueprint"></a>Inspect Blueprint

**Example:**
```bash
> vamp inspect-blueprint def
```

## <a name="inspect-deployment"></a>Inspect Deployment

**Example:**
```bash
> vamp inspect-deployment abc
```

## <a name="inspect-escalation"></a>Inspect Escalation

**Example:**
```bash
> vamp inspect-escalation fgh
```

## <a name="inspect-filter"></a>Inspect Filter

**Example:**
```bash
> vamp inspect-filter klm
```

## <a name="inspect-routing"></a>Inspect Routing

**Example:**
```bash
> vamp inspect-routing nip
```

## <a name="inspect-scale"></a>Inspect Scale

**Example:**
```bash
> vamp inspect-scale few
```

## <a name="inspect-sla"></a>Inspect SLA

**Example:**
```bash
> vamp inspect-sla tgb
```

## <a name="remove-breed"></a>Remove Breed

**Example:**
```bash
> vamp remove-breed fds
```

## <a name="routings"></a>Routings

Shows a list of routings
**Example:**
```bash
> vamp routings
```
## <a name="scales"></a>Scales

Shows a list of scales

**Example:**
```bash
> vamp scales
```

## <a name="slas"></a>Slas

Shows a list of slas

**Example:**
```bash
> vamp slas
```

## <a name="version"></a>Version

Displays the Vamp CLI version information 

**Example:**
```bash
> vamp version
```
