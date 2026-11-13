# Local operator tooling only; no credentials or application files enter this image.
FROM public.ecr.aws/aws-cli/aws-cli:2.39.7@sha256:73f81e380c7974c9f03ac7ccd520ef080f17f443e10582f024c828b970ac8b73
RUN yum install -y git jq tar gzip unzip openssl && yum clean all
RUN curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
      https://releases.hashicorp.com/terraform/1.16.4/terraform_1.16.4_linux_amd64.zip -o /tmp/terraform.zip \
    && printf '%s  /tmp/terraform.zip\n' dc94af0eef1147718ad7c8daea792ed199e3e0492eec180d0adafa2a65a879df | sha256sum -c - \
    && unzip -q /tmp/terraform.zip -d /usr/local/bin \
    && rm /tmp/terraform.zip \
    && git config --system --add safe.directory /repo
ENTRYPOINT ["/bin/bash"]
