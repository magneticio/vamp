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
TARGET  := $(CURDIR)/bootstrap/target
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
			make clean test build
	make pack

.PHONY: test
test:
	sbt test

.PHONY: build
build:
	sbt package \
		"project common" publish-local-katana \
		"project persistence" publish-local-katana \
		"project model" publish-local-katana \
		"project operation" publish-local-katana \
		"project bootstrap" publish-local-katana \
		"project container_driver" publish-local-katana \
		"project workflow_driver" publish-local-katana \
		"project pulse" publish-local-katana \
		"project http_api" publish-local-katana \
		"project gateway_driver" publish-local-katana
	sbt publish-local

.PHONY: pack
pack:
	make build
	sbt "project bootstrap" pack
	rm -rf  $(TARGET)/vamp-$(VERSION)
	mkdir -p $(TARGET)/vamp-$(VERSION)
	cp -r $(TARGET)/pack/lib $(TARGET)/vamp-$(VERSION)/
	mv $$(find $(TARGET)/vamp-$(VERSION)/lib -type f -name "vamp-*-$(VERSION).jar") $(TARGET)/vamp-$(VERSION)/

	docker volume create packer
	docker run \
		--name packer \
		--interactive \
		--rm \
		--volume $(TARGET)/vamp-$(VERSION):/usr/local/src \
		--volume packer:/usr/local/stash \
		$(BUILD_PACKER) \
			vamp $(VERSION)

.PHONY: clean
clean:
	sbt clean
