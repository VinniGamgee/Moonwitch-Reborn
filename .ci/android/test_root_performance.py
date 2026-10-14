#!/usr/bin/env python3
"""Exercise the real controller with temporary sysfs fixtures, never host sysfs."""
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / 'src/android/app/src/main/assets/performance/root-session.sh'


class ControllerTests(unittest.TestCase):
    def run_session(self, stop_mode, gpu=True):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cpu = root / 'cpu/policy0'
            cpu.mkdir(parents=True)
            initial = {
                cpu / 'cpuinfo_max_freq': '2918400',
                cpu / 'scaling_max_freq': '2200000',
                cpu / 'scaling_min_freq': '300000',
                cpu / 'scaling_governor': 'schedutil',
                cpu / 'scaling_available_governors': 'schedutil performance',
            }
            if gpu:
                graphics = root / 'gpu'
                graphics.mkdir()
                initial.update({
                    graphics / 'available_frequencies': '200000000 580000000 400000000',
                    graphics / 'max_freq': '400000000',
                    graphics / 'min_freq': '200000000',
                })
            for path, value in initial.items():
                path.write_text(value + '\n')
            script = SCRIPT.read_text().replace('/sys/devices/system/cpu/cpufreq', str(root / 'cpu'))
            script = script.replace('/sys/class/kgsl/kgsl-3d0/devfreq', str(root / 'gpu'))
            script = script.replace('/proc/', str(root / 'proc') + '/')
            script = script.replace('/data/adb/moonwitch-performance.lock', str(root / 'lock'))
            script = script.replace('[ "$(id -u)" = 0 ]', 'true')
            self.assertNotIn('/sys/', script)
            target = root / 'controller.sh'
            target.write_text(script)
            lease = root / 'lease'
            lease.touch()
            proc = root / 'proc/1234'
            proc.mkdir(parents=True)
            stat = proc / 'stat'
            stat.write_text('1234 (Moonwitch app) S ' + '0 ' * 18 + '98765 0\n')
            process = subprocess.Popen(['sh', str(target), '1234', str(lease)],
                                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            try:
                startup = []
                while True:
                    line = process.stdout.readline()
                    self.assertTrue(line, 'Controller exited before READY: ' + ''.join(startup))
                    startup.append(line)
                    if line.startswith('READY'):
                        break
                self.assertEqual((cpu / 'scaling_min_freq').read_text().strip(), '2918400')
                if gpu:
                    self.assertEqual((root / 'gpu/min_freq').read_text().strip(), '580000000')
                if stop_mode == 'lease':
                    lease.unlink()
                elif stop_mode == 'death':
                    stat.unlink()
                else:
                    process.terminate()
                output, _ = process.communicate(timeout=5)
                self.assertEqual(process.returncode, 0, output)
                for path, value in initial.items():
                    self.assertEqual(path.read_text().strip(), value, str(path))
                self.assertFalse((root / 'lock').exists())
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()

    def test_normal_stop(self):
        self.run_session('lease')

    def test_app_process_death(self):
        self.run_session('death')

    def test_termination_signal(self):
        self.run_session('signal')

    def test_missing_gpu_interface(self):
        self.run_session('lease', gpu=False)


if __name__ == '__main__':
    unittest.main()
