#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_shell_variables CLOUDRUN_PROJECT_ID CLOUDRUN_SERVICE_NAME CLOUDRUN_SERVICE_REGION SAMPLE_NAME_ROOT
check_required_commands gcloud

printf "\nThis script configures network elements for the Service Extension authorization sample\n"
printf "in the project '%s'.\n" "$CLOUDRUN_PROJECT_ID"


SAMPLE_ADDRESS_NAME="${SAMPLE_NAME_ROOT}-ip"
printf "\nChecking for an IP address named '%s'...\n" "${SAMPLE_ADDRESS_NAME}"
if ! gcloud compute addresses describe "${SAMPLE_ADDRESS_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --global 2>/dev/null; then
  printf "Creating a new address named '%s'...\n" "${SAMPLE_ADDRESS_NAME}"
  gcloud compute addresses create "${SAMPLE_ADDRESS_NAME}" \
    --project "${CLOUDRUN_PROJECT_ID}" \
    --network-tier PREMIUM \
    --ip-version IPV4 \
    --global
else
  printf "An address with that name already exists.\n"
fi

IP=$(gcloud compute addresses describe "${SAMPLE_ADDRESS_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --format="get(address)" \
  --global)

dnsname="${IP}.nip.io"
printf "DNS name: %s\n" "${dnsname}"

printf "\nCreate a Serverless Network endpoint Group...\n"
SAMPLE_NEG_NAME="${SAMPLE_NAME_ROOT}-neg"
gcloud compute network-endpoint-groups create "${SAMPLE_NEG_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --network-endpoint-type "serverless" \
  --cloud-run-service "${CLOUDRUN_SERVICE_NAME}" \
  --region "${CLOUDRUN_SERVICE_REGION}"

printf "\nCreate a backend service...\n"
SAMPLE_BACKEND_NAME="${SAMPLE_NAME_ROOT}-backend"
gcloud compute backend-services create "${SAMPLE_BACKEND_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --global \
  --load-balancing-scheme "EXTERNAL_MANAGED"

printf "\nAdd the serverless NEG to the Backend Service...\n"
gcloud compute backend-services add-backend "${SAMPLE_BACKEND_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --global \
  --network-endpoint-group "${SAMPLE_NEG_NAME}" \
  --network-endpoint-group-region "${CLOUDRUN_SERVICE_REGION}"

printf "\nCreate a URL Map...\n"
SAMPLE_URLMAP_NAME="${SAMPLE_NAME_ROOT}-urlmap"
gcloud compute url-maps create "${SAMPLE_URLMAP_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --default-service "${SAMPLE_BACKEND_NAME}"

# Not sure this is needed

printf "\nAsk Google Cloud to manage certificates?......\n"
SAMPLE_CERT_NAME="${SAMPLE_NAME_ROOT}-cert"
gcloud compute ssl-certificates create "${SAMPLE_CERT_NAME}" \
  --domains "${dnsname}" \
  --global

printf "\nCreate an HTTPS Proxy...\n"
SAMPLE_HTTPSPROXY_NAME="${SAMPLE_NAME_ROOT}-httpsproxy"
gcloud compute target-https-proxies create "${SAMPLE_HTTPSPROXY_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --url-map "${SAMPLE_URLMAP_NAME}" \
  --ssl-certificates "${SAMPLE_CERT_NAME}" \
  --global

printf "\nCreating a forwarding rule....\n"
SAMPLE_FWDRULE_NAME="${SAMPLE_NAME_ROOT}-fwdrule"
gcloud compute forwarding-rules create "${SAMPLE_FWDRULE_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --load-balancing-scheme "EXTERNAL_MANAGED" \
  --target-https-proxy "${SAMPLE_HTTPSPROXY_NAME}" \
  --global \
  --ports 443 \
  --address "$IP"
