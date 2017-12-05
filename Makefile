# See: http://clarkgrubb.com/makefile-style-guide
SHELL             := bash
.SHELLFLAGS       := -eu -o pipefail -c
.DEFAULT_GOAL     := default
.DELETE_ON_ERROR:
.SUFFIXES:

# Constants, these can be overwritten in your Makefile.local
PACKER       ?= packer
BUILD_SERVER := magneticio/buildserver
DIR_SBT	     := $(HOME)/.sbt/boot
DIR_IVY	     := $(HOME)/.ivy2

# if Makefile.local exists, include it.
ifneq ("$(wildcard Makefile.local)", "")
	include Makefile.local
endif

# Don't change these
PROJECT   := vamp
TARGET    := $(CURDIR)/bootstrap/target
VERSION   := $(shell git describe --tags)
BUILD_CMD := sbt clean test 'project bootstrap' pack
PACK_CMD  := VAMP_VERSION=katana sbt publish-local && VAMP_VERSION=$(VERSION) sbt 'project bootstrap' pack

# Targets
.PHONY: all
all: default

# Using our buildserver which contains all the necessary dependencies
.PHONY: default
default:
	docker pull $(BUILD_SERVER)
	docker run \
		--rm \
		--volume $(CURDIR):/srv/src \
		--volume $(DIR_SBT):/home/vamp/.sbt/boot \
		--volume $(DIR_IVY):/home/vamp/.ivy2 \
		--workdir=/srv/src \
		--env BUILD_UID=$(shell id -u) \
		--env BUILD_GID=$(shell id -g) \
		$(BUILD_SERVER) "$(BUILD_CMD)"

.PHONY: pack
pack:
	docker volume create $(PACKER)
	docker pull $(BUILD_SERVER)

	docker run \
		--rm \
		--volume $(CURDIR):/srv/src \
		--volume $(DIR_SBT):/home/vamp/.sbt/boot \
		--volume $(DIR_IVY):/home/vamp/.ivy2 \
		--workdir=/srv/src \
		--env BUILD_UID=$(shell id -u) \
		--env BUILD_GID=$(shell id -g) \
		$(BUILD_SERVER) "$(PACK_CMD)"

	rm -rf $(TARGET)/$(PROJECT)-*
	mkdir -p $(TARGET)/$(PROJECT)-$(VERSION)
	cp -r $(TARGET)/pack/lib $(TARGET)/$(PROJECT)-$(VERSION)/
	mv $$(find $(TARGET)/$(PROJECT)-$(VERSION)/lib -type f -name "vamp-*.jar") $(TARGET)/$(PROJECT)-$(VERSION)/

	docker run \
		--rm \
		--volume $(TARGET)/$(PROJECT)-$(VERSION):/usr/local/src \
		--volume $(PACKER):/usr/local/stash \
		$(BUILD_SERVER) \
			push $(PROJECT) $(VERSION)

.PHONY: pack-local
pack-local:
	$(PACK_CMD)

	rm -rf $(TARGET)/$(PROJECT)-*
	mkdir -p $(TARGET)/$(PROJECT)-$(VERSION)
	cp -r $(TARGET)/pack/lib $(TARGET)/$(PROJECT)-$(VERSION)/
	mv $$(find $(TARGET)/$(PROJECT)-$(VERSION)/lib -type f -name "vamp-*.jar") $(TARGET)/$(PROJECT)-$(VERSION)/

	docker volume create $(PACKER)
	docker pull $(BUILD_SERVER)
	docker run \
		--rm \
		--volume $(TARGET)/$(PROJECT)-$(VERSION):/usr/local/src \
		--volume $(PACKER):/usr/local/stash \
		$(BUILD_SERVER) \
			push $(PROJECT) $(VERSION)
