#!/bin/sh
set -eu
BACKEND_URL="${BACKEND_URL:-http://benchmark-backend:8080}"
# Strip a trailing slash so /api/... is forwarded unchanged.
BACKEND_URL="${BACKEND_URL%/}"
sed "s#__BACKEND_URL__#${BACKEND_URL}#g" /opt/app-root/etc/nginx.conf.template > /tmp/nginx.conf
echo "Proxying /api/ -> ${BACKEND_URL}"
exec nginx -c /tmp/nginx.conf -g 'daemon off;'
