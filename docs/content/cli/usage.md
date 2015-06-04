---
title: Usage
weight: 20
---
menu:
  main:
    parent: cli

# Usage

VAMP has a command line interface (CLI) which can be used to perform some basic actions.

VAMP CLI needs to know where the VAMP Host is locate. The host can be specified as a command line option (--host) or via an environment variable (VAMP_HOST) 
Example:
{{% copyable %}}
```bash
export VAMP_HOST=http://192.168.59.103:8081
```
{{% /copyable %}}

The command line parameter will overrule the environment variable. With the exception of the `help` and the `version`, all commands require the Vamp host to be specified.

VAMP CLI support the following commands:

* [blueprints](#blueprints)                
* [breeds](#breeds)                         
* [clone-breed](#clone-breed)               
* [create-breed](#create-breed)  
* [deploy-breed](#deploy-breed)  
* [deployments](#deployments)            
* [escalations](#escalations)               
* [filters](#filters)                                     
* [help](#help)                              
* [info](#info)                              
* [inspect-breed](#inspect-breed)            
* [inspect-blueprint](#inspect-blueprint)   
* [inspect-deployment](#inspect-deployment)  
* [inspect-escalation](#inspect-escalation)  
* [inspect-filter](#inspect-filter)          
* [inspect-routing](#inspect-routing)        
* [inspect-scale](#inspect-scale)            
* [inspect-sla](#inspect-sla)                
* [remove-breed](#remove-breed)             
* [routings](#routings)                      
* [scales](#scales)                         
* [slas](#slas)                             
* [version](#version)  

For more details about a specific command, try `vamp COMMAND --help`
                     

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

Clones an existing breed

*Usage:* vamp clone-breed NAME --destination [--deployable] 

Parameter | purpose
-------------------
--destination   |   Name of the new breed
--deployable    |   Name of the deployable [Optional]
**Example:**
```bash
```

## <a name="create-breed"></a>Create Breed

Create a breed read from the specified filename. When no file name is supplied, stdin will be read.

*Usage:* vamp create-breed NAME [--file] 

Parameter | purpose
-------------------
  --file        |       Name of the yaml file [Optional]
**Example:**
```bash
```


## <a name="deploy-breed"></a>Deploy Breed

Deploys a breed into an existing deployment cluster

*Usage:* vamp deploy-breed NAME --deployment --cluster --routing --scale 

Parameter | purpose
-------------------
  --deployment   |      Name of the existing deployment
  --cluster      |      Name of the cluster within the deployment
  --routing      |      Name of the routing to apply [Optional]
  --scale        |      Name of the scale to apply [Optional]

**Example:**
```bash
```


## <a name="deployments"></a>Deployments

Shows a list of deployments

**Example:**
```bash
> vamp deployments
NAME                                    CLUSTERS
80b310eb-027e-44e8-b170-5bf004119ef4    sava
06e4ace5-41ce-46d7-b32d-01ee2c48f436    sava
a1e2a68b-295f-4c9b-bec5-64158d84cd00    sava, backend1, backend2
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
message: Hi, I'm Vamp! How are you?
jvm:
  operating_system:
    name: Mac OS X
    architecture: x86_64
    version: 10.9.5
    available_processors: 8.0
    system_load_average: 4.8095703125
  runtime:
    process: 12871@MacMatthijs-4.local
    virtual_machine_name: Java HotSpot(TM) 64-Bit Server VM
    virtual_machine_vendor: Oracle Corporation
    virtual_machine_version: 25.31-b07
    start_time: 1433415167162
    up_time: 1305115
...    
```

## <a name="inspect-breed"></a>Inspect Breed
Representation of a stored breed

*Usage:* vamp inspect-breed NAME --json 

| Parameter | purpose |
-----------------------
| --json    |  Output Json instead of Yaml[Optional]|

**Example:**
```bash
> vamp inspect-breed sava:1.0.0
name: sava:1.0.0
deployable: magneticio/sava:1.0.0
ports:
  port: 80/http
environment_variables: {}
constants: {}
dependencies: {}
```

## <a name="inspect-blueprint"></a>Inspect Blueprint
Representation of a stored blueprint

*Usage:* vamp inspect-blueprint NAME --json 

|Parameter | purpose
--------------------
  --json   |  Output Json instead of Yaml[Optional]
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
Representation of a stored escalation

*Usage:* vamp inspect-escalation NAME --json 

Parameter | purpose
-------------------
  --json  |  Output Json instead of Yaml[Optional]
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
CLI version: 0.7.7
```
