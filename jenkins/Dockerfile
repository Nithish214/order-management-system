# The official Jenkins image has no Docker CLI at all -- this pipeline's build stages
# shell out to `docker build`/`docker save` directly (see the repo-root Jenkinsfile), so
# without this, every one of those steps would just fail with "docker: command not
# found". Adding the CLI here, rather than installing it as a Jenkinsfile step on every
# single run, means it's baked into the image once instead of re-downloaded every build.
#
# This does NOT include a Docker daemon -- Jenkins talks to the HOST's daemon instead
# (see jenkins/docker-compose.yml's volume mount of /var/run/docker.sock), the standard
# "Docker outside of Docker" pattern. That's what makes an image Jenkins builds inside
# this container immediately visible to `docker images` on the host too, and lets
# `docker save`/`docker load` actually work without a second, isolated daemon involved.
FROM jenkins/jenkins:lts-jdk17

USER root

# NOT `apt-get install docker.io` -- found by actually testing the image, not assuming:
# on this base image's Debian release, that package installs dockerd/docker-proxy but
# genuinely omits the `docker` CLI binary itself, so every `sh 'docker ...'` step in the
# Jenkinsfile would fail with "docker: command not found." Docker's own static CLI-only
# tarball sidesteps whatever that packaging gap is entirely -- it's just the one binary
# this image actually needs (there's no daemon running in here at all; see
# jenkins/docker-compose.yml's docker.sock mount for why a CLI alone is enough).
ARG DOCKER_CLI_VERSION=27.3.1
RUN apt-get update && apt-get install -y --no-install-recommends curl openssh-client \
    && curl -fsSL "https://download.docker.com/linux/static/stable/x86_64/docker-${DOCKER_CLI_VERSION}.tgz" \
       | tar -xz --strip-components=1 -C /usr/local/bin docker/docker \
    && rm -rf /var/lib/apt/lists/*

# Stays root, deliberately -- not the usual jenkins/jenkins image default. The mounted
# /var/run/docker.sock's group ownership varies by Docker Desktop's own setup on
# Windows/WSL2, and chasing that down with a matching GID inside the image is real
# complexity for zero benefit on a single-user local learning setup like this one
# (the usual reason to run Jenkins as a non-root user -- defense in depth on a
# shared/multi-tenant server -- doesn't apply here). Root can always reach the socket
# regardless of its group, which is what actually matters for this pipeline to work.
