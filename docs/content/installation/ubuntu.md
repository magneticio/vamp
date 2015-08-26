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

This package is generally available on Ubuntu version 14.10 or higher.

```bash
sudo apt-get install -y openjdk-8
```

**installing Oracle JDK via apt**

If you are using Ubuntu 14.04 or earlier, please use the Oracle installer. You will be prompted to accept the Oracle license agreement.

```bash
sudo apt-get install -y python-software-properties
sudo add-apt-repository -y ppa:webupd8team/java
sudo apt-get -y update
sudo apt-get install -y oracle-java8-installer
```

### HAproxy 1.5.x

Only Vamp Router needs HAProxy 1.5.x or higher.


```bash
sudo add-apt-repository -y ppa:vbernat/haproxy-1.5
sudo apt-get update
sudo apt-get install -y haproxy
```

## Add the Vamp APT Repository

For **Ubuntu 14.04** (Trusty), use the following commands:

```bash
echo "deb https://dl.bintray.com/magnetic-io/upstart trusty main" | sudo tee -a /etc/apt/sources.list
curl https://bintray.com/user/downloadSubjectPublicKey?username=bintray | sudo apt-key add -
sudo apt-get update
```

For **Ubuntu 15.04** (Vivid), use the following commands:

```bash
echo "deb https://dl.bintray.com/magnetic-io/systemd jessie main" | sudo tee -a /etc/apt/sources.list
curl https://bintray.com/user/downloadSubjectPublicKey?username=bintray | sudo apt-key add -
sudo apt-get update
```

## Install Core


```bash
sudo apt-get install -y vamp-core
```


Check the `application.conf` file at `/usr/share/vamp-core/conf/` and change when needed.

After making changes, restart the application with the command:


```bash
sudo service vamp-core restart
```


## Install Pulse


```bash
sudo apt-get install -y vamp-pulse
```


Check the `application.conf` file at `/usr/share/vamp-pulse/conf/` and change when needed.

After making changes, restart the application with the command:

```bash
sudo service vamp-pulse restart
```

## Install Router


```bash
sudo apt-get install -y vamp-router
```

Vamp Router has some issues with Systemd and needs to have haproxy at `/usr/sbin/haproxy`. To fix any issues for now,
you can just start Vamp Router directly and provide it with the correct haproxy path, i.e:

```bash
/usr/share/vamp-router/vamp-router --binary=/usr/local/sbin/haproxy
```

## Install CLI


```bash
sudo apt-get install -y vamp-cli
```


Type `vamp version` to check if Vamp Cli has been properly installed. 
Now export the location of the Vamp Core host and check if the CLI can talk to Vamp Core, i.e:

```bash
export VAMP_HOST=http://localhost:8080
vamp info
```
