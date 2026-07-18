bucket       = "remote-state-431071856068"
region       = "us-east-1"
use_lockfile = true

# `key` is deliberately not set here - it's supplied per-environment at init
# time (see deploy.sh/undeploy.sh/authenticate.sh), so state lives at
# <environment>/mootmaker-api/terraform.tfstate in this bucket.
