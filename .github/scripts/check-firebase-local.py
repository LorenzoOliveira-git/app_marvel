import json
import subprocess
import time
from pathlib import Path

subprocess.run(['node', 'tools/check-firebase-local.cjs'], check=True)
package = 'com.example.app_marvel'
subprocess.run(['adb', 'shell', 'run-as', package, 'rm', '-f', 'files/diagnostics/firebase-local.json'], check=True)
subprocess.run(['adb', 'shell', 'am', 'start', '-n', package + '/.diagnostics.FirebaseLocalCheckActivity'], check=True)
for attempt in range(90):
    result = subprocess.run(['adb', 'shell', 'run-as', package, 'cat', 'files/diagnostics/firebase-local.json'], capture_output=True, text=True)
    if result.returncode == 0:
        report = json.loads(result.stdout)
        Path('firebase-local-check/android.json').write_text(json.dumps(report, indent=2))
        assert report['success'], report['message']
        assert report['local'] and not report['generatedImage']
        print(report['message'])
        break
    time.sleep(1)
else:
    subprocess.run(['adb', 'logcat', '-d', '-s', 'AndroidRuntime:E', 'FirebaseLocalCheck:E'])
    raise RuntimeError('SDK Android não concluiu o diagnóstico local em 90 segundos.')
