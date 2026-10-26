# Redis caching

## Current status

Redis is available as a local infrastructure service in `compose.yaml`. Tenant-aware storefront caching was merged to `main` in [commit cc0d5f5](https://github.com/skSagar0143/MealMinutes/commit/cc0d5f575f701c980c17caa2797dbc05d96eb442). Its backend CI passed 204 tests on the feature branch; the post-merge main-branch CI run is still pending. Unit tests cover tenant-scoped versioned keys, TTL, after-commit invalidation, missing-context bypass and safe fallback on Redis failures. Real-Redis end-to-end cache tests and load/outage testing remain release gates.

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

- [ ] A real-Redis integration test proves two tenants cannot read each other's cached profile, categories or items.
- [x] Unit tests verify version-based invalidation is scoped to the current tenant and runs after commit.
- [ ] Add a transaction rollback integration test proving cache invalidation does not run for rolled-back writes.
- [ ] Redis outage/timeout falls back safely and produces bounded logs.
- [ ] Cache TTL and version-key behavior are verified against a real Redis instance.
- [ ] Sensitive values and raw user search text are not stored in keys or logs.
- [ ] Database load is measured for a Redis outage and cache cold start.

See [implementation plan](implementation-plan.md) for release status.
