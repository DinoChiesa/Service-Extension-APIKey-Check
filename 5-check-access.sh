#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_shell_variables CLOUDRUN_PROJECT_ID CLOUDRUN_SERVICE_NAME CLOUDRUN_SERVICE_REGION SAMPLE_NAME_ROOT
check_required_commands gcloud

printf "\nThis script checks access to the service after the initial frontend networking\n"
printf "has been set up.\n\n"


SAMPLE_ADDRESS_NAME="${SAMPLE_NAME_ROOT}-ip"
IP=$(gcloud compute addresses describe "${SAMPLE_ADDRESS_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --format="get(address)" \
  --global)
dnsname="${IP}.nip.io"
printf "DNS name: %s\n" "${dnsname}"


printf "\nTest access...\n"
printf "\n  curl -i https://${dnsname}/status\n\n\n"
curl -i https://${dnsname}/status
