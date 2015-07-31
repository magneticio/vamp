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

Before installing Vamp, make sure your system has the required software installed.

### Java 8

Vamp needs an OpenJDK or Oracle Java version of 1.8.0_45 or higher. Check the version with `java -version`.

**installing OpenJDK with apt**

{{% copyable %}}
```bash
sudo apt-get install -y openjdk-8
```
{{% /copyable %}}

**installing Oracle manually**

For detail on how to install Java 8, check the following page: http://www.webupd8.org/2014/03/how-to-install-oracle-java-8-in-debian.html

### HAproxy 1.5.x

Only Vamp Router needs HAProxy 1.5.x or higher.

{{% copyable %}}
```bash
sudo apt-get install -y haproxy
```
{{% /copyable %}}

## Add the Vamp RPM Repository

For Debian 8 (Jessie), use the following commands:

{{% copyable %}}
```bash
echo "deb https://dl.bintray.com/magnetic-io/systemd jessie main" | sudo tee -a /etc/apt/sources.list
sudo apt-get update
```
{{% /copyable %}}


For Debian 7 (Wheezy), use the following commands:

{{% copyable %}}
```bash
echo "deb https://dl.bintray.com/magnetic-io/systemv wheezy main" | sudo tee -a /etc/apt/sources.list
sudo apt-get update
```
{{% /copyable %}}


## Install Core

{{% copyable %}}
```bash
sudo apt-get install -y vamp-core
```
{{% /copyable %}}

Check the `application.conf` file at `/usr/share/vamp-core/conf/` and change when needed.

Start the application with the command:

{{% copyable %}}
```bash
sudo service vamp-core start
```
{{% /copyable %}}

## Install Pulse

{{% copyable %}}
```bash
sudo apt-getinstall -y vamp-pulse
```
{{% /copyable %}}

Check the `application.conf` file at `/usr/share/vamp-pulse/conf/` and change when needed.

Start the application with the command:

```bash
sudo service vamp-pulse start
```

## Install Router

{{% copyable %}}
```bash
sudo apt-get install -y vamp-router haproxy
```
{{% /copyable %}}

Start the application with the command:

```bash
sudo service vamp-router start
```

## Install CLI

{{% copyable %}}
```bash
sudo apt-get install -y vamp-cli
```
{{% /copyable %}}

Type `vamp version` to check if Vamp Cli has been properly installed. 
Now export the location of the Vamp Core host and check if the CLI can talk to Vamp Core, i.e:

```bash
export VAMP_HOST=http://localhost:8080
vamp info
```
