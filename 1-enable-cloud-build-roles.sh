#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_shell_variables CLOUDRUN_PROJECT_ID CLOUDRUN_SERVICE_NAME CLOUDRUN_SERVICE_REGION
check_required_commands gcloud

printf "\nThis script sets the  roles for the default compute service account so that\n"
printf "Cloud Build can work properly.\n"
printf "\n  Project: %s\n" "$CLOUDRUN_PROJECT_ID"

## Roles
# Cloud Run Developer: Allows Cloud Build to manage and deploy Cloud Run services.
#
# Logs Writer: Enables Cloud Build to write build logs to Cloud Logging.
#
# Artifact Registry Writer: Grants Cloud Build the ability to write artifacts
# to Artifact Registry.
#
# Service Account User: Permits Cloud Build to act as the Cloud Run service's
# Runtime Service Account, which is necessary for deploying new images.
#
# Storage Admin: Enables Cloud Build to manage storage resources, including
# storing and retrieving artifacts.

project_number=$(gcloud projects describe "${CLOUDRUN_PROJECT_ID}" --format="value(projectNumber)")
sa_email="${project_number}-compute@developer.gserviceaccount.com"

SA_REQUIRED_ROLES=("run.developer" "logging.logWriter" "artifactregistry.writer"
  "iam.serviceAccountUser" "storage.admin")

printf "\nEnabling roles.\n"
for j in "${!SA_REQUIRED_ROLES[@]}"; do
  ROLE=${SA_REQUIRED_ROLES[j]}
  printf "Role %s...\n" "$ROLE"
  gcloud projects add-iam-policy-binding "${CLOUDRUN_PROJECT_ID}" \
    --condition=None \
    --member "serviceAccount:${sa_email}" \
    --role "roles/${ROLE}"
done

printf "\nOk.\n"
