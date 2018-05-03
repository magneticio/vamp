# See: http://clarkgrubb.com/makefile-style-guide
SHELL             := bash
.SHELLFLAGS       := -eu -o pipefail -c
.DEFAULT_GOAL     := default
.DELETE_ON_ERROR  :
.SUFFIXES         :

STASH     := stash
PROJECT   := vamp
VERSION   := $(shell git describe --tags)
FABRICATOR:= magneticio/fabricator:jdk_8u162_scala_2.12.1_sbt_0.13.13
TARGET    := $$HOME/.stash/$(PROJECT)

# if Makefile.local exists, include it.
ifneq ("$(wildcard Makefile.local)", "")
	include Makefile.local
endif

.PHONY: clean
clean:
	find "$(CURDIR)" -type d -name "target" | xargs rm -Rf

.PHONY: local
local:
	VAMP_VERSION=katana sbt clean test publish-local
	VAMP_VERSION=$(VERSION) sbt 'project bootstrap' pack

.PHONY: stash
stash:
	rm -Rf $(TARGET) || true
	mkdir -p $(TARGET)
	cp -r $(CURDIR)/bootstrap/target/pack/lib $(TARGET)/
	find $(TARGET)/lib -type f -name "vamp-*.jar" -exec mv {} $(TARGET)/ \;

.PHONY: build
build:
	test -t 1 && USE_TTY="-it" ; \
	docker run \
	       $${USE_TTY} \
         --rm \
         --volume $(STASH):/root \
         --volume $(CURDIR):/$(PROJECT) \
         --workdir=/$(PROJECT) \
         $(FABRICATOR) make local stash

.PHONY: default
default: clean build
