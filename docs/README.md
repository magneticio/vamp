# Writing & updating Vamp docs

When working on the docs, please use the following conventions and stick to the style guide:

## File names

Use all lowercase, no special characters and connect words using `-` characters, i.e: `sla-and-escalations.md`

## Front matter

Our static site generator [Hugo](http://gohugo.io) requires front matter to be set at the start of a page.
This links the page to correct category, gives it its html title and determines the order of the pages from top to bottom.
We use the YAML notation for this, i.e:

```
---
title: Breeds
weight: 20
menu:
  main:
    parent: reference
---
```
