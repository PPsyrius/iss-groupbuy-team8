# Redis 8.10.0 on Ubuntu 24.04 with Docker Compose

`redis:8.10.0` is a valid official image. However, as of August 30, 2026, Redis 8.10.1 is the newer patch release. Use 8.10.0 if you specifically require it; otherwise replace `8.10.0` with `8.10.1` below. See the [official Redis image tags](https://hub.docker.com/_/redis/tags?name=8.10).

This setup provides:

- Redis 8.10.0 pinned to an exact version
- A named volume for persistent data
- AOF persistence plus RDB snapshots
- Password authentication
- Localhost-only host exposure
- Container health checking
- Automatic restart and log rotation

## 1. Install Docker Engine and Compose
> See [pgsql-guide](install-pgsql.md)
## 2. Apply recommended Redis host settings

Redis recommends enabling memory overcommit so background persistence operations can fork successfully:

```bash
printf 'vm.overcommit_memory = 1\n' |
  sudo tee /etc/sysctl.d/99-redis.conf >/dev/null

sudo sysctl --system
```

On a dedicated Redis host, disable Transparent Huge Pages to avoid memory and latency spikes:

```bash
echo never |
  sudo tee /sys/kernel/mm/transparent_hugepage/enabled >/dev/null
```

That THP change lasts only until reboot. To make it persistent:

```bash
sudo tee /etc/systemd/system/disable-transparent-huge-pages.service \
  >/dev/null <<'EOF'
[Unit]
Description=Disable Transparent Huge Pages for Redis
DefaultDependencies=no
After=sysinit.target local-fs.target
Before=docker.service

[Service]
Type=oneshot
ExecStart=/bin/sh -c 'echo never > /sys/kernel/mm/transparent_hugepage/enabled'
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now disable-transparent-huge-pages.service
```

Redis recommends both settings in its [production administration guidance](https://redis.io/docs/latest/operate/oss_and_stack/management/admin/). If this host runs other memory-sensitive software, assess the system-wide THP change first.

## 3. Create the project directory

```bash
sudo install -d -m 0750 \
  -o "$USER" \
  -g "$(id -gn)" \
  /opt/redis-8.10

cd /opt/redis-8.10

install -d -m 0700 secrets
```

The resulting layout will be:

```text
/opt/redis-8.10/
├── compose.yaml
└── secrets/
    └── redis.conf
```

## 4. Create a strong password and Redis configuration

The following generates a 256-bit hexadecimal password and writes the Redis configuration without printing the password to the terminal:

```bash
umask 077
redis_secret="$(openssl rand -hex 32)"

printf '%s\n' \
  'bind 0.0.0.0' \
  'protected-mode yes' \
  'port 6379' \
  'tcp-backlog 511' \
  'timeout 0' \
  'tcp-keepalive 300' \
  'daemonize no' \
  'supervised no' \
  'loglevel notice' \
  'databases 16' \
  'dir /data' \
  'dbfilename dump.rdb' \
  'save 900 1' \
  'save 300 10' \
  'save 60 10000' \
  'stop-writes-on-bgsave-error yes' \
  'rdbcompression yes' \
  'rdbchecksum yes' \
  'appendonly yes' \
  'appendfilename appendonly.aof' \
  'appenddirname appendonlydir' \
  'appendfsync everysec' \
  "requirepass $redis_secret" \
  > secrets/redis.conf

unset redis_secret
chmod 0644 secrets/redis.conf
```

Although the file is mode `0644` so the unprivileged Redis container process can read it, its parent directory is mode `0700`, preventing other host users from traversing it.

AOF with `appendfsync everysec` offers a practical performance/durability balance, with approximately one second of data potentially at risk during a host failure. With both AOF and RDB enabled, Redis normally uses AOF on restart because it is more complete. See [Redis persistence](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/).

## 5. Create `compose.yaml`

```bash
tee compose.yaml >/dev/null <<'EOF'
name: redis810

services:
  redis:
    image: redis:8.10.0
    restart: unless-stopped
    command:
      - redis-server
      - /usr/local/etc/redis/redis.conf

    ports:
      - "127.0.0.1:6379:6379"

    volumes:
      - redis_data:/data
      - ./secrets/redis.conf:/usr/local/etc/redis/redis.conf:ro

    healthcheck:
      test:
        - CMD-SHELL
        - >-
          REDISCLI_AUTH=$$(sed -n 's/^requirepass //p'
          /usr/local/etc/redis/redis.conf)
          redis-cli --no-auth-warning ping | grep -q '^PONG$$'
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

    stop_grace_period: 30s

    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"

volumes:
  redis_data:
    name: redis810-data
EOF
```

Important details:

- `redis:8.10.0` prevents an accidental major or minor upgrade.
- The named volume `redis810-data` survives container replacement.
- Port 6379 is published only on `127.0.0.1`, not every host interface.
- The password is not embedded in `compose.yaml` or a container environment variable.
- No obsolete top-level Compose `version:` field is needed.
- The official image drops privileges to its `redis` user by default. Avoid `SKIP_DROP_PRIVS=1`. See the [official Redis Docker image documentation](https://hub.docker.com/_/redis).

Validate the file:

```bash
sudo docker compose config
```

## 6. Pull and start Redis

```bash
sudo docker compose pull
sudo docker compose up -d
```

Check status:

```bash
sudo docker compose ps
sudo docker compose logs --tail=100 redis
```

Within several seconds, the service should show `healthy`.

## 7. Verify the version and authentication

Load the password into a temporary shell variable:

```bash
redis_secret="$(sed -n 's/^requirepass //p' secrets/redis.conf)"
```

Check connectivity:

```bash
sudo docker compose exec \
  -e REDISCLI_AUTH="$redis_secret" \
  redis redis-cli --no-auth-warning PING
```

Expected result:

```text
PONG
```

Verify the precise Redis version:

```bash
sudo docker compose exec \
  -e REDISCLI_AUTH="$redis_secret" \
  redis redis-cli --no-auth-warning INFO server |
  grep '^redis_version:'
```

Expected result:

```text
redis_version:8.10.0
```

Test reading and writing:

```bash
sudo docker compose exec \
  -e REDISCLI_AUTH="$redis_secret" \
  redis redis-cli --no-auth-warning SET install:test "Redis works"

sudo docker compose exec \
  -e REDISCLI_AUTH="$redis_secret" \
  redis redis-cli --no-auth-warning GET install:test
```