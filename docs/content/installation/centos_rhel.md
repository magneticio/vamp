---
title: Centos & RHEL
type: documentation
url: /installation/centos_rhel
weight: 40
menu:
    main:
      parent: installation
---

# Centos & RHEL

## Prerequisites

Before installing, make sure your system has the required software installed.

### Java 8

Vamp needs an OpenJDK or Oracle Java version of 1.8.0_40 or higher. Check the version with `java -version`.

**installing OpenJDK with yum**

```bash
sudo yum install -y java-1.8.0-openjdk
```


**Installing Oracle manually**

For detail on how to install Java 8, check the following page: http://tecadmin.net/install-java-8-on-centos-rhel-and-fedora/

### HAproxy 1.5.x

> **Note:** Only Vamp Router needs HAProxy 1.5.x or higher.  

Either install it using your package manager or build it from source:

```bash
curl -OL http://www.haproxy.org/download/1.5/src/haproxy-1.5.12.tar.gz
tar -xzvf haproxy-1.5.12.tar.gz
cd haproxy-1.5.12
sudo yum -y install gcc
sudo make install
```

Vamp Router expect the binary executable to be at in `/usr/sbin/haproxy`. If this is not the case, you can either make a symlink to it, or just start Vamp Router with the `--binary` flag and point to it, i.e:

```bash
/usr/share/vamp-router/vamp-router --binary=/usr/local/sbin/haproxy
```

## Add the Vamp RPM Repository

Use the following command to get a generated `.repo` file

```bash
 sudo curl -o /etc/yum.repos.d/bintray-magnetic-io-rpm.repo https://bintray.com/magnetic-io/rpm/rpm
```


## Install Core

```bash
sudo yum install -y vamp-core
```


Check the `application.conf` file at `/usr/share/vamp-core/conf/` and change when needed.

Start the application with the command:

```bash
sudo service vamp-core start
```


## Install Pulse

```bash
sudo yum install -y vamp-pulse
```


Check the `application.conf` file at `/usr/share/vamp-pulse/conf/` and change when needed.

Start the application with the command:

```bash
sudo service vamp-pulse start
```


## Install Router

```bash
sudo yum install -y vamp-router
```


Start the application with the command:

```bash
sudo service vamp-router start
```

Vamp Router has some issues with Systemd and needs to have haproxy at `/usr/sbin/haproxy`. To fix any issues for now,
you can just start Vamp Router directly and provide it with the correct haproxy path, i.e:

```bash
/usr/share/vamp-router/vamp-router --binary=/usr/local/sbin/haproxy
```

## Install CLI

```bash
sudo yum install -y vamp-cli
```


Type `vamp version` to check if Vamp Cli has been properly installed. 
Now export the location of the Vamp Core host and check if the CLI can talk to Vamp Core, i.e:

```bash
export VAMP_HOST=http://localhost:8080
vamp info
```

