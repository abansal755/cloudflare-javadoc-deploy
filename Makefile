SHELL := /bin/bash

IMAGE_NAME ?= cloudflare-javadoc-deploy
LOCAL_TMP_DIR ?= $(HOME)/Downloads/cloudflare-javadoc-deploy

-include secrets.mk

.PHONY: help compile docker-build docker-run-local local-test

help:
	@echo "Targets:"
	@echo "  make compile         - mvn clean compile"
	@echo "  make docker-build    - Build local Docker image"
	@echo "  make docker-run-local- Run Lambda container locally for testing"
	@echo "  make local-test      - Run compile, image build, and local container run"

compile:
	mvn clean compile

copy-dependencies:
	mvn dependency:copy-dependencies

docker-build:
	docker buildx build --provenance=false -t $(IMAGE_NAME) ./

docker-run-local:
	docker run --rm \
		-p 9000:8080 \
		-v $(LOCAL_TMP_DIR):/tmp \
		$(IMAGE_NAME)

local-test: compile copy-dependencies docker-build docker-run-local
