ARG version
FROM clojure:temurin-17-alpine AS builder

RUN mkdir -p /build
WORKDIR /build

COPY deps.edn /build/
RUN clojure -P -X:build

COPY ./ /build
RUN clojure -T:build uber

FROM eclipse-temurin:17-alpine AS final

LABEL org.opencontainers.image.source=https://github.com/HughPowell/clj-htmx-contacts
LABEL org.opencontainers.image.description="The HTMX Contacts App written in Clojure"
LABEL org.opencontainers.image.licenses=MPL-2.0
LABEL org.opencontainers.image.version=1.0.$version

RUN apk add --no-cache \
    dumb-init~=1.2.5

ARG UID=10001
RUN adduser \
    --disabled-password \
    --gecos "" \
    --home "/nonexistent" \
    --shell "/sbin/nologin" \
    --no-create-home \
    --uid "${UID}" \
    clojure

RUN addgroup -S hughpowell && adduser -S hughpowell -G hughpowell

RUN mkdir -p /service && chown -R clojure. /service

USER clojure

WORKDIR /service
COPY --from=builder --chown=clojure:clojure /build/target/clj-htmx-contacts.jar /service/clj-htmx-contacts.jar

EXPOSE 3000

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["java", "-jar", "/service/clj-htmx-contacts.jar"]
