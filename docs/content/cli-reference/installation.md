---
title: Installation & config
weight: 20
menu:
  main:
    parent: cli-reference
    identifier: cli-installation
---


# Installation & configuration

Please check the appropriate [installation section](/installation) for your platform for details on how to install the Vamp CLI. 

* [Centos/RHEL](/installation/centos_rhel/#install-cli)
* [Debian](/installation/debian/#install-cli)
* [Ubuntu](/installation/ubuntu/#install-cli)
* [OSX](/installation/osx#/#install-cli)

After installation, set Vamp Core's host location. This location can be specified as a command line option (`--host`)

```bash
vamp list breeds --host=http://192.168.59.103:8080
```

...or via the environment variable `VAMP_HOST`
```bash
export VAMP_HOST=http://192.168.59.103:8080
```

## Windows

We currently don't have a installer specifically for Windows, but the manual install is pretty easy.

After confirming you've got the correct Java version installed, head over to our Bintray download section to get the latest Vamp CLI release.
It is located at https://dl.bintray.com/magnetic-io/downloads/vamp-cli/

* Download the release zip file
* Unzip it anywhere you'd like, e.g. `C:\Program Files\` 
* Inside the extracted Vamp CLI package is a `bin` directory. Add it to your PATH statement
* Open een CMD window and type `vamp`

