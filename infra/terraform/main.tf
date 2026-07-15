data "aws_vpc" "default" {
  default = true
}

# The existing GitHub Actions workflow publishes the backend image here.
resource "aws_ecr_repository" "reycom_api" {
  name                 = var.ecr_repository_name
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "reycom_api" {
  repository = aws_ecr_repository.reycom_api.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep the latest 10 backend images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

resource "aws_dynamodb_table" "order_events" {
  name         = var.dynamodb_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "orderId"
  range_key    = "eventTime"

  attribute {
    name = "orderId"
    type = "S"
  }

  attribute {
    name = "eventTime"
    type = "S"
  }
}

# This phase uses the account's default VPC to keep the first Terraform setup small.
resource "aws_security_group" "reycom_ec2" {
  name        = "${var.project_name}-ec2-sg"
  description = "Network access for the ReyCom EC2 host"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH from one trusted CIDR"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.allowed_ssh_cidr]
  }

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Temporary direct ReyCom API testing"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-ec2-sg"
  }
}

resource "aws_iam_role" "reycom_ec2" {
  name = "${var.project_name}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy" "ecr_pull" {
  name = "${var.project_name}-ecr-pull"
  role = aws_iam_role.reycom_ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrLogin"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Sid    = "PullReyComImages"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer"
        ]
        Resource = aws_ecr_repository.reycom_api.arn
      }
    ]
  })
}

resource "aws_iam_role_policy" "dynamodb_access" {
  name = "${var.project_name}-dynamodb-access"
  role = aws_iam_role.reycom_ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:BatchGetItem",
          "dynamodb:BatchWriteItem",
          "dynamodb:DeleteItem",
          "dynamodb:DescribeTable",
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:Query",
          "dynamodb:Scan",
          "dynamodb:UpdateItem"
        ]
        Resource = [
          aws_dynamodb_table.order_events.arn,
          "${aws_dynamodb_table.order_events.arn}/index/*"
        ]
      }
    ]
  })
}

resource "aws_iam_instance_profile" "reycom_ec2" {
  name = "${var.project_name}-ec2-profile"
  role = aws_iam_role.reycom_ec2.name
}

resource "aws_instance" "reycom" {
  ami                         = var.ami_id
  instance_type               = var.ec2_instance_type
  key_name                    = var.ec2_key_name
  vpc_security_group_ids      = [aws_security_group.reycom_ec2.id]
  iam_instance_profile        = aws_iam_instance_profile.reycom_ec2.name
  associate_public_ip_address = true

  # Installs host tooling only. Application deployment remains a manual step.
  user_data = <<-EOF
    #!/bin/bash
    set -euxo pipefail

    export DEBIAN_FRONTEND=noninteractive
    apt-get update
    apt-get install -y ca-certificates curl unzip

    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    . /etc/os-release
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" > /etc/apt/sources.list.d/docker.list

    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    systemctl enable --now docker
    usermod -aG docker ubuntu

    case "$(uname -m)" in
      x86_64) aws_arch="x86_64" ;;
      aarch64|arm64) aws_arch="aarch64" ;;
      *) echo "Unsupported architecture" >&2; exit 1 ;;
    esac
    curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-$aws_arch.zip" -o /tmp/awscliv2.zip
    unzip -q /tmp/awscliv2.zip -d /tmp
    /tmp/aws/install
    rm -rf /tmp/aws /tmp/awscliv2.zip

    mkdir -p /opt/reycom
    chown ubuntu:ubuntu /opt/reycom
  EOF

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    encrypted             = true
    delete_on_termination = true
  }

  depends_on = [
    aws_iam_role_policy.ecr_pull,
    aws_iam_role_policy.dynamodb_access
  ]

  tags = {
    Name = "${var.project_name}-api"
  }
}
