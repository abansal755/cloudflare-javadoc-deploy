SHELL := /bin/bash

IMAGE_NAME ?= cloudflare-javadoc-deploy
LOCAL_TMP_DIR ?= $(HOME)/Downloads/cloudflare-javadoc-deploy

DISABLE_TEMP_FILE_DELETION ?= false

DISABLE_SNAPSHOTS ?= false

DISABLE_CLOUDFLARE_DEPLOYMENT ?= true
CLOUDFLARE_API_TOKEN ?= my-token
CLOUDFLARE_PROJECT_NAME ?= my-project

DISABLE_STATUS_EMAIL ?= true
STATUS_EMAIL_RECIPIENT ?= recipient@email.com
STATUS_EMAIL_SENDER ?= sender@email.com
SITE_URL ?= https://website.com
AWS_ACCESS_KEY_ID ?= access-key-id
AWS_SECRET_ACCESS_KEY ?= secret-access-key

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

docker-build:
	docker buildx build --provenance=false -t $(IMAGE_NAME) ./

docker-run-local:
	docker run --rm \
		-e DISABLE_TEMP_FILE_DELETION=$(DISABLE_TEMP_FILE_DELETION) \
		-e DISABLE_SNAPSHOTS=$(DISABLE_SNAPSHOTS) \
		-e DISABLE_CLOUDFLARE_DEPLOYMENT=$(DISABLE_CLOUDFLARE_DEPLOYMENT) \
		-e CLOUDFLARE_API_TOKEN=$(CLOUDFLARE_API_TOKEN) \
		-e CLOUDFLARE_PROJECT_NAME=$(CLOUDFLARE_PROJECT_NAME) \
		-e DISABLE_STATUS_EMAIL=$(DISABLE_STATUS_EMAIL) \
		-e STATUS_EMAIL_RECIPIENT=$(STATUS_EMAIL_RECIPIENT) \
		-e STATUS_EMAIL_SENDER=$(STATUS_EMAIL_SENDER) \
		-e SITE_URL=$(SITE_URL) \
		-e AWS_ACCESS_KEY_ID=$(AWS_ACCESS_KEY_ID) \
		-e AWS_SECRET_ACCESS_KEY=$(AWS_SECRET_ACCESS_KEY) \
		-p 9000:8080 \
		-v $(LOCAL_TMP_DIR):/tmp \
		$(IMAGE_NAME)

local-test: compile docker-build docker-run-local
