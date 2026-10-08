"""Host regression harness for the actual experimental UBO helper bodies.

Mocks GPU storage separately from CPU RAM. This checks data flow and copy bounds,
not Vulkan driver behavior, asynchronous lifetime, or full Android compilation.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def function(path, signature):
    source = (ROOT / path).read_text()
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


runtime = function('src/video_core/renderer_vulkan/vk_buffer_cache.cpp',
                   'bool BufferCacheRuntime::TryBindAlignedUniformBuffer(')
cache = function('src/video_core/buffer_cache/buffer_cache.h',
                 'bool BufferCache<P>::TryBindGpuWrittenAlignedUniformBuffer(')

HARNESS = r'''
#include <algorithm>
#include <array>
#include <cassert>
#include <cstdint>
#include <deque>
#include <iostream>
#include <span>
#include <vector>
using u8 = uint8_t; using u32 = uint32_t; using u64 = uint64_t; using DAddr = u64;
#define LOG_INFO(...) ((void)0)
#define LOG_WARNING(...) ((void)0)
namespace Common { template<class T> T AlignUp(T n, T a) { return (n+a-1)/a*a; } }
namespace VideoCommon { struct BufferCopy { u64 src_offset, dst_offset, size; }; }
enum class MemoryUsage { DeviceLocal };
struct Buffer {
    std::vector<u8> gpu = std::vector<u8>(64, 0xA5);
    u64 SizeBytes() const { return gpu.size(); }
    u32 Offset(DAddr addr) const { return u32(addr); }
    auto Handle() { return &gpu; }
    void MarkUsage(u32 offset, u64 size) { assert(offset + size <= gpu.size()); }
};
struct StagingBufferRef { std::vector<u8>* buffer; u64 device_address; u64 offset; };
struct Pool {
    std::deque<std::vector<u8>> buffers;
    StagingBufferRef Request(u64 size, MemoryUsage usage) {
        assert(usage == MemoryUsage::DeviceLocal);
        buffers.emplace_back(size, 0);
        return {&buffers.back(), 0, 0};
    }
};
struct Descriptors {
    std::vector<u8>* buffer{}; u32 offset{}, size{};
    void AddBuffer(std::vector<u8>* b, u64, u32 o, u32 s) {
        buffer=b; offset=o; size=s;
    }
};
struct BufferCacheRuntime {
    Pool staging_pool;
    Descriptors guest_descriptor_queue;
    u32 copies{};
    u32 GetUniformBufferAlignment() const { return 256; }
    void CopyBuffer(std::vector<u8>* dst, std::vector<u8>* src,
                    std::span<const VideoCommon::BufferCopy> copies_, bool barrier) {
        assert(barrier);
        for (auto c : copies_) {
            assert(c.src_offset%4 == 0 && c.dst_offset%4 == 0 && c.size%4 == 0);
            assert(c.src_offset+c.size <= src->size());
            assert(c.dst_offset+c.size <= dst->size());
            std::copy_n(src->begin()+c.src_offset,c.size,dst->begin()+c.dst_offset);
        }
        ++copies;
    }
    bool TryBindAlignedUniformBuffer(Buffer&,u32,u32);
};
struct Tracker { bool dirty = true; bool IsRegionGpuModified(DAddr,u32) { return dirty; } };
template<class P> struct BufferCache {
    static constexpr bool IS_OPENGL = P::IS_OPENGL;
    struct Diag { u64 bindings{},unaligned{},gpu_modified{},gpu_copies{},readbacks{}; };
    std::array<Diag,2> uniform_alignment_diagnostics{};
    Tracker memory_tracker;
    BufferCacheRuntime runtime;
    std::vector<u8> cpu = std::vector<u8>(64, 0);
    bool pending_cpu_byte = false;
    u32 syncs{}, downloads{};
    void SynchronizeBuffer(Buffer& b,DAddr addr,u32) {
        ++syncs;
        if (pending_cpu_byte) b.gpu[addr] = cpu[addr];
    }
    void DownloadBufferMemory(Buffer& b,DAddr addr,u32 size) {
        ++downloads;
        std::copy_n(b.gpu.begin()+addr,size,cpu.begin()+addr);
        memory_tracker.dirty=false;
    }
    bool TryBindGpuWrittenAlignedUniformBuffer(Buffer&,DAddr,u32,bool);
};
struct VulkanPolicy { static constexpr bool IS_OPENGL = false; };
struct OpenGLPolicy { static constexpr bool IS_OPENGL = true; };
'''

TESTS = r'''
int main() {
    // The GPU has new bytes while CPU RAM is stale. Both stage variants must bind GPU bytes.
    for (bool compute : {false,true}) {
        BufferCache<VulkanPolicy> cache; Buffer source;
        cache.pending_cpu_byte=true; cache.cpu[16]=0xCC;
        assert(cache.TryBindGpuWrittenAlignedUniformBuffer(source,16,7,compute));
        auto& d=cache.runtime.guest_descriptor_queue;
        assert(d.offset==0 && d.size==7 && d.buffer->size()==8);
        assert((*d.buffer)[0]==0xCC && (*d.buffer)[1]==0xA5);
        assert(cache.cpu[17]==0 && cache.downloads==0 && cache.syncs==1);
        assert(cache.uniform_alignment_diagnostics[compute].gpu_copies==1);
        // A later bind gets a separate snapshot; changing the source must not alter the first.
        auto* first=d.buffer; source.gpu[17]=0xBB;
        assert(cache.TryBindGpuWrittenAlignedUniformBuffer(source,16,7,compute));
        assert((*first)[1]==0xA5 && (*d.buffer)[1]==0xBB);
    }
    // CPU-clean ownership stays with the existing CPU streaming path.
    { BufferCache<VulkanPolicy> c; Buffer b; c.memory_tracker.dirty=false;
      assert(!c.TryBindGpuWrittenAlignedUniformBuffer(b,16,8,false));
      assert(c.syncs==0 && c.runtime.copies==0 && c.downloads==0); }
    // A byte offset invalid for vkCmdCopyBuffer refreshes CPU RAM before falling back.
    { BufferCache<VulkanPolicy> c; Buffer b;
      assert(!c.TryBindGpuWrittenAlignedUniformBuffer(b,17,7,true));
      assert(c.cpu[17]==0xA5 && c.downloads==1 && c.runtime.copies==0); }
    // Rounded transfers must not read past allocation tails; descriptor sizes remain exact.
    { BufferCacheRuntime r; Buffer b; b.gpu.resize(19);
      assert(!r.TryBindAlignedUniformBuffer(b,16,3));
      assert(!r.TryBindAlignedUniformBuffer(b,0,0));
      assert(r.copies==0 && r.staging_pool.buffers.empty()); }
    // The generic cache must not change OpenGL behavior.
    { BufferCache<OpenGLPolicy> c; Buffer b;
      assert(!c.TryBindGpuWrittenAlignedUniformBuffer(b,16,8,false));
      assert(c.syncs==0 && c.downloads==0 && c.runtime.copies==0); }
    std::cout << "UBO coherency host cases passed (driver integration requires Android build/test)\n";
}
'''

with tempfile.TemporaryDirectory(prefix='moonwitch-ubo-') as directory:
    source = Path(directory) / 'ubo.cpp'
    binary = Path(directory) / 'ubo-test'
    source.write_text(HARNESS + runtime + '\ntemplate<class P>\n' + cache + TESTS)
    subprocess.run(['c++', '-std=c++20', '-Wall', '-Wextra', '-Werror',
                    str(source), '-o', str(binary)], check=True)
    subprocess.run([str(binary)], check=True)
