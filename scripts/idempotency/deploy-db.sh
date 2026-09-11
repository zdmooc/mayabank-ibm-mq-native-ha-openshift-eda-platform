#!/usr/bin/env bash
set -euo pipefail
case "${OSTYPE:-}" in msys*|cygwin*) unset MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL ;; esac
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ns=mayabank-mq-local
[[ "$(oc get nodes -o name)" == node/crc ]] || { echo 'STOP : CRC uniquement.'; exit 1; }
oc -n "$ns" get deployment mq >/dev/null

# Keep the installed digest on subsequent runs; do not silently upgrade a persistent database.
db_image="$(oc -n "$ns" get deployment payments-db --ignore-not-found -o jsonpath='{.spec.template.spec.containers[0].image}')"
if [[ -z "$db_image" ]]; then
  db_image="$(oc -n openshift get istag postgresql:16 --ignore-not-found -o jsonpath='{.image.dockerImageReference}')"
  if [[ -z "$db_image" ]]; then
    oc -n mayabank-mq-build import-image payments-postgresql:16 --from=quay.io/sclorg/postgresql-16-c9s:latest --confirm
    db_image="$(oc -n mayabank-mq-build get istag payments-postgresql:16 -o jsonpath='{.image.dockerImageReference}')"
  fi
fi
[[ "$db_image" =~ ^[a-zA-Z0-9./:_-]+@sha256:[a-f0-9]{64}$ ]] || { echo 'STOP : digest PostgreSQL absent ou invalide.'; exit 1; }
printf 'PostgreSQL image : %s\n' "$db_image"
if [[ -z "$(oc -n "$ns" get secret payments-db-credentials --ignore-not-found -o name)" ]]; then
  password_file="$(mktemp)"
  chmod 600 "$password_file"
  trap 'rm -f -- "$password_file"' EXIT
  openssl rand -hex 24 | tr -d '\r\n' > "$password_file"
  oc -n "$ns" create secret generic payments-db-credentials --from-file="password=$password_file"
  rm -f -- "$password_file"
  trap - EXIT
fi
sed "s|image: quay.io/sclorg/postgresql-16-c9s:latest|image: $db_image|" "$root/deploy/idempotency/database.yaml" | oc apply -f -
oc -n "$ns" rollout status deployment/payments-db --timeout=600s
# Password remains inside the container, never in command arguments or local output.
MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' oc -n "$ns" exec -i deployment/payments-db -- sh -c \
  'PGPASSWORD="$POSTGRESQL_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRESQL_USER" -d "$POSTGRESQL_DATABASE" -v ON_ERROR_STOP=1' \
  < "$root/deploy/idempotency/schema.sql"
echo 'PostgreSQL et schéma prêts ; tests métier encore nécessaires.'
