# Disruptor Wait Strategy — Decision Matrix

The trading engine's Disruptor ring buffer uses a configurable `WaitStrategy` to
decide how the consumer thread behaves when the ring is empty. The choice
trades **CPU cost** against **wake latency** (time from "event published" to
"consumer observes and dispatches the event"). Wrong choice = wasted hardware
or latency spikes during quiet periods.

Configured via `hft.engine.wait-strategy` in `application.properties`. Default
is `SLEEPING`, intentionally conservative so the app behaves nicely on dev
laptops. Production deployments should override.

| Strategy     | CPU        | Wake latency    | Use when                                         | Avoid when                               |
|--------------|------------|-----------------|--------------------------------------------------|------------------------------------------|
| `BUSY_SPIN`  | 100% of 1 core | < 1 us        | Dedicated isolated core (Linux `isolcpus`), live HFT | Shared/oversubscribed hosts; battery-powered dev machines |
| `YIELDING`   | ~100% but yields | 1-10 us     | Prod without a fully isolated core, bursty flow   | Single-vCPU VMs (no peer to yield to)    |
| `SLEEPING`   | Low        | 1-100 us        | Dev, backtesting, non-latency-critical prod       | True HFT where quiet periods are rare    |
| `BLOCKING`   | Minimal    | 10-1000 us      | Unit tests, BDD scenarios, batch jobs             | Any real-time trading path               |

## How to pick

1. **Is this production and is every microsecond on the consumer thread
   load-bearing?** → `BUSY_SPIN` **if and only if** you have a dedicated core
   reserved for the Disruptor consumer (see Phase 1 affinity plan and Linux
   `isolcpus` / `nohz_full` kernel args). Otherwise `BUSY_SPIN` will burn the
   host and hurt neighbouring work without buying you tight tail latency.

2. **Production, no dedicated core?** → `YIELDING`. Cheaper than busy-spin
   under contention, still sub-10 us wake.

3. **Dev, backtest, or batch?** → `SLEEPING`. Default. Laptop-friendly.

4. **Pure unit test?** → `BLOCKING` (lowest CPU, wake latency doesn't matter
   because the test controls the pacing).

## Interaction with thread affinity

The `BUSY_SPIN` strategy's value disappears entirely if the OS migrates the
consumer thread off its core: every migration destroys L1 and L2 cache state
and the "low wake latency" is then dominated by cache-miss cost. If you're
running `BUSY_SPIN` in production you should also:

- Enable `hft.engine.pin-consumer-thread=true` (Phase 1 wiring).
- On Linux, boot with `isolcpus=<N>` and `nohz_full=<N>` reserving the core
  for the Disruptor consumer, then `taskset -c <N>` the whole JVM.
- Confirm `net.openhft:affinity` is picking up the pin (log line from
  `AffinityLock` at engine init — emitted on macOS too but ignored by the
  kernel there).

## Benchmark context

`hft-bdd/src/test/java/com/hft/bdd/benchmark/EndToEndLatencyBenchmark.java`
uses `YieldingWaitStrategy` by default. Phase 1 / Phase 2 latency tables in
`local-docs/` reflect that. Switching to `BUSY_SPIN` on an isolated Linux core
is expected to knock another ~0.5 us off p50 and tighten p99/p99.9
meaningfully — not yet measured on this repo's hardware.
