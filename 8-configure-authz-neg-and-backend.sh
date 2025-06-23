#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_shell_variables REPOSITORY_PROJECT CLOUDRUN_AUTHZ_SERVICE_NAME CLOUDRUN_SERVICE_REGION CLOUDRUN_PROJECT_ID
check_required_commands gcloud

printf "Create the serverless network endpoint group for the Authz extension...\n"
authz_neg="${CLOUDRUN_AUTHZ_SERVICE_NAME}-neg"
gcloud compute network-endpoint-groups create "${authz_neg}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --network-endpoint-type "serverless" \
  --cloud-run-service "${CLOUDRUN_AUTHZ_SERVICE_NAME}" \
  --region "${CLOUDRUN_SERVICE_REGION}"

printf "Create the backend for the serverless NEG...\n"
authz_backend="${CLOUDRUN_AUTHZ_SERVICE_NAME}-backend"
gcloud compute backend-services create "${authz_backend}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --global \
  --load-balancing-scheme "EXTERNAL_MANAGED" \
  --protocol "HTTP2" \
  --port-name "http"

printf "Add the backend to the load balancer...\n"
gcloud compute backend-services add-backend "${authz_backend}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --global \
  --network-endpoint-group "${authz_neg}" \
  --network-endpoint-group-region "${CLOUDRUN_SERVICE_REGION}"
