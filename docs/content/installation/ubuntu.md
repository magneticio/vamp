---
title: Ubuntu
type: documentation
url: /installation/ubuntu
weight: 50
menu:
    main:
      parent: installation    
---

# Ubuntu

## Prerequisites

Before installing Vamp, make sure your system has the required software installed.

### Java 8

Vamp needs an OpenJDK or Oracle Java version of 1.8.0_45 or higher. Check the version with `java -version`.

**installing OpenJDK with apt**

{{% /copyable %}}
```bash
sudo apt-get install -y openjdk-8
```
{{% /copyable %}}

**installing Oracle JDK via apt**

{{% /copyable %}}
```bash
sudo apt-get install python-software-properties
sudo add-apt-repository ppa:webupd8team/java
sudo apt-get update
sudo apt-get install oracle-java8-installer
```
{{% /copyable %}}


## Add the Vamp RPM Repository

For Ubuntu 15.04 (Vivid), use the following commands:

{{% /copyable %}}
```bash
echo "deb https://dl.bintray.com/magnetic-io/systemd jessie main" | sudo tee -a /etc/apt/sources.list
sudo apt-get update
```
{{% /copyable %}}


For Ubuntu 14.04 (Trusty), use the following commands:

{{% /copyable %}}
```bash
echo "deb https://dl.bintray.com/magnetic-io/upstart trusty main" | sudo tee -a /etc/apt/sources.list
sudo apt-get update
```
{{% /copyable %}}


## Install Core

{{% /copyable %}}
```bash
sudo apt-get install -y vamp-core
```
{{% /copyable %}}

Check the `application.conf` file at `/usr/share/vamp-core/conf/` and change when needed.

Start the application with the command:

{{% /copyable %}}
```bash
sudo service vamp-core start
```
{{% /copyable %}}

## Install Pulse

{{% /copyable %}}
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

{{% /copyable %}}
```bash
sudo yum install -y vamp-router haproxy
```
{{% /copyable %}}

Start the application with the command:

```bash
sudo service vamp-router start
```

## Install CLI

{{% /copyable %}}
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
