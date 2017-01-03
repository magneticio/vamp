# See: http://clarkgrubb.com/makefile-style-guide
SHELL             := bash
.SHELLFLAGS       := -eu -o pipefail -c
.DEFAULT_GOAL     := default
.DELETE_ON_ERROR:
.SUFFIXES:

# Constants, these can be overwritten in your Makefile.local
CONTAINER := magneticio/buildserver:0.4
DIR_SBT   := $(HOME)/.sbt
DIR_IVY   := $(HOME)/.ivy2

# if Makefile.local exists, include it.
ifneq ("$(wildcard Makefile.local)", "")
	include Makefile.local
endif

# Targets
.PHONY: all
all: default

# Using our buildserver which contains all the necessary dependencies
.PHONY: default
default:
	docker run \
		--interactive \
		--rm \
		--volume $(CURDIR):/srv/src \
		--volume $(DIR_SBT):/home/vamp/.sbt \
		--volume $(DIR_IVY):/home/vamp/.ivy2 \
		--workdir=/srv/src \
		--env BUILD_UID=$(shell id -u) \
		--env BUILD_GID=$(shell id -g) \
		$(CONTAINER) \
			make test build


.PHONY: test
test:
	sbt test


.PHONY: build
build:
	sbt assembly


.PHONY: clean
clean:
	$(foreach dir, \
		$(shell find $(CURDIR) -name target -type d), \
			rm -rf $(dir))

.PHONY: katana
katana:
	sbt "project common" publish-local-katana \
	    "project persistence" publish-local-katana \
	    "project model" publish-local-katana \
	    "project operation" publish-local-katana \
	    "project bootstrap" publish-local-katana \
	    "project container_driver" publish-local-katana \
	    "project workflow_driver" publish-local-katana \
	    "project pulse" publish-local-katana \
	    "project http_api" publish-local-katana \
	    "project gateway_driver" publish-local-katana
