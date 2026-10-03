# Assets do Marv — preparação

Ferramenta: ImageGen integrada, em modo de edição com guia anexado como referência. Não é a integração de geração paga de heróis.

## Recepção
Destino: app/src/main/res/drawable-nodpi/marv_welcome.png
Prompt:
```text
Use case: background-extraction. Asset type: transparent Android app mascot asset. Input image is the edit target: the MARV identity guide. Extract ONLY the exact full-body FRONT view of Marv in the lower-left VISTAS section (small robot front view smiling, hands down, red cape), and remove everything else. Preserve that existing robot's identity, pose, proportions, expressive white smiling eyes on dark visor, silver-white body, red ear pieces, red crest, red cape, black gloves and boots, red chest medallion with white M. Produce a single isolated full-body mascot, centered with comfortable transparent padding, high resolution crisp edges. NO guide, labels, text, borders, extra poses, background, ground or drop shadow. Do not redesign or add accessories. Actual transparent alpha background.
```

## Pensativo
Destino: app/src/main/res/drawable-nodpi/marv_thinking.png
Prompt:
```text
Use case: background-extraction. Asset type: transparent Android UI state mascot. Edit target: attached MARV identity guide. Isolate only the exact PENSATIVO robot pose, second pose in the EXPRESSÕES top row: Marv chest-up, one black gloved finger on chin, one luminous white oval eye and one smiling/winking eye on glossy dark face, red ear pieces and crest, silver-white helmet and body, red cape and white M in red chest badge. Keep its original identity, pose, proportions and palette from the guide, do not redesign it. Remove the guide, label, surrounding artwork and background completely. Center the single thinking Marv bust with transparent padding; bottom cropped naturally at torso exactly as the source. No text, no labels, no box, no border, no other characters, no shadow. Genuine transparent alpha.
```

Os resultados são imagens derivadas preservando a identidade, não recortes pixel a pixel da prancha original. A imagem sorrindo é usada na home; a pensativa no componente MarvStateView. Sem porcentagens de progresso fictícias.
