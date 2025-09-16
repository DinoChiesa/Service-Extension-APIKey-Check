#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_shell_variables SAMPLE_NAME_ROOT CLOUDRUN_PROJECT_ID CLOUDRUN_AUTHZ_SERVICE_NAME
check_required_commands gcloud

printf "\nThis script configures the deployed Authz extension\n"
printf "in the project '%s'.\n" "$CLOUDRUN_PROJECT_ID"

SAMPLE_ADDRESS_NAME="${SAMPLE_NAME_ROOT}-ip"
IP=$(gcloud compute addresses describe "${SAMPLE_ADDRESS_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --format="get(address)" \
  --global)

dnsname="${IP}.nip.io"
printf "DNS name: %s\n" "${dnsname}"

authz_ext_name="authz-extnsn-1"
authz_backend="${CLOUDRUN_AUTHZ_SERVICE_NAME}-backend"

# https://cloud.google.com/service-extensions/docs/reference/rest/v1beta1/projects.locations.authzExtensions#AuthzExtension
#
# forwardHeaders:
#
#   Optional. List of the HTTP headers to forward to the extension (from the
#   client). If omitted, all headers are sent. Each element is a string indicating
#   the header name.
#
# Therefore, omit this from the YAML:
#
# forwardHeaders:
# - authorization

tmpfile=$(mktemp /tmp/authzext-sample.XXXXXX)
cat >"$tmpfile" <<EOF
name: ${authz_ext_name}
authority: ${dnsname}
loadBalancingScheme: EXTERNAL_MANAGED
service: https://www.googleapis.com/compute/v1/projects/${CLOUDRUN_PROJECT_ID}/global/backendServices/${authz_backend}
failOpen: false
timeout: "0.1s"
EOF

# Can I haz this?
#
# supportedEvents:
#   - REQUEST_HEADERS
#   - RESPONSE_HEADERS

cat "$tmpfile"

printf "\nImporting the authz extension definition...\n"
gcloud beta service-extensions authz-extensions import "${authz_ext_name}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --source "$tmpfile" \
  --location "global"

rm "$tmpfile"
