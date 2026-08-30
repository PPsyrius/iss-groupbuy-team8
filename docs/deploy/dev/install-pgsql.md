Below is a PostgreSQL 18 + Docker Compose installation process for AWS EC2 on Ubuntu 24.04. The modern Compose command is `docker compose`, and the older `docker-compose` is no longer recommended.

## 1. Preparing AWS EC2

Recommendations:

- Ubuntu Server 24.04 LTS.

- At least `2 vCPUs / 4 GB RAM` for the database production environment, adjust according to workload.

- Use a dedicated EBS `gp3` disk for the data disk and enable EBS encryption.

- Place EC2 on a private subnet if possible.

- Security Groups:

- TCP 22: Allow only management IPs.

- TCP 5432: Allow only the application server's security group or private network CIDR.

- Do not expose 5432 as `0.0.0.0/0`.

AWS security groups control inbound access through source security groups or CIDRs. See [AWS EC2 Security Group Rules](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-rules-reference.html).

## 2. Install Docker and Compose

SSH Login to the Server:

```bash

# In the dir of the private key

ssh -i "BlocBuy-dev.pem" ubuntu@ec2-47-129-51-142.ap-southeast-1.compute.amazonaws.com

```

Configure the Docker Official Software Repository:

```bash

sudo apt update

sudo apt install -y ca-certificates curl

sudo install -m 0755 -d /etc/apt/keyrings

sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \

-o /etc/apt/keyrings/docker.asc

sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update

sudo apt install -y \
docker-ce \
docker-ce-cli \
containerd.io \
docker-buildx-plugin \
docker-compose-plugin

```

Start and verify:

```bash

sudo systemctl enable --now docker

sudo docker run --rm hello-world

sudo docker compose version

```

Ubuntu 24.04 is an officially supported Docker system. For installation steps, see [Docker Engine] For Ubuntu, refer to [https://docs.docker.com/engine/install/ubuntu/](https://docs.docker.com/engine/install/ubuntu/) and [Docker Compose plugin installation instructions](https://docs.docker.com/compose/install/linux/).

To avoid using `sudo`:

```bash
sudo usermod -aG docker "$USER"

```

Then exit SSH and log back in. Note: Joining the `docker` group is essentially equivalent to gaining root privileges.

## 3. Create the deployment directory and password

```bash
sudo mkdir -p /opt/postgres18/secrets
sudo chown -R "$USER":"$USER" /opt/postgres18
cd /opt/postgres18

openssl rand -base64 36 > secrets/db_password

chmod 600 secrets/db_password

```

Do not commit the password file to Git.

## 4. Create `compose.yaml`

```yaml
name: postgres18

services: 
postgres: 
image: postgres:18 
restart: unless-stopped 

environment: 
POSTGRES_DB: appdb 
POSTGRES_USER: pgadmin 
POSTGRES_PASSWORD_FILE: /run/secrets/db_password 

secrets: 
-db_password 

# By default only local access is allowed 
ports: 
- "127.0.0.1:5432:5432" 

volumes: 
- pgdata:/var/lib/postgresql 

healthcheck: 
test: ["CMD-SHELL", "pg_isready -U pgadmin -d appdb"] 
interval: 10s 
timeout: 5s 
retries: 10 
start_period: 20s `stop_grace_period: 1m`

logging:

driver: json-file

options:

max-size: "10m"

max-file: "5"

secrets:

db_password:

file: ./secrets/db_password

volumes:

pgdata:

```

An important change in the official PostgreSQL 18 image: persistent volumes should be mounted to `/var/lib/postgresql`, and the default data directory inside the image is `/var/lib/postgresql/18/docker`; do not copy the `/var/lib/postgresql/data` configuration commonly used in PostgreSQL 17 and earlier. [PostgreSQL Official Docker Image Description](https://hub.docker.com/_/postgres)

Port configuration can be adjusted according to the scenario:

- The application is also in the same Compose: you can delete `ports`, and the application connects to `postgres:5432`.

```` - If the application is running on the same EC2 instance: retain `127.0.0.1:5432:5432`.

- If the application is running on another EC2 instance: bind the EC2 instance to a private IP address and allow only the application server to access it in the AWS security group.

- It is not recommended to expose PostgreSQL directly to the public internet.

## 5. Startup and Verification

```bash
cd /opt/postgres18

sudo docker compose config

sudo docker compose pull

sudo docker compose up -d

sudo docker compose ps

sudo docker compose logs --tail=100 postgres

docker ps

sudo docker update --restart=unless-stopped <containerID>

```

Check version:

```bash
docker compose exec postgres \
psql -U pgadmin -d appdb -c "SELECT version();"

```

Check health status:

```bash
docker inspect \
--format='{{json .State.Health}}' \
postgres18-postgres-1

```