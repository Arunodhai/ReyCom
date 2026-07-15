output "ecr_repository_url" {
  description = "URL used to tag and push the ReyCom backend image."
  value       = aws_ecr_repository.reycom_api.repository_url
}

output "dynamodb_table_name" {
  description = "DynamoDB order event table managed by Terraform."
  value       = aws_dynamodb_table.order_events.name
}

output "ec2_public_ip" {
  description = "Public IPv4 address of the ReyCom EC2 instance."
  value       = aws_instance.reycom.public_ip
}

output "ec2_public_dns" {
  description = "Public DNS name of the ReyCom EC2 instance."
  value       = aws_instance.reycom.public_dns
}

output "security_group_id" {
  description = "Security group attached to the ReyCom EC2 instance."
  value       = aws_security_group.reycom_ec2.id
}

output "ec2_instance_id" {
  description = "ID of the ReyCom EC2 instance."
  value       = aws_instance.reycom.id
}
