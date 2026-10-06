# Memory Pressure Manager

The Vulkan allocator uses a lightweight memory-pressure policy on every platform where VMA heap
budgets are available. The implementation intentionally stays conservative:

- samples heap budgets every 32 allocations;
- switches VMA allocation strategy from minimum-time to minimum-memory at critical pressure;
- uses hysteresis before returning to the normal strategy;
- never changes render resolution, resource lifetime, or guest-visible memory behavior;
- keeps the existing allocator fallbacks when an allocation cannot stay within budget.

There is no user-facing toggle because this is allocator policy rather than a rendering feature.
The policy is designed to be transparent when memory pressure is low and to reduce fragmentation
risk near the device budget without introducing forced cache eviction or synchronization.
