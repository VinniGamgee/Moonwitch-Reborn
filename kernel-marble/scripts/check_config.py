import pathlib,sys
def cfg(f): return dict(x.split('=',1) for x in pathlib.Path(f).read_text().splitlines() if x.startswith('CONFIG_'))
a,b=map(cfg,sys.argv[1:])
delta=[(k,a.get(k),b.get(k)) for k in a.keys()|b.keys() if a.get(k)!=b.get(k)]
assert not delta, f'Config differs from official ROM: {delta}'
assert b['CONFIG_LTO_CLANG_FULL']=='y' and b['CONFIG_CFI_CLANG']=='y' and b['CONFIG_MODVERSIONS']=='y'
print('Exact stock configuration retained, Full LTO + CFI + MODVERSIONS enabled.')
