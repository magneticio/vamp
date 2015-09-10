---
title: FAQ
type: faq
url: /documentation/faq/
aliases:
  - /faq/
weight: 10
---

## How does Vamp do service discovery?

Vamp currently uses a type of service discovery commonly called "server-side service discovery". Because this is quite an extensive subject, we've [dedicated a full section to it.](/documentation/about-vamp/service-discovery/)

## Why is Vamp Router not starting?

This is commonly due to the fact that Vamp Router tries to read/write to files and sockets owned by the `root` user. In a default installation using a package manager, Vamp Router runs as its own user `vamp-router`. It will also start HAproxy under that user.

However, if you at some moment run either Vamp Router or HAproxy as root directly, the file/socket ownership changes to `root`. If you then later return to using the default user `vamp-router`, it will fail. Please see [this issue](https://github.com/magneticio/vamp-router/issues/22#issuecomment-138900317) and its solution for more details.

> **Note:** there are many perfectly good reasons for running HAProxy and Vamp Router as `root`, like binding to privileged ports like port 80.
