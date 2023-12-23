# clj-htmx-contacts

This is a project to
1. Work through the contacts app example in the [htmx/hypermedia system (same thing) book](https://hypermedia.systems/).
2. Demo the minimum infrastructure needed for a modern software engineering product.

## Development

To run the service locally

1. Install the
[HCP Vault Secrets CLI](https://developer.hashicorp.com/vault/tutorials/hcp-vault-secrets-get-started/hcp-vault-secrets-install-cli).
2. Start your REPL (this project assumes the `user` namespace is loaded by default).
3. Execute `(user/reset)` to start the service.

## Currently used infrastructure

### SaaS

* [Auth0](https://auth0.com) User management
* [Neon](https://neon.tech) Persistent storage
* [Hashicorp Vault Secrets](https://www.hashicorp.com/products/vault) Secrets Management
* [GitHub](https://github.com) Repository management and CI/CD pipelines

### Local software

* [Clojure](https://clojure.org)
* [Docker](https://www.docker.com)
