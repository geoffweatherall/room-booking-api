variable "aws_region" {
  description = "AWS region to deploy the room-booking API into."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefix used to name AWS resources for this project."
  type        = string
  default     = "room-booking"
}
