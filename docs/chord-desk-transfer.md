# Chord Desk → Light Chord transfer (v1)

How Chord Desk (desktop) sends ChordPro files to the Light Chord tool on the
Light Phone over the local Wi-Fi. Works on any LightOS: it does not use the
system Tool Manager.

## Roles

- **Chord Desk** is the server. While a send is open it runs a temporary HTTP
  server on the LAN and shows a QR code.
- **Light Chord** is the client. Chords › + › Receive from Chord Desk opens
  the QR scanner, then downloads the files.

## QR payload

A plain URL:

```
http://<lan-ip>:<port>/lightchord/v1?t=<token>
```

- `<lan-ip>`: the desktop's IPv4 address on the active LAN interface.
- `<port>`: any free port (Chord Desk picks one).
- `<token>`: 32 lowercase hex chars (128 random bits), one per send session.

Light Chord accepts only `http://` URLs whose path is `/lightchord/v1` and that
carry `t`. Anything else shows "Not a Chord Desk code".

## Endpoints

All requests carry the token as the `t` query parameter. A wrong or expired
token gets `401`. Bodies are UTF-8.

### `GET /lightchord/v1/manifest?t=…`

```json
{
  "version": 1,
  "sender": "Chord Desk",
  "files": [
    { "id": 0, "name": "Neil Young - Heart of Gold.cho", "size": 3449, "sha256": "…" }
  ]
}
```

`name` is a bare file name (no `/` or `\`), ending in `.cho`, `.chopro`,
`.crd` or `.txt`. At most 200 files, each at most 256 KiB.

### `GET /lightchord/v1/file/<id>?t=…`

The raw file bytes, `Content-Type: text/plain; charset=utf-8`,
`Content-Length` set. Chord Desk counts served files for its progress bar.

### `POST /lightchord/v1/done?t=…`

Sent once by the phone at the end:

```json
{ "received": ["a.cho"], "replaced": ["b.cho"], "skipped": ["c.cho"], "failed": [{ "name": "d.cho", "reason": "too large" }] }
```

Chord Desk answers `204`, shows the result ("7 songs received"), stops the
server and invalidates the token.

## Rules

- **Session lifetime.** The token is valid from when the QR is shown until
  `done`, Cancel, or 10 minutes, whichever comes first.
- **Duplicates.** The phone compares each file with the same name in its songs
  folder. Same SHA-256 → skipped silently. Different → the phone asks once for
  the batch: Replace or Skip.
- **Validation on the phone.** Reject names with path separators, other
  extensions, files over 256 KiB, or a body whose SHA-256 differs from the
  manifest. Files are written to `files/shared/songs/`, then the library is
  re-indexed.
- **Status in Chord Desk.** Waiting for phone → Sending n/N → Done (summary) or
  Failed (reason). There is no persistent connection indicator.
- **Security.** Plain HTTP on the local network, protected by the one-time
  token. Chord Desk binds only while a send is open. Say "Same Wi-Fi only" in
  both UIs.
