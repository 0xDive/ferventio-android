# Contributing

Small, focused changes are preferred while Ferventio is in early beta.

## Before you start

- Search existing issues and pull requests.
- Open an issue before large UI, protocol, database or architecture changes.
- Keep backend changes in [`0xDive/ferventio-backend`](https://github.com/0xDive/ferventio-backend).
- Do not include credentials, private user data, generated build output or third-party assets without redistribution rights.

## Workflow

```bash
git switch main
git pull --ff-only
git switch -c fix/short-description
```

Run the checks relevant to your change. Before opening a pull request, run the complete local gate from the README when possible.

Visible UI changes should include sanitized before/after screenshots. Room schema changes must include exported schemas, migrations and migration tests.

UI text changes must follow [the localization workflow](docs/localization.md) and pass `python3 scripts/localization/check_ui_localization.py`.

## Commit and pull request style

Use short, imperative commit subjects, for example:

```text
chat: preserve reply draft after reconnect
```

A pull request should explain:

- the problem and chosen solution
- user-visible impact
- affected FOSS/Play flavors
- database or protocol compatibility
- tests performed
- rollback considerations

The project follows the account-level [Code of Conduct](https://github.com/0xDive/.github/blob/main/CODE_OF_CONDUCT.md).
