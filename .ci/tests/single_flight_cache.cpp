// SPDX-FileCopyrightText: Copyright 2026 Moonwitch contributors
// SPDX-License-Identifier: GPL-3.0-or-later
#include <atomic>
#include <barrier>
#include <cassert>
#include <future>
#include <iostream>
#include <stdexcept>
#include <thread>
#include <vector>
#include "common/single_flight_cache.h"

int main() {
    Common::SingleFlightCache<int> cache{2, 1024};
    std::atomic<int> builds{};
    std::barrier start{9};
    std::promise<void> entered, release;
    auto gate = release.get_future().share();
    std::vector<std::future<std::shared_ptr<int>>> calls;
    for (int i = 0; i < 8; ++i) {
        calls.emplace_back(std::async(std::launch::async, [&] {
            start.arrive_and_wait();
            return cache.GetOrCreate("shared", [&] {
                ++builds;
                entered.set_value();
                gate.wait();
                return std::make_shared<int>(42);
            });
        }));
    }
    start.arrive_and_wait();
    entered.get_future().wait();
    // A different key can finish while the first driver compilation is blocked.
    auto independent = cache.GetOrCreate("independent", [] {return std::make_shared<int>(7);});
    assert(*independent == 7);
    release.set_value();
    std::shared_ptr<int> first;
    for (auto& call : calls) {
        auto value = call.get();
        if (!first) first = value;
        assert(value == first && *value == 42);
    }
    assert(builds == 1);
    // Refresh shared. The independent entry, not shared, must be evicted.
    cache.GetOrCreate("shared", [] {assert(false); return std::shared_ptr<int>{};});
    cache.GetOrCreate("third", [] {return std::make_shared<int>(3);});
    cache.GetOrCreate("shared", [] {assert(false); return std::shared_ptr<int>{};});
    bool rebuilt = false;
    cache.GetOrCreate("independent", [&] {rebuilt=true; return std::make_shared<int>(8);});
    assert(rebuilt && *independent == 7); // Eviction does not destroy an in-use value.
    bool failed = false;
    try {
        cache.GetOrCreate("failure", []() -> std::shared_ptr<int> {throw std::runtime_error("driver");});
    } catch (const std::runtime_error&) { failed=true; }
    assert(failed);
    assert(*cache.GetOrCreate("failure", [] {return std::make_shared<int>(9);}) == 9);
    Common::SingleFlightCache<int> tiny{4, 2};
    int oversized = 0;
    for (int i=0; i<2; ++i) tiny.GetOrCreate("large", [&] {++oversized; return std::make_shared<int>(1);});
    assert(oversized == 2);
    std::cout << "PASS: one producer, independent keys, LRU, retained values, retry, byte limit\n";
}
