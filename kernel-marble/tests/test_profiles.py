import os,pathlib,shutil,subprocess,tempfile
project=pathlib.Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as tmp:
 root=pathlib.Path(tmp);module=root/'module';shutil.copytree(project/'profiles',module)
 sys=root/'sys';gpu=sys/'class/kgsl/kgsl-3d0/devfreq';gpu.mkdir(parents=True)
 def write(p,v):p.write_text(str(v)+'\n')
 write(gpu/'available_frequencies','591500000 515000000 439000000 364000000 324000000 285000000 220000000')
 write(gpu/'min_freq',220000000);write(gpu/'max_freq',591500000)
 for i,freqs in [(0,'300000 691200 1132800 1804800'),(4,'633600 1113600 1881600 2496000'),(7,'787200 1286400 2400000 2918400')]:
  p=sys/f'devices/system/cpu/cpufreq/policy{i}';p.mkdir(parents=True)
  write(p/'scaling_available_frequencies',freqs);write(p/'cpuinfo_max_freq',freqs.split()[-1])
  write(p/'scaling_min_freq',freqs.split()[0]);write(p/'scaling_max_freq',freqs.split()[-1])
 env=dict(os.environ,MARBLE_TEST_SYSROOT=str(sys))
 # CI runners are unprivileged, while the controller correctly requires root.
 # A local PATH shim supplies only id -u for this simulated sysfs test.
 shim=root/'bin';shim.mkdir();(shim/'id').write_text('#!/bin/sh\necho 0\n');(shim/'id').chmod(0o755)
 env['PATH']=str(shim)+':'+env['PATH']
 def run(mode,ok=True):
  p=subprocess.run(['sh',str(module/'profilectl'),'apply',mode],env=env,capture_output=True,text=True)
  assert (p.returncode==0)==ok,(p.stdout,p.stderr)
 for mode,low,high in [('performance',439000000,591500000),('powersave',220000000,364000000),('balanced',220000000,515000000)]:
  run(mode);assert int((gpu/'min_freq').read_text())==low;assert int((gpu/'max_freq').read_text())==high
  assert (module/'selected').read_text().strip()==mode
 original=(module/'profiles.conf').read_text()
 (module/'profiles.conf').write_text(original.replace('balanced.gpu_min=220000000','balanced.gpu_min=591500000'))
 run('balanced',False);assert int((gpu/'max_freq').read_text())==515000000
 (module/'profiles.conf').write_text(original.replace('balanced.gpu_max=515000000','balanced.gpu_max=439000000'))
 run('balanced');assert int((gpu/'max_freq').read_text())==439000000
 write(gpu/'available_frequencies','580000000 515000000 439000000 364000000 220000000')
 run('performance',False);assert int((gpu/'max_freq').read_text())==439000000
 (module/'.lock').mkdir();run('balanced',False)
print('PASS: profile transitions, custom limits, invalid ranges, missing OC and concurrent apply lock')
