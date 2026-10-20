// SPDX-FileCopyrightText: Copyright 2026 Moonwitch contributors
// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <cstddef>
#include <exception>
#include <future>
#include <list>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

namespace Common {
// Exact string keys, shared results and one producer per key. Only completed
// entries enter the LRU: an in-flight entry must never be evicted and duplicated.
// No factory or wait runs under the cache mutex. Owners must join callers before destruction.
template <typename Value>
class SingleFlightCache {
public:
    SingleFlightCache(size_t max_entries_, size_t max_key_bytes_)
        : max_entries{max_entries_}, max_key_bytes{max_key_bytes_} {}

    template <typename Factory>
    std::shared_ptr<Value> GetOrCreate(std::string key, Factory&& factory) {
        std::shared_ptr<Entry> entry;
        bool producer{};
        {
            std::scoped_lock lock{mutex};
            const auto it = entries.find(key);
            if (it != entries.end()) {
                entry = it->second;
                if (entry->ready) {
                    lru.splice(lru.begin(), lru, entry->position);
                }
            } else {
                entry = std::make_shared<Entry>();
                entries.emplace(key, entry);
                producer = true;
            }
        }
        if (!producer) {
            return entry->future.get();
        }
        std::shared_ptr<Value> result;
        try {
            result = factory();
        } catch (...) {
            // Wake every waiter even on failure. A later request may retry.
            const auto error = std::current_exception();
            {
                std::scoped_lock lock{mutex};
                entries.erase(key);
            }
            entry->promise.set_exception(error);
            std::rethrow_exception(error);
        }
        entry->promise.set_value(result);
        {
            std::scoped_lock lock{mutex};
            if (max_entries == 0 || key.size() > max_key_bytes / 2) {
                entries.erase(key);
                return result;
            }
            // Keys are owned by both the lookup table and the LRU list.
            const size_t bytes = key.size() * 2;
            while (!lru.empty() &&
                   (lru.size() >= max_entries || key_bytes + bytes > max_key_bytes)) {
                key_bytes -= lru.back().size() * 2;
                entries.erase(lru.back());
                lru.pop_back();
            }
            lru.push_front(std::move(key));
            entry->position = lru.begin();
            entry->ready = true;
            key_bytes += bytes;
        }
        return result;
    }

private:
    struct Entry {
        std::promise<std::shared_ptr<Value>> promise;
        std::shared_future<std::shared_ptr<Value>> future{promise.get_future().share()};
        std::list<std::string>::iterator position;
        bool ready{};
    };
    const size_t max_entries;
    const size_t max_key_bytes;
    std::mutex mutex;
    std::unordered_map<std::string, std::shared_ptr<Entry>> entries;
    std::list<std::string> lru;
    size_t key_bytes{};
};
} // namespace Common
