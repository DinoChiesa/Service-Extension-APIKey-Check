#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_shell_variables CLOUDRUN_PROJECT_ID CLOUDRUN_SERVICE_NAME CLOUDRUN_SERVICE_REGION
check_required_commands gcloud

printf "\nThis script sets the Google Cloud project for gcloud, and enables the services/APIs\n"
printf "needed for this example.\n"
printf "\n  Project: %s\n" "$CLOUDRUN_PROJECT_ID"

printf "\n\nSetting default project...\n"
gcloud config set project "$CLOUDRUN_PROJECT_ID"
printf "\nEnabling APIs...\n"
# Enable the needed APIs
gcloud services enable \
  compute.googleapis.com \
  sheets.googleapis.com \
  run.googleapis.com \
  storage.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  certificatemanager.googleapis.com \
  networkservices.googleapis.com \
  networksecurity.googleapis.com

printf "\nOk.\n"
