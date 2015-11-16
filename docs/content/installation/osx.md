---
title: OSX
type: documentation
url: /installation/osx
weight: 70
menu:
    main:
      parent: installation
---

# Mac OSX

Currently, we do not have native installers for running Vamp's server component on OSX. For the CLI we have Homebrew support.

## Prerequisites

Before installing, make sure your system has the required software installed.

### Java 8

Vamp needs an OpenJDK or Oracle Java version of 1.8.0_40 or higher. Check the version with `java -version`.

## Install CLI

Use Homebrew to install the Vamp CLI. Simply add a brew tap and install:


```bash
brew tap magneticio/vamp
brew install vamp
```


Type `vamp version` to check if Vamp Cli has been properly installed. 
Now export the location of the Vamp Core host and check if the CLI can talk to Vamp Core, i.e:
 

```bash
export VAMP_HOST=http://localhost:8080
vamp info
```

