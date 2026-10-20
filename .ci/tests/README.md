# Focused pipeline tests

Run from the repository root with a C++20 host compiler:

```sh
g++ -std=c++20 -pthread -Isrc .ci/tests/single_flight_cache.cpp -o /tmp/moonwitch-cache-test
/tmp/moonwitch-cache-test
```

This test also runs in Android CI. It checks concurrent requests for the same key,
independent-key progress, LRU, value lifetime after eviction, failure retry and
key-byte limits.

With Vulkan-Headers installed (set the include path to its `include` directory):

```sh
g++ -D__ANDROID__ -std=c++20 -pthread -I.ci/tests/vulkan_stubs -Isrc \
  -I/path/to/Vulkan-Headers/include .ci/tests/pipeline_library_runtime.cpp \
  -o /tmp/moonwitch-lto-test
/tmp/moonwitch-lto-test
```

The runtime test compiles the production library cache, real handle wrapper and
worker code, using test-only device/logging declarations and a fake driver.
It blocks optimization to verify that fast linking returns first, checks the
library retain-LTO flag and live dependencies, and verifies successful publication,
failed optimization and orderly destruction. It does not run real GPU commands.
The test include directory must never be added to production builds.
