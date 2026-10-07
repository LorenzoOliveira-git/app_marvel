"""Contraste calculado a partir dos tokens Android usados na interface."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET
RES=Path('app/src/main/res')
colors={n.get('name'):n.text for n in ET.parse(RES/'values/colors.xml').getroot()}
def color(name):
    value=colors[name]
    if value.startswith('@color/'):return color(value.split('/')[1])
    value=value.removeprefix('#')
    alpha=int(value[:2],16)/255 if len(value)==8 else 1
    value=value[-6:]
    return alpha,[int(value[i:i+2],16)/255 for i in (0,2,4)]
def luminance(rgb):
    return sum(w*(v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4)
               for w,v in zip((.2126,.7152,.0722),rgb))
def contrast(fg,bg):
    alpha,front=color(fg);back_alpha,back=color(bg)
    if back_alpha<1:
        _,base=color("arquivo_papel")
        back=[back_alpha*a+(1-back_alpha)*b for a,b in zip(back,base)]
    front=[alpha*a+(1-alpha)*b for a,b in zip(front,back)]
    lo,hi=sorted((luminance(front),luminance(back)))
    return (hi+.05)/(lo+.05)
rows=[]
for fg,backgrounds in [('arquivo_tinta',['arquivo_papel','arquivo_folha','arquivo_pergaminho','arquivo_desabilitado']),
                       ('arquivo_texto_corpo',['arquivo_papel','arquivo_folha','arquivo_pergaminho']),
                       ('arquivo_vermelho_texto',['arquivo_papel','arquivo_folha','arquivo_pergaminho']),
                       ('arquivo_sucesso',['arquivo_papel','arquivo_folha']),
                       ('arquivo_erro',['arquivo_papel','arquivo_folha']),
                       ('arquivo_sobre_vermelho',['arquivo_vermelho','arquivo_tinta'])]:
    for bg in backgrounds:
        ratio=contrast(fg,bg);assert ratio>=4.5,(fg,bg,ratio)
        rows.append({'foreground':fg,'background':bg,'ratio':round(ratio,2),'minimum':4.5})
for name,value in {'arquivo_papel':'#F5F3EE','arquivo_folha':'#FDFCFA','arquivo_pergaminho':'#E8E4DD',
                   'arquivo_tinta':'#2D2D2D','arquivo_vermelho':'#D92D20'}.items():assert colors[name]==value
out=Path('app/build/design-preview');out.mkdir(parents=True,exist_ok=True)
(out/'contrast.json').write_text(json.dumps({'success':True,'pairs':rows},indent=2))
print('Tokens principais preservados; contraste de texto aprovado em todas as superfícies usadas.')
