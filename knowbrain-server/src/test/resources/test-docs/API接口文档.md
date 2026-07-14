# Internal API Reference — User Service v2.3

> **Base URL**: `https://api.internal.company.com/v2`
> **Auth**: Bearer token (JWT, issued by SSO)
> **Content-Type**: `application/json; charset=utf-8`

---

## GET /users/{id}

Retrieve a single user by employee ID.

### Path Parameters

| Parameter | Type   | Required | Description           |
|-----------|--------|----------|-----------------------|
| id        | string | yes      | Employee ID (6-digit) |

### Query Parameters

| Parameter | Type   | Default | Description                        |
|-----------|--------|---------|------------------------------------|
| expand    | string | none    | Comma-separated: `dept`, `manager` |

### Response `200 OK`

```json
{
  "id": "E12345",
  "name": "Zhang Wei",
  "email": "zhangwei@company.com",
  "department": {
    "id": "D009",
    "name": "Infrastructure Engineering"
  },
  "manager": {
    "id": "E10001",
    "name": "Liu Qiang"
  },
  "status": "active",
  "created_at": "2022-03-15T09:30:00Z"
}
```

### Error Codes

| Status | Code              | Meaning                        |
|--------|-------------------|--------------------------------|
| 404    | USER_NOT_FOUND    | Employee ID does not exist     |
| 403    | INSUFFICIENT_SCOPE| Token lacks `user:read` scope  |
| 410    | USER_DEPROVISIONED| User was offboarded            |

---

## POST /users/search

Full-text search across employee directory.

### Request Body

```json
{
  "query": "infrastructure engineer beijing",
  "filters": {
    "department": ["D009", "D010"],
    "status": "active",
    "joined_after": "2023-01-01"
  },
  "pagination": {
    "page": 1,
    "size": 20
  }
}
```

### Performance Notes

- Search latency SLA: p95 < 200ms for result sets under 1,000 users
- For full directory export, use `GET /users/export` with async callback
- Rate limit: 100 requests/minute per API key (429 on exceed)

### Example: cURL

```bash
curl -X POST https://api.internal.company.com/v2/users/search \
  -H "Authorization: Bearer $API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query": "data engineer", "pagination": {"page": 1, "size": 5}}'
```

---

## Rate Limiting

All endpoints enforce token-bucket rate limiting:

| Tier   | Burst | Sustained (req/s) | Scope       |
|--------|-------|--------------------|-------------|
| admin  | 100   | 20                 | per user    |
| app    | 50    | 10                 | per API key |
| basic  | 10    | 2                  | per IP      |

When rate-limited, the API returns `429 Too Many Requests` with a
`Retry-After` header indicating the number of seconds to wait.
