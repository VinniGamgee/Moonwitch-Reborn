import hashlib,json,pathlib,shutil,struct,sys,zipfile
p=pathlib.Path(sys.argv[1]);w=p/'work';d=p/'dist'
image=w/'out/arch/arm64/boot/Image'
data=image.read_bytes()
assert len(data)>5_000_000 and data[56:60]==b'ARMd', 'Invalid ARM64 Image'
config=(w/'out/.config').read_text()
assert 'CONFIG_LTO_CLANG_FULL=y' in config
stage=w/'package'
stage.mkdir()
for name in ['META-INF','tools']:
 shutil.copytree(w/'ak3'/name,stage/name)
shutil.copy2(p/'anykernel.sh',stage/'anykernel.sh')
for folder in ['boot-files','vendor_boot-files']:(stage/folder).mkdir()
shutil.copy2(image,stage/'boot-files/Image')
shutil.copy2(w/'dtb',stage/'vendor_boot-files/dtb')
# Upstream licenses and all project source accompany the binaries.
for f in (w/'ak3').glob('*'):
 if f.is_file() and f.name.lower().startswith(('license','copying')):shutil.copy2(f,stage/f.name)
def archive(src,dest):
 with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for f in sorted(src.rglob('*')):
   if f.is_file():
    info=zipfile.ZipInfo.from_file(f,f.relative_to(src))
    if f.suffix=='.sh' or f.name in ('profilectl','update-binary') or 'tools' in f.relative_to(src).parts:
     info.external_attr=(0o100755<<16)
    info.compress_type=zipfile.ZIP_DEFLATED
    z.writestr(info,f.read_bytes())
archive(stage,d/'Marble-EvoX12.3-FullLTO-GPU591.5.zip')
archive(p/'profiles',d/'Marble-Profiles.zip')
with zipfile.ZipFile(d/'Marble-Kernel-Sources.zip','w',zipfile.ZIP_DEFLATED) as z:
 for f in sorted(p.rglob('*')):
  if f.is_file() and not any(x in f.relative_to(p).parts for x in ('work','dist','__pycache__')):z.write(f,f.relative_to(p))
 z.write(w/'out/.config','kernel.config')
shutil.copy2(p/'README.md',d/'LEIA-ME.md')
manifest={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in d.iterdir() if f.suffix=='.zip'}
(d/'SHA256SUMS').write_text(''.join(h+'  '+name+'\n' for name,h in manifest.items()))
report=json.loads((d/'build-info.json').read_text())
report.update(compilation_success=True,image_sha256=hashlib.sha256(data).hexdigest(),kernel_release=(w/'out/include/config/kernel.release').read_text().strip())
(d/'build-info.json').write_text(json.dumps(report,indent=2))
print('Packaged:',*manifest)
