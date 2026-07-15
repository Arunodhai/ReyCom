variable "aws_region" {
  description = "AWS region in which ReyCom resources will be created."
  type        = string
  default     = "ap-south-1"
}

variable "project_name" {
  description = "Name used to tag and name ReyCom infrastructure."
  type        = string
  default     = "reycom"
}

variable "ecr_repository_name" {
  description = "Name of the ECR repository that stores the backend image."
  type        = string
  default     = "reycom-api"
}

variable "dynamodb_table_name" {
  description = "Name of the DynamoDB table used for order event timelines."
  type        = string
  default     = "reycom_order_events"
}

variable "ec2_instance_type" {
  description = "EC2 instance type for the future ReyCom host."
  type        = string
  default     = "t3.micro"
}

variable "ec2_key_name" {
  description = "Name of an existing EC2 key pair used for SSH access."
  type        = string

  validation {
    condition     = length(trimspace(var.ec2_key_name)) > 0
    error_message = "ec2_key_name must identify an existing EC2 key pair."
  }
}

variable "allowed_ssh_cidr" {
  description = "Single trusted CIDR allowed to reach SSH. Replace the safe default with your public IP /32."
  type        = string
  default     = "127.0.0.1/32"

  validation {
    condition = (
      can(cidrhost(var.allowed_ssh_cidr, 0)) &&
      var.allowed_ssh_cidr != "0.0.0.0/0" &&
      var.allowed_ssh_cidr != "::/0"
    )
    error_message = "allowed_ssh_cidr must be a valid restricted CIDR and cannot allow the entire internet."
  }
}

variable "ami_id" {
  description = "Ubuntu 22.04 or 24.04 LTS AMI ID for the selected AWS region."
  type        = string

  validation {
    condition     = can(regex("^ami-[0-9a-fA-F]+$", var.ami_id))
    error_message = "ami_id must be a valid AMI identifier beginning with ami-."
  }
}
