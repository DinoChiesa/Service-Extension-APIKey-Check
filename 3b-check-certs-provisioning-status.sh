#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_shell_variables CLOUDRUN_PROJECT_ID SAMPLE_NAME_ROOT
check_required_commands gcloud

printf "\nThis script checks the status of the Google-managed TLS cert for the Service Extension\n"
printf "authorization sample.\n\n"

SAMPLE_CERT_NAME="${SAMPLE_NAME_ROOT}-cert"
gcloud compute ssl-certificates describe "${SAMPLE_CERT_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
 --global \
 --format "get(name,managed.status, managed.domainStatus)"


printf "\nIt will not be usable while it remains in \"PROVISIONING\" state.\n\n"



