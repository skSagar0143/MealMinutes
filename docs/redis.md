# Redis caching

## Current status

Redis is available as a local infrastructure service in `compose.yaml`. Tenant-aware storefront cache integration is being validated in [PR #8](https://github.com/skSagar0143/MealMinutes/pull/8); until that change is merged and its CI is green, do not assume the cache implementation is part of the `main` release.

Redis must remain a cache, not the source of truth for orders, payments or tenant authorization.

## Local Redis

Start Redis with Docker Compose:

```powershell
docker compose up -d redis
docker compose ps redis
docker exec mealminutes-redis redis-cli ping
```

Expected response: `PONG`.

The local Compose service uses a persistent named volume and append-only file (AOF) persistence to make development behavior reproducible. This local setup is not a production Redis security configuration.

## Intended cache design

The tenant-aware storefront cache implementation uses:

- A tenant identifier in every tenant-specific key.
- Version-scoped cache keys to invalidate a tenant's storefront without scanning/deleting wildcard keys.
- TTL-limited serialized menu/profile data.
- A hashed key suffix for item query filters, so raw search text is not exposed in Redis keys.
- Database fallback on cache misses, Redis connection failures or serialization errors.
- Cache invalidation after the business transaction commits, avoiding invalidation for a transaction that later rolls back.

The cache must not be used to authorize access, resolve tenant identity, or hold the only copy of business data. A cache outage should increase database load but must not silently allow cross-tenant reads or block core workflows unless the business explicitly chooses a fail-closed policy.

## Configuration

The intended configuration keys are:

| Property | Default | Purpose |
|---|---|---|
| `mealminutes.redis.enabled` | `false` | Enables storefront caching |
| `mealminutes.redis.cache-ttl-seconds` | `60` | TTL, bounded by the implementation |
| `spring.data.redis.host` | `localhost` | Redis hostname |
| `spring.data.redis.port` | `6379` | Redis port |
| `spring.data.redis.password` | empty | Optional local password |

Production must configure authentication, TLS, network restrictions, bounded connection/read timeouts, secret rotation and monitoring. Do not expose Redis directly to the public internet.

## Required tests before enabling in production

- [ ] Two tenants cannot read each other's cached profile, categories or items.
- [ ] Menu writes invalidate only the current tenant and only after commit.
- [ ] Rollback does not invalidate a cache as if the write committed.
- [ ] Redis outage/timeout falls back safely and produces bounded logs.
- [ ] Cache TTL and version-key behavior are verified against a real Redis instance.
- [ ] Sensitive values and raw user search text are not stored in keys or logs.
- [ ] Database load is measured for a Redis outage and cache cold start.

See [implementation plan](implementation-plan.md) for release status.
