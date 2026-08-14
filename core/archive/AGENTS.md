# core/archive/ Module

CBZ/archive reading with optional AES encryption (SY). Package root: `mihon.core.archive.*`.

## Key classes

| Class | Purpose |
|-------|---------|
| `ArchiveReader` | Memory-mapped archive reading via libarchive (JNI). Detects encryption on open. |
| `ArchiveInputStream` | InputStream over mmap'd archive with passphrase support |
| `ArchiveEntry` | Archive entry model (name, isFile, isEncrypted) |
| `CbzCrypto` | AES-256/128/ZipCrypto encryption via Android KeyStore |
| `EpubReader` | EPUB format parser over ArchiveReader (OPF, spine, image extraction) |
| `ZipWriter` | Archive writing with optional encryption (SY) |

## Dependencies

- `core:common` (ImageUtil, SecurityPreferences, Injekt DI)
- `libarchive` (native C library via JNI)
- `UniFile` (storage abstraction)

## Encryption (SY)

- Two key aliases: `cbzPw` (CBZ archive) and `sqlPw` (SQL database)
- Password stored encrypted in SharedPreferences as Base64-encoded (IV + ciphertext)
- `createComicInfoPadding()` adds random padding to ComicInfo.xml (anti-fingerprinting)

## Conventions

- All archive I/O uses `Os.mmap()` for zero-copy reads
- Fork markers: `// SY -->` for TachiyomiSY encryption additions
