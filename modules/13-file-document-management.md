# Module 13 — File & Document Management

> **Tier:** 2 — Platform Services
> **Status:** In Design
> **Dependencies:** Module 02, Module 07 (Auth)

---

## 1. Purpose

GRC activities generate significant documentation — policy documents, test evidence, vendor contracts, assessment questionnaires, audit workpapers, certificates. This module handles secure file upload, storage, access control, versioning, and retrieval for all attachments across the platform.

---

## 2. Design Constraints

- Files are **associated with records**, not standalone
- File content is stored in external blob storage (not in the database)
- Metadata (name, size, type, scan status) is stored in `record_attachments` (SQL Server)
- Access to download a file **requires** record read permission
- All uploads are scanned for malware (hookpoint defined, actual scanner is configurable)
- No file can be downloaded without a valid, short-lived signed URL

---

## 3. Storage Backends

The storage layer is abstracted via a `StorageProvider` interface. The active provider is configured via application config:

| Provider | Config Key | Use Case |
|----------|-----------|----------|
| Azure Blob Storage | `azure_blob` | Cloud/Azure deployments |
| AWS S3 | `s3` | Cloud/AWS deployments |
| Local filesystem | `local` | Development only |

```java
public interface StorageProvider {
    String upload(InputStream content, String filename, String mimeType, long sizeBytes);
    InputStream download(String storagePath);
    void delete(String storagePath);
    SignedUrl generateDownloadUrl(String storagePath, Duration ttl);
}
```

Storage path format: `{orgId}/{appKey}/{recordId}/{fileId}/{originalName}`

---

## 4. File Upload Flow

```
Client                          API                             Storage
  │                              │                                │
  │─ POST /api/v1/files/upload ──►│                               │
  │  (multipart: file, recordId) │                               │
  │                              │─── validate file (size/type) ─►│
  │                              │─── generate fileId            │
  │                              │─── stream to Storage ─────────►│
  │                              │         ← storage path ────────│
  │                              │─── insert record_attachments  │
  │                              │    (status: pending_scan)     │
  │                              │─── queue scan job (async)     │
  │◄── { fileId, scanStatus } ───│                               │
  │                              │                               │
  │  [async: virus scan]         │                               │
  │                              │─── scan_complete event ───────│
  │                              │─── update scan_status         │
  │                              │─── notify user if infected    │
```

### 4.1 File Validation (Before Upload)

```java
public void validateUpload(MultipartFile file) {
    // Size limit
    if (file.getSize() > maxFileSizeBytes) {
        throw new ValidationException("File exceeds maximum size of " + maxFileSizeMb + "MB");
    }
    // Allowed MIME types
    if (!allowedMimeTypes.contains(file.getContentType())) {
        throw new ValidationException("File type not allowed: " + file.getContentType());
    }
    // Extension vs MIME type match (prevent spoofing)
    String detectedType = tika.detect(file.getInputStream());
    if (!detectedType.equals(file.getContentType())) {
        throw new ValidationException("File extension does not match content");
    }
    // File name sanitization
    String safeName = sanitizeFilename(file.getOriginalFilename());
    // No path traversal, no special characters
}
```

### 4.2 Allowed File Types (Default)

```
Documents: application/pdf, application/msword, 
           application/vnd.openxmlformats-officedocument.wordprocessingml.document,
           application/vnd.ms-excel, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,
           application/vnd.ms-powerpoint, text/plain, text/csv

Images: image/png, image/jpeg, image/gif, image/webp

Archives: application/zip (only if configured; contents not auto-extracted)
```

Configurable per org. Executable files (`.exe`, `.bat`, `.sh`, `.js`, `.py`) are **never** permitted regardless of config.

---

## 5. Virus Scanning

Scanning is integrated via a configurable adapter:

```java
public interface VirusScanAdapter {
    ScanResult scan(String storagePath, String fileId);
}

// Implementations:
// - ClamAvScanAdapter (open source, self-hosted)
// - DefenderScanAdapter (Windows Defender via REST API)
// - NoOpScanAdapter (development mode, always returns 'clean')
```

