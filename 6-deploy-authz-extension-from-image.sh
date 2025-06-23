#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_shell_variables REPOSITORY_PROJECT CLOUDRUN_AUTHZ_SERVICE_NAME CLOUDRUN_SERVICE_REGION CLOUDRUN_PROJECT_ID AUTHZ_SHORT_SA

check_required_commands gcloud

SHEET_ID=1eeluePPX1b9um33ZmEUTLKXRGoSjqISCNkWaZYH_bjQ
sa_email="$AUTHZ_SHORT_SA@${CLOUDRUN_PROJECT_ID}.iam.gserviceaccount.com"

printf "\nDeploy the container image to Cloud Run as '%s'...\n" "${CLOUDRUN_AUTHZ_SERVICE_NAME}"
image_name_and_tag="service-extension-authz-container:20250622"
gcloud run deploy "${CLOUDRUN_AUTHZ_SERVICE_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --set-env-vars "VERBOSE=true" \
  --set-env-vars "SHEET_ID=${SHEET_ID}" \
  --region "${CLOUDRUN_SERVICE_REGION}" \
  --service-account "${sa_email}" \
  --cpu 1 \
  --memory '512Mi' \
  --min-instances 1 \
  --max-instances 1 \
  --no-invoker-iam-check \
  --use-http2 \
  --image "gcr.io/${REPOSITORY_PROJECT}/cloud-builds-submit/${image_name_and_tag}"
