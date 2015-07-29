---
title: Debian
type: documentation
url: /installation/debian
weight: 60
menu:
    main:
      parent: installation
---

# Debian

## Prerequisites

Before installing, make sure your system has the required software installed.

### Java 8

Vamp needs an OpenJDK or Oracle Java version of 1.8.0_40 or higher. Check the version with `java -version`.

**Installing Java 8 Oracle**

For details on how to install Java 8, check the following page: http://www.webupd8.org/2014/03/how-to-install-oracle-java-8-in-debian.html

## Add the Vamp Debian Repository

Use the following commands to add the Vamp repo"

```bash
echo "deb https://dl.bintray.com/magnetic-io/debian wheezy main" | sudo tee -a /etc/apt/sources.list
sudo apt-get update
```

## Install CLI

```bash
sudo apt-get install vamp-cli
```
