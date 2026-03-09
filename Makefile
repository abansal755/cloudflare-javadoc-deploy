SHELL := /bin/bash

IMAGE_NAME ?= cloudflare-javadoc-deploy
LOCAL_TMP_DIR ?= $(HOME)/Downloads/cloudflare-javadoc-deploy
CLOUDFLARE_API_TOKEN ?= my-token
CLOUDFLARE_PROJECT_NAME ?= my-project

.PHONY: help compile docker-build docker-run-local local-test

help:
	@echo "Targets:"
	@echo "  make compile         - mvn clean compile"
	@echo "  make docker-build    - Build local Docker image"
	@echo "  make docker-run-local- Run Lambda container locally for testing"
	@echo "  make local-test      - Run compile, image build, and local container run"

compile:
	mvn clean compile

docker-build:
	docker buildx build --provenance=false -t $(IMAGE_NAME) ./

docker-run-local:
	docker run --rm \
		-e DISABLE_TEMP_FILE_DELETION=true \
		-e DISABLE_CLOUDFLARE_DEPLOYMENT=true \
		-e DISABLE_SNAPSHOTS=false \
		-e CLOUDFLARE_API_TOKEN=$(CLOUDFLARE_API_TOKEN) \
		-e CLOUDFLARE_PROJECT_NAME=$(CLOUDFLARE_PROJECT_NAME) \
		-p 9000:8080 \
		-v $(LOCAL_TMP_DIR):/tmp \
		$(IMAGE_NAME)

local-test: compile docker-build docker-run-local
