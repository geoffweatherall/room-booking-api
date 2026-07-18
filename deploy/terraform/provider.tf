provider "aws" {
  region = var.aws_region
}

# The custom domain's ACM certificate must exist in us-east-1 regardless of
# which region this environment otherwise deploys into (AppSync custom
# domains have the same us-east-1 requirement as CloudFront) - aliased
# explicitly rather than relying on var.aws_region defaulting to us-east-1
# by coincidence. See domain.tf.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}
