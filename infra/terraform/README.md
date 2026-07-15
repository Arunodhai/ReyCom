# ReyCom Terraform Infrastructure

This folder prepares the first ReyCom AWS infrastructure as reviewable Terraform code. It does not run automatically, and no GitHub Actions workflow calls `terraform apply`.

EC2 deployment remains paused. The instance resource and bootstrap script are included so the intended infrastructure can be reviewed and planned before deciding to create billable AWS resources.

## Resources

Terraform defines:

- An ECR repository for `reycom-api`, with scan-on-push, AES-256 encryption, and a lifecycle policy that retains the latest 10 images.
- A `PAY_PER_REQUEST` DynamoDB table with `orderId` as the partition key and `eventTime` as the sort key.
- An EC2 security group in the account's default VPC.
- An EC2 IAM role and instance profile with scoped ECR pull and DynamoDB table permissions.
- An Ubuntu EC2 instance with an encrypted 20 GiB root volume and IMDSv2 required.
- EC2 user data that installs Docker, Docker Compose, and AWS CLI. It does not deploy ReyCom or contain application secrets.

The security group allows:

- SSH `22` only from `allowed_ssh_cidr`.
- HTTP `80` from the internet.
- ReyCom testing port `8080` from the internet temporarily.
- All outbound traffic.

Remove public port `8080` access when the API is placed behind a reverse proxy or load balancer.

## Prerequisites

Install Terraform and AWS CLI, then authenticate AWS CLI with an identity allowed to manage ECR, DynamoDB, IAM, security groups, and EC2 resources.

Confirm the active AWS identity before planning:

```bash
aws sts get-caller-identity
```

Terraform uses local state in this phase. There is no remote backend yet.

## Configure Variables

Create your local variable file from the committed example:

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` and replace:

- `ec2_key_name` with an existing EC2 key pair in `aws_region`.
- `allowed_ssh_cidr` with your current public IP followed by `/32`.
- `ami_id` with an Ubuntu AMI available in `aws_region`.

`terraform.tfvars` is ignored by Git. It should not contain AWS access keys, JWT secrets, database passwords, or other application secrets.

Find your public IP with:

```bash
curl https://checkip.amazonaws.com
```

For the AMI, open **EC2 > AMI Catalog** in the AWS console, select an official Canonical Ubuntu Server 22.04 or 24.04 LTS image, confirm its architecture matches the instance type, and copy its AMI ID for `ap-south-1`. AMI IDs differ by region.

You can also query Canonical's public SSM parameter for Ubuntu 24.04 x86_64:

```bash
aws ssm get-parameter \
  --region ap-south-1 \
  --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
  --query Parameter.Value \
  --output text
```

## Initialize And Validate

Download the AWS provider:

```bash
terraform init
```

Format and validate the configuration:

```bash
terraform fmt
terraform validate
```

Initialization may create `.terraform.lock.hcl`. Commit that lock file after reviewing it so future runs use consistent provider versions.

## Existing ECR Repository

The `reycom-api` ECR repository already used by the publishing workflow must be imported into Terraform state before the first plan or apply. Importing records the existing repository; it does not recreate it:

```bash
terraform import \
  -var-file="terraform.tfvars" \
  aws_ecr_repository.reycom_api \
  reycom-api
```

If the AWS DynamoDB table already exists, import it as well:

```bash
terraform import \
  -var-file="terraform.tfvars" \
  aws_dynamodb_table.order_events \
  reycom_order_events
```

After importing, inspect the plan carefully for differences between existing AWS configuration and this code.

## Preview Changes

Terraform planning reads AWS but does not create the declared resources:

```bash
terraform plan -var-file="terraform.tfvars"
```

Review every proposed create, update, and destroy operation. In particular, confirm the AWS account, region, AMI, key pair, and SSH CIDR.

## Optional Apply And Destroy

`terraform apply` is intentionally manual and optional:

```bash
terraform apply -var-file="terraform.tfvars"
```

**Warning:** applying this configuration can create AWS resources that cost money, including EC2, EBS, DynamoDB usage, and ECR storage. Terraform is not connected to CI/CD and nothing in this phase applies it automatically.

To remove resources managed by this Terraform state:

```bash
terraform destroy -var-file="terraform.tfvars"
```

Destroy is destructive. Review its plan before confirming, especially when existing ECR or DynamoDB resources have been imported.

## ECR Workflow Integration

The existing ECR GitHub Actions workflow continues to build and push `latest` and commit-SHA tags to `reycom-api`. Terraform manages the repository itself and its image-retention policy; it does not change, trigger, or replace the publishing workflow.

When EC2 deployment resumes, the instance profile permits the host to request an ECR login token and pull layers from this repository without storing long-lived AWS keys on the instance. The DynamoDB policy similarly limits application access to the ReyCom order-event table.
