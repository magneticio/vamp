# See: http://clarkgrubb.com/makefile-style-guide
SHELL             := bash
.SHELLFLAGS       := -eu -o pipefail -c
.DEFAULT_GOAL     := default
.DELETE_ON_ERROR:
.SUFFIXES:

# Constants, these can be overwritten in your Makefile.local
BUILD_SERVER := magneticio/buildserver
BUILD_PACKER := magneticio/packer
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
			make clean test build pack

.PHONY: test
test:
	sbt test

.PHONY: build
build:
	sbt package "project common" publish-local-katana \
      "project persistence" publish-local-katana \
      "project model" publish-local-katana \
      "project operation" publish-local-katana \
      "project bootstrap" publish-local-katana \
      "project container_driver" publish-local-katana \
      "project workflow_driver" publish-local-katana \
      "project pulse" publish-local-katana \
      "project http_api" publish-local-katana \
      "project gateway_driver" publish-local-katana ; \
  if [ "$$(git describe --tags)" = "$$(git describe --abbrev=0)" ]; then sbt publish-local; fi

.PHONY: pack
pack:
	sbt "project bootstrap" pack && \
	export target=$(CURDIR)"/bootstrap/target" && export version="$$(git describe --tags)" && \
	rm -Rf "$${target}/vamp-$${version}" && mkdir -p "$${target}/vamp-$${version}" && \
	cp -R "$${target}/pack/lib" "$${target}/vamp-$${version}/." && \
	mv $$(find "$${target}/vamp-$${version}/lib" -name "vamp-*-$${version}.jar") "$${target}/vamp-$${version}/." && \
	docker volume create packer && \
	docker run \
    --name packer \
    --interactive \
    --volume $${target}/vamp-$${version}:/usr/local/src \
    --volume packer:/usr/local/stash \
    $(BUILD_PACKER) \
      vamp $${version} && \
  docker rm packer

.PHONY: clean
clean:
	sbt clean
