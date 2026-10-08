"""One-time, allowlisted cleanup authorized by the repository owner.

All retired branch tips were preserved in a verified Git bundle before this job.
Never delete a branch that advanced since review, or touch Android/Turnip runs.
"""
import json
import os
from pathlib import Path
import time
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen


manifest = json.loads(Path('.github/cleanup-20261008.json').read_text())
repo = manifest['repository']
assert repo == os.environ['GITHUB_REPOSITORY'] == 'VinniGamgee/Moonwitch-Reborn'
assert os.environ['GITHUB_REF'] == 'refs/heads/experiment/ubo-gpu-coherency'
token = os.environ['GH_TOKEN']
protected = {'main', 'driver/moonwitch-turnip-a7xx', 'eden-update-2026-10-05',
             'experiment/ubo-gpu-coherency'}
assert not protected.intersection(manifest['branches'])
report = {'deleted_branches': [], 'preserved_changed_branches': [],
          'disabled_workflows': [], 'deleted_runs': [], 'errors': []}


def api(method, path):
    request = Request('https://api.github.com/repos/' + repo + '/' + path,
                      method=method, headers={
                          'Authorization': 'Bearer ' + token,
                          'Accept': 'application/vnd.github+json',
                          'X-GitHub-Api-Version': '2022-11-28',
                      })
    with urlopen(request, timeout=30) as response:
        body = response.read()
        return json.loads(body) if body else None


for branch, expected in manifest['branches'].items():
    path = 'git/refs/heads/' + quote(branch, safe='/')
    try:
        current = api('GET', 'git/ref/heads/' + quote(branch, safe='/'))
        if current['object']['sha'] != expected:
            report['preserved_changed_branches'].append(branch)
            continue
        api('DELETE', path)
        report['deleted_branches'].append(branch)
        print('Removed retired branch:', branch)
    except HTTPError as error:
        if error.code != 404:
            report['errors'].append(f'{branch}: HTTP {error.code}')

for workflow in manifest['workflows']:
    workflow_path = 'actions/workflows/' + str(workflow['id'])
    try:
        current = api('GET', workflow_path)
        assert current['name'] == workflow['name'] and current['path'] == workflow['path']
        api('PUT', workflow_path + '/disable')
        report['disabled_workflows'].append(current['name'])
        # Re-read page 1 after each deletion batch; pagination offsets would skip runs.
        for attempt in range(20):
            runs = api('GET', workflow_path + '/runs?per_page=100')['workflow_runs']
            if not runs:
                break
            for run in runs:
                run_path = 'actions/runs/' + str(run['id'])
                if run['status'] != 'completed':
                    api('POST', run_path + '/cancel')
                    time.sleep(2)
                    if api('GET', run_path)['status'] != 'completed':
                        continue
                api('DELETE', run_path)
                report['deleted_runs'].append(run['id'])
        else:
            report['errors'].append(current['name'] + ': run cleanup did not finish')
        print('Retired workflow:', current['name'])
    except HTTPError as error:
        if error.code != 404:
            report['errors'].append(f"{workflow['name']}: HTTP {error.code}")

print(json.dumps(report, indent=2))
with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
    summary.write('## Repository cleanup\n\n```json\n' + json.dumps(report, indent=2) + '\n```\n')
if report['errors']:
    raise SystemExit('Cleanup incomplete; see the job summary.')
