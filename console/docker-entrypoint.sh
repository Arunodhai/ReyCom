#!/bin/sh
set -eu

cat > /usr/share/nginx/html/env-config.js <<EOF
window.REYCOM_CONFIG = {
  apiBaseUrl: "${VITE_API_BASE_URL:-}"
};
EOF
