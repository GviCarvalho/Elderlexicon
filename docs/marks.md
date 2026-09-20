## Marks persistentes

- Qualquer entidade, item ou bloco com bloco-entidade pode carregar uma Mark permanente (sem expirar em 20s).
- Chave NBT: `elderlexicon:mark` (ou fallback `Mark`). O valor e um texto; sera sanitizado para minusculo e sem espacos extras.

### Como aplicar
- Entidade: `/data merge entity @e[type=minecraft:pig,limit=1] {elderlexicon:mark:"granja_a"}`
- Item no inventario: `/data merge entity @p {SelectedItem:{tag:{elderlexicon:mark:"kit_medico"}}}`
- Item dropado: `/data merge entity @e[type=item,limit=1,nbt={Item:{id:"minecraft:diamond"}}] {elderlexicon:mark:"gema_rara"}`
- Bloco com bloco-entidade (ex. baus, fornalhas): `/data merge block 0 64 0 {elderlexicon:mark:"deposito_a"}`

Ou use o **Elder Brush**: segure o item, clique com botao direito em um bloco/entidade e digite a Mark na interface que aparece. Deixar o campo vazio remove a mark persistente.

### Uso com Ligabis
- Ao lancar `ligabis` sem especificar Mark apos a runa, o feitiço tentara usar a Mark persistente do alvo (entidade, item dropado ou bloco-entidade). Se nao encontrar, a criacao falha.
- Marcas definidas pelo comando nao expiram; use `/data remove ... elderlexicon:mark` para limpar.
