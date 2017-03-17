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
	docker volume create packer
	docker pull $(BUILD_SERVER)
	docker run \
		--volume $(CURDIR):/srv/src \
		--volume $(DIR_SBT):/home/vamp/.sbt \
		--volume $(DIR_IVY):/home/vamp/.ivy2 \
		--volume packer:/usr/local/stash \
		--workdir=/srv/src \
		--env BUILD_UID=$(shell id -u) \
		--env BUILD_GID=$(shell id -g) \
		--env VAMP_VERSION="katana" \
		$(BUILD_SERVER) \
			sbt package publish-local pack

	rm -rf $(TARGET)/vamp-elasticsearch-$(VERSION)
	mkdir -p $(TARGET)/vamp-elasticsearch-$(VERSION)
	cp -r $(TARGET)/pack/lib $(TARGET)/vamp-elasticsearch-$(VERSION)/
	mv $$(find $(TARGET)/vamp-elasticsearch-$(VERSION)/lib -type f -name "vamp-*-katana.jar") $(TARGET)/vamp-elasticsearch-$(VERSION)/

	docker run \
		--volume packer:/usr/local/stash \
		$(BUILD_SERVER) \
			push vamp-elasticsearch $(VERSION)

.PHONY: clean
clean:
	sbt clean
