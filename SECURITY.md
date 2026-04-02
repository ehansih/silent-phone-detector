# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.1.x   | Yes       |
| 1.0.x   | No        |

## Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it privately:

- **Email:** vardhan.chauhan@icloud.com
- **Do not** open a public GitHub issue for security vulnerabilities.

You will receive a response within 48 hours. If confirmed, a patch will be released as soon as possible.

## Security Features in This App

- All permission scanning is read-only — no data is modified on your device.
- Password breach checks use the HIBP k-anonymity API: only 5 characters of a SHA-1 hash are transmitted. The full password never leaves your device.
- No analytics, telemetry, or tracking of any kind.
- No data is sent to any server except the HIBP API (breach check tab, user-initiated only).
