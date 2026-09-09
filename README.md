# grindtrack

Self-hosted tracker for a 5-year engineering study plan.

- **[gt2/](gt2/)** — current version: Spring Boot 3.5 / Java 21 + React 18 / TypeScript + Postgres 16.
  Start with [gt2/README.md](gt2/README.md); full docs live in [gt2/docs/](gt2/docs/).
- **[tracker/](tracker/)** — v1 (Node), kept for reference and data export.

Deployed to a kubeadm cluster on Hetzner Cloud at `track.caseyrquinn.com`. The cluster is defined
in the separate [`k8s-cluster-hetzner`](https://github.com/caseythecoder90/k8s-cluster-hetzner)
repo — see [gt2/docs/deployment.md](gt2/docs/deployment.md).
