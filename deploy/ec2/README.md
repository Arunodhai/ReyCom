# ReyCom EC2 Deployment

This deployment runs the ReyCom API and its supporting services on one Ubuntu EC2 instance with Docker Compose. The API image is pulled from the private `reycom-api` ECR repository; no source code or image build is required on EC2.

## 1. Launch the EC2 instance

Create an Ubuntu 24.04 LTS x86_64 instance in the same AWS Region as the ECR repository. Because the instance runs Java, PostgreSQL, Redis, DynamoDB Local, and Kafka together, use at least `t3.medium` with 30 GB of storage for this learning deployment.

Attach an IAM role with `AmazonEC2ContainerRegistryReadOnly` permission. This lets the instance pull from ECR without storing AWS access keys on disk.

Configure the security group:

| Port | Source | Purpose |
| --- | --- | --- |
| `22` | Your public IP only | SSH administration |
| `8080` | Your public IP while testing | ReyCom API |

PostgreSQL, Redis, DynamoDB Local, and Kafka are not published on the EC2 host and do not need inbound security group rules. For a public production service, place a load balancer and HTTPS in front of the API instead of leaving port `8080` broadly open.

## 2. Connect with SSH

Restrict the key file and connect using Ubuntu's default user:

```bash
chmod 400 reycom-ec2.pem
ssh -i reycom-ec2.pem ubuntu@<EC2_PUBLIC_IP>
```

See the [AWS EC2 SSH documentation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/connect-linux-inst-ssh.html) if the connection is rejected.

## 3. Install Docker Engine and Compose

On the EC2 instance, install Docker from Docker's official Ubuntu repository:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl unzip
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
```

Log out and reconnect so the Docker group change takes effect, then verify:

```bash
docker --version
docker compose version
```

These commands follow Docker's [official Ubuntu installation guide](https://docs.docker.com/engine/install/ubuntu/).

## 4. Install AWS CLI v2

For an x86_64 Ubuntu instance:

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip
unzip awscliv2.zip
sudo ./aws/install
aws --version
```

See the [official AWS CLI installation guide](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) for ARM instances or update instructions.

The attached EC2 IAM role supplies AWS credentials automatically. You should not run `aws configure` with long-lived access keys on the instance.

## 5. Copy the deployment files

Run this command from the ReyCom repository on your local machine:

```bash
scp -i reycom-ec2.pem -r deploy/ec2 ubuntu@<EC2_PUBLIC_IP>:~/reycom-deploy
```

Reconnect to EC2 and enter the deployment directory:

```bash
cd ~/reycom-deploy
chmod +x deploy.sh
```

## 6. Configure the environment

Create the private deployment environment file:

```bash
cp .env.example .env
nano .env
```

Set `AWS_ACCOUNT_ID` to the account that owns ECR. Replace `POSTGRES_PASSWORD` and `JWT_SECRET` with strong values; the JWT secret must contain at least 32 characters. Use single quotes around values containing shell-special characters because `deploy.sh` reads this file.

Do not commit or upload the completed `.env` file to source control.

## 7. Deploy

Run:

```bash
./deploy.sh
```

The script checks its prerequisites, authenticates Docker to ECR, pulls the latest API and supporting images, starts the Compose stack, and displays container status.

The `dynamodb-volume-init` service fixes ownership of the persistent DynamoDB volume before DynamoDB Local starts. The separate `dynamodb-init` service then waits for DynamoDB and creates the order-events table.

Inspect API logs:

```bash
docker compose -f docker-compose.prod.yml logs -f reycom-api
```

## 8. Test the backend

From your local machine:

```bash
curl http://<EC2_PUBLIC_IP>:8080/api/health
```

The response should report `success: true` and service status `UP`.

## 9. Update or stop

After GitHub Actions publishes a new `reycom-api:latest` image, run this again on EC2:

```bash
./deploy.sh
```

The script pulls the new image and recreates the API container when its image changed. Persistent service data remains in named Docker volumes.

Stop the stack without deleting data:

```bash
docker compose -f docker-compose.prod.yml down
```

Deleting named volumes also deletes PostgreSQL, Redis, DynamoDB Local, and Kafka data. Do not add `--volumes` unless a full reset is intended.
