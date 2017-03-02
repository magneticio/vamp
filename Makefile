# See: http://clarkgrubb.com/makefile-style-guide
SHELL             := bash
.SHELLFLAGS       := -eu -o pipefail -c
.DEFAULT_GOAL     := default
.DELETE_ON_ERROR:
.SUFFIXES:

# Constants, these can be overwritten in your Makefile.local
BUILD_SERVER := magneticio/buildserver
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
	docker pull $(BUILD_SERVER)
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
			make clean test

.PHONY: test
test:
	sbt test

.PHONY: pack
pack:
	export VAMP_VERSION="katana" && sbt package publish-local
	sbt pack
	rm -rf $(TARGET)/vamp-zookeeper-$(VERSION)
	mkdir -p $(TARGET)/vamp-zookeeper-$(VERSION)
	cp -r $(TARGET)/pack/lib $(TARGET)/vamp-zookeeper-$(VERSION)/
	mv $$(find $(TARGET)/vamp-zookeeper-$(VERSION)/lib -type f -name "vamp-*-$(VERSION).jar") $(TARGET)/vamp-zookeeper-$(VERSION)/

	docker volume create packer
	docker pull $(BUILD_SERVER)
	docker run \
		--name packer \
		--interactive \
		--rm \
		--volume $(TARGET)/vamp-zookeeper-$(VERSION):/usr/local/src \
		--volume packer:/usr/local/stash \
		$(BUILD_SERVER) \
			push vamp-zookeeper $(VERSION)

.PHONY: clean
clean:
	sbt clean
