terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Bucket/key/region/locking are supplied via backend.hcl (see
  # room-booking-bootstrap-terraform's README for how remote state works).
  backend "s3" {}
}
