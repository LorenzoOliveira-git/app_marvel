"""Recuperação restrita a falhas do launcher das imagens Android usadas no CI."""
import re

def dismiss_launcher_anr(snapshot, adb):
    launcher_failure = any(n.get('text', '').startswith("Pixel Launcher isn't responding")
                           for n in snapshot)
    if not launcher_failure:
        return False
    close = next((n for n in snapshot if n.get('text') == 'Close app'), None)
    if close is None:
        return False
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', close.get('bounds', '')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    print('Falha do launcher do emulador fechada; falhas do app continuam visíveis.', flush=True)
    return True