| Scan Result | Behavior |
|-------------|----------|
| `clean` | File available for download |
| `infected` | File quarantined, user notified, admin alerted |
| `scan_failed` | Retried 3×; after failure, admin notified; file held pending |
| `skipped` | Scanning disabled (dev mode only) |

---

## 6. Download — Signed URL

Files are **never** served directly through the application server in production. A short-lived pre-signed URL is generated:

```
GET /api/v1/files/{fileId}/download
Authorization: Bearer {token}

→ 302 Redirect to:
  https://storage.example.com/grc/{path}?signature=...&expires=1712197200
  (Valid for 5 minutes)
```

```java
public String generateDownloadUrl(UUID fileId, UUID requestingUserId, UUID orgId) {
    RecordAttachment attachment = attachmentRepository.findById(fileId)
        .filter(a -> a.orgId().equals(orgId))
        .orElseThrow(() -> new NotFoundException(fileId));

    // Check record read permission
    permissionEvaluator.requireRecordRead(attachment.recordId(), requestingUserId);

    // Check scan status
    if (attachment.scanStatus() == ScanStatus.INFECTED) {
        throw new ForbiddenException("File is quarantined");
    }
    if (attachment.scanStatus() == ScanStatus.PENDING) {
        throw new ServiceUnavailableException("File is pending security scan");
    }

    // Log the download in audit log
    auditService.logFileDownload(orgId, fileId, requestingUserId);

    // Generate signed URL (5 minute TTL)
    return storageProvider.generateDownloadUrl(attachment.storagePath(), Duration.ofMinutes(5));
}
```

---

## 7. File Versioning

When a new file is uploaded to a field that already has an attachment, the previous version is retained (not deleted):

```
field_def: "policy_document" (field type: attachment)
  Version 1: Policy_v1.0.pdf (2025-01-15)
  Version 2: Policy_v1.1.pdf (2025-06-01)  ← current version
  Version 3: Policy_v2.0.pdf (2026-01-01)  ← latest
```

In `record_attachments`, the `version` column tracks the version number per field. The current version is the highest version number for that (`record_id`, `field_def_id`) pair.

---

## 8. Database Schema

Already defined in Module 02. Restated for reference:

```sql
CREATE TABLE record_attachments (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    record_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    field_def_id    UNIQUEIDENTIFIER  NULL,         -- NULL = general (not field-specific)
    original_name   NVARCHAR(500)     NOT NULL,
    storage_path    NVARCHAR(2000)    NOT NULL,
    mime_type       NVARCHAR(200)     NOT NULL,
    file_size_bytes BIGINT            NOT NULL,
    checksum_sha256 NCHAR(64)         NOT NULL,
    scan_status     NVARCHAR(20)      NOT NULL DEFAULT 'pending',
    version         INT               NOT NULL DEFAULT 1,
    is_deleted      BIT               NOT NULL DEFAULT 0,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by      UNIQUEIDENTIFIER  NOT NULL
);
```

---

## 9. GraphQL API

```graphql
type Mutation {
  deleteAttachment(id: UUID!, reason: String): Boolean!
}

type Query {
  recordAttachments(recordId: UUID!): [RecordAttachment!]!
  attachmentVersions(recordId: UUID!, fieldKey: String!): [RecordAttachment!]!
}

type RecordAttachment {
  id:            UUID!
  originalName:  String!
  mimeType:      String!
  fileSizeBytes: Int!
  fileSizeLabel: String!   # e.g. "2.4 MB"
  scanStatus:    ScanStatus!
  downloadUrl:   String!   # short-lived signed URL, generated per request
  version:       Int!
  fieldKey:      String
  createdBy:     User!
  createdAt:     DateTime!
}
```

---

## 10. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | Maximum file size? Suggest 200MB default, configurable per org. | Medium |
| 2 | Should archived records' attachments be migrated to cheaper storage tier? | Low |
| 3 | GDPR: if a record is "erased", should its attachments also be deleted from blob storage? | High |
| 4 | Should there be a general document library (not attached to a specific record)? | Medium |
| 5 | Bulk download (zip of all attachments for a record or report)? | Low |

---

*Previous: [12 — Reporting & Dashboards](12-reporting-dashboards.md) | Next: [14 — Integration Framework](14-integration-framework.md)*
