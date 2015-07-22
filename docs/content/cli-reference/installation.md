---
title: Installation
weight: 20
menu:
  main:
    parent: cli-reference
    identifier: cli-installation
---


# Installation

Vamp requires Java version 8 to be installed. 

To verify if the correct Java version has been installed, type 
```
java -version
``` 

It should report back the version as 1.8
```bash
java version "1.8.0_45"
Java(TM) SE Runtime Environment (build 1.8.0_45-b14)
Java HotSpot(TM) 64-Bit Server VM (build 25.45-b02, mixed mode)
```

For further installation steps, please check the section appropriate for your platform:

* [Debian](#Debian)
* [Red Hat](#RedHat)
* [OSX](#OSX)
* [Windows](#Windows)

##Debian
A detailed explanation on how to install Java 8 on Debian can be found here: 
http://www.webupd8.org/2014/03/how-to-install-oracle-java-8-in-debian.html

To add the Vamp Debian repository & install the vamp-cli package:
{{% copyable %}}
```bash
echo "deb https://dl.bintray.com/magnetic-io/debian wheezy main" | sudo tee -a /etc/apt/sources.list
sudo apt-get update
sudo apt-get install vamp-cli
```
{{% /copyable %}}

Now you can run the Vamp CLI, by typing 
```
vamp
```

## RedHat

To resolve RPM artifacts, get the generated .repo file from Bintray:

{{% copyable %}}
```bash
wget https://bintray.com/magnetic-io/rpm/rpm -O bintray-magnetic-io-rpm.repo
```
{{% copyable %}}

Next, install Vamp CLI via yum:
{{% copyable %}}
```bash
yum update
yum install vamp
```
{{% copyable %}}

Now you can run the Vamp CLI, by typing 
```
vamp
```

## OSX

To install the Vamp CLI, simple add a brew tap and install:

{{% /copyable %}}
```bash
brew tap magneticio/vamp
brew install vamp
```
{{% /copyable %}}

Now you can run the Vamp CLI, by typing 
```
vamp
```

Updating Vamp CLI to the latest version can be done using these two commands:

{{% /copyable %}}
```bash
brew update
brew upgrade vamp
```
{{% /copyable %}}

## Windows

We currently don't have a installer specifically for Windows, but the manual install is pretty easy.

After confirming you've got the correct Java version installed, head over to our Bintray download section to get the latest Vamp CLI release.
It is located at https://dl.bintray.com/magnetic-io/downloads/vamp-cli/

* Download the release zip file
* Unzip it anywhere you'd like, e.g. `C:\Program Files\` 
* Inside the extracted Vamp CLI package is a `bin` directory. Add it to your PATH statement
* Open een CMD window and type `vamp`

