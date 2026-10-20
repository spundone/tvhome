# Sourced by the other scripts: loads tools/local.env if present.
_here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[ -f "$_here/local.env" ] && . "$_here/local.env"
