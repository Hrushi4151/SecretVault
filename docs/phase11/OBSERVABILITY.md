# SecretVault SDK — Observability & Telemetry

## SDK Metrics (`SdkMetrics`)

- `secretvault_requests_total`: Total outbound API requests.
- `secretvault_request_failures_total`: Total request failures.
- `secretvault_cache_hits_total`: Total cache hits (<1ms latency).
- `secretvault_cache_misses_total`: Total cache misses requiring backend fetch.
- `secretvault_refresh_total`: Number of dynamic secret refreshes executed.
- `secretvault_circuit_open_total`: Count of circuit breaker trips.

*Note: High-cardinality secret names or sensitive payload values are NEVER used as metric labels.*
