# See: http://clarkgrubb.com/makefile-style-guide
SHELL             := bash
.SHELLFLAGS       := -eu -o pipefail -c
.DEFAULT_GOAL     := default
.DELETE_ON_ERROR:
.SUFFIXES:

# Constants, these can be overwritten in your Makefile.local
DOCKER_IMAGE    := magneticio/buildserver
SBT_GLOBAL_BASE := ~/.sbt/0.13
SBT_IVY_HOME    := ~/.ivy2

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
	@echo "Building Vamp"
	docker run \
		--interactive \
		--rm \
		--volume $(CURDIR):/srv/src \
		--volume $(SBT_GLOBAL_BASE):/srv/cache/sbt \
		--volume $(SBT_IVY_HOME):/src/cache/sbt/ivy2 \
		--workdir=/srv/src \
		--env BUILD_UID=$(shell id -u) \
		--env BUILD_GID=$(shell id -g) \
		$(DOCKER_IMAGE) \
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

