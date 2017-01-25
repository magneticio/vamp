# See: http://clarkgrubb.com/makefile-style-guide
SHELL             := bash
.SHELLFLAGS       := -eu -o pipefail -c
.DEFAULT_GOAL     := default
.DELETE_ON_ERROR:
.SUFFIXES:

# Constants, these can be overwritten in your Makefile.local
BUILD_SERVER := magneticio/buildserver
BUILD_PACKER := magneticio/packer
DIR_SBT	     := $(HOME)/.sbt
DIR_IVY	     := $(HOME)/.ivy2

# if Makefile.local exists, include it.
ifneq ("$(wildcard Makefile.local)", "")
	include Makefile.local
endif

# Don't change these
TARGET  := $(CURDIR)/target
VERSION := $(shell git describe --tags)

# Targets
.PHONY: all
all: default

# Using our buildserver which contains all the necessary dependencies
.PHONY: default
default:
	docker run \
		--name buildserver \
		--interactive \
		--rm \
		--volume $(CURDIR):/srv/src \
		--volume $(DIR_SBT):/home/vamp/.sbt \
		--volume $(DIR_IVY):/home/vamp/.ivy2 \
		--workdir=/srv/src \
		--env BUILD_UID=$(shell id -u) \
		--env BUILD_GID=$(shell id -g) \
		$(BUILD_SERVER) \
			make clean test pack

.PHONY: test
test:
	sbt test

.PHONY: pack
pack:
	sbt pack
	rm -rf $(TARGET)/vamp-consul-$(VERSION)
	mkdir -p $(TARGET)/vamp-consul-$(VERSION)
	cp -r $(TARGET)/pack/lib $(TARGET)/vamp-consul-$(VERSION)/
	mv $$(find $(TARGET)/vamp-consul-$(VERSION)/lib -type f -name "vamp-*-$(VERSION).jar") $(TARGET)/vamp-consul-$(VERSION)/

	docker volume create packer
	docker run \
		--name packer \
		--interactive \
		--rm \
		--volume $(TARGET)/vamp-consul-$(VERSION):/usr/local/src \
		--volume packer:/usr/local/stash \
		$(BUILD_PACKER) \
			vamp-consul $(VERSION)

.PHONY: clean
clean:
	sbt clean
