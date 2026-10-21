import gzip,hashlib,json,pathlib,struct,sys
work,dist=map(pathlib.Path,sys.argv[1:])
boot=(work/'boot.img').read_bytes()
assert boot[:8]==b'ANDROID!'
size=struct.unpack_from('<I',boot,8)[0]
kernel=boot[4096:4096+size]
assert b'Linux version 5.10.269-gki-gc3d1c3d93062 ' in kernel
start=kernel.index(b'IKCFG_ST')+8;end=kernel.index(b'IKCFG_ED',start)
config=gzip.decompress(kernel[start:end])
assert b'CONFIG_LTO_CLANG_FULL=y' in config
(work/'stock.config').write_bytes(config)
vendor=(work/'vendor_boot.img').read_bytes()
assert hashlib.sha256(vendor).hexdigest()=='ede997b39a6b009460af72a6eaad809b8a1ce9da4efb9dc261bd4b7816e530f4', 'Official vendor_boot changed; revalidate before building'
assert vendor[:8]==b'VNDRBOOT'
version,page,_,_,ramdisk_size=struct.unpack_from('<5I',vendor,8)
assert version==4 and page==4096
header_size,dtb_size=struct.unpack_from('<2I',vendor,2096)
align=lambda n:(n+page-1)//page*page
offset=align(header_size)+align(ramdisk_size)
original=vendor[offset:offset+dtb_size]
assert hashlib.sha256(original).hexdigest()=='5036448420d9ce349da846cef489a6a134fc83cf1678bc0be2507101b12355b8'
# Parsed and verified against the exact official image: tree 7, Adreno725v1,
# /soc/qcom,kgsl-3d0@3d00000/qcom,gpu-pwrlevels/qcom,gpu-pwrlevel@0,
# qcom,gpu-freq. The complete input hash guards this fixed-offset edit.
pos=3467795
assert original[pos:pos+4]==struct.pack('>I',580000000)
patched=original[:pos]+struct.pack('>I',591500000)+original[pos+4:]
assert hashlib.sha256(patched).hexdigest()=='c1f888aa9a5362b1f7937d2c01815c66d6d604d276626dc27940acccfe424abc'
(work/'dtb').write_bytes(patched)
report={'rom':'EvolutionX-17.0-20261010-marble-12.3-Official','kernel_commit':'c3d1c3d93062911060e8460fdb88cab235f6603e','gpu_top_hz':591500000,'original_gpu_top_hz':580000000,'full_lto':True,'device_boot_tested':False,'stock_boot_sha256':hashlib.sha256(boot).hexdigest(),'stock_vendor_boot_sha256':hashlib.sha256(vendor).hexdigest(),'patched_dtb_sha256':hashlib.sha256(patched).hexdigest()}
(dist/'build-info.json').write_text(json.dumps(report,indent=2))
print('Exact stock config extracted; Adreno 725 OPP patched.')
