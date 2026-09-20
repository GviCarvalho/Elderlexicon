📜 UMU — Universal Magical Unit
Sistema de Consumo Energético do Elder Lexicon

UMU (Universal Magical Unit) é a unidade padrão usada para medir, calcular e executar o custo de qualquer feitiço dentro do sistema Elder Lexicon.

Cada runa, ação, transformação ou efeito utiliza UMU como energia fundamental.

🔄 Atualização Sprint 4 – Pipeline de Ações
-------------------------------------------
- O cálculo e o consumo de UMU agora são totalmente derivados do pipeline de ações (`SpellActionEngine` → `SpellActionExecutor` → `SpellCostProcessor`).
- Extensões/mods devem registrar novos comportamentos via `SpellFunctionHandlerRegistry` e `SpellModuleRegistry` em vez de editar comandos diretamente.
- Consulte `docs/action-pipeline-migration-guide.md` para ver o passo a passo de migração e exemplos de como plugar novos handlers ou módulos de custo.

⚡ 1. Ordem de Consumo

Sempre que um feitiço exige UMU, o jogador paga nesta ordem fixa:

XP (Experiência)

Saturação

HP (Vida)

Se todos acabarem, o feitiço falha.

🧪 2. Conversões de Recursos → UMU

Essas conversões seguem tua lógica oficial:

🎓 XP → UMU

XP é o recurso primário de conjuração.

10 pontos de XP = 1 UMU

Isso mantém coerência com o fluxo do jogo vanilla e facilita balanceamento.

🍗 Saturação → UMU

Saturação é energia corporal convertida em magia.

1 ponto de saturação = 1 UMU

Saturação é menos valiosa que XP, mas funciona como reserva secundária.
Permite usar magias simples mesmo sem XP.

❤️ HP → UMU

Vida é energia bruta.
É o último recurso e o mais caro.

Tu pediu explicitamente:

Vita vale 4 UMU por ponto de HP.

Então:

1 ponto de HP = 4 UMU

1 coração = 2 HP = 8 UMU

Jogador padrão: 20 HP = 80 UMU totais (se quiser se matar conjurando)

HP só é usado quando XP e Saturação zeram.

🔥 3. Custo das Runas Base (não-fundidas)

Agora puxando direto da tua documentação técnica, sem inventar nada.

🜄 Fontes (Sources)

Fontes não têm custo fixo próprio — elas custam a quantidade de UMU equivalente ao que estão movendo/convertendo.

Runa	Significado	Custo	Observação
aqua	água	varia	de acordo com a quantidade de água em Litros, sendo 0,5L = 1 UMU. 1 garrafa d'agua contem 0,5L, um caldeirao contem 1,5L (3 UMU), assim como o balde e o bloco fonte de água.
aura	ar	varia	idem, mas o padrao é 1/psi
igni	fogo	varia	de acordo com o nível de luz de blocos/entidades/partículas/itens que emitem luz atravez do fogo, sendo 1 UMU = 12 níveis de emissao de luz
firmo	terra	varia de acordo com a dureza do bloco, ou seja, resistencia a explosoes 10 = 1 UMU. Para ter nocao, um bloco de terra tem 0,5 BR (Blast resistance), já um bloco de pedra tem 30 BR
vis	mana é o próprio ponto de partida, sendo 1 mana = 1 UMU. Importante lembrar que a mana é constituída da energia de todos os 4 elementos primordiais em perfeito equilibrio(25% de igni, 25% de aura, 25% de aqua, 25% de firmo).
vita	vida	5 UMU por ponto de HP. Importante lembrar que Vita é também a energia proviniente da juncao dos 4 elementos primordiais, mas em quantidades específicas (56% aqua, 38% aura, 2% igni e 4% firmo).

Fontes são “matéria-prima”. O custo real vem do quanto a função exige manipular.

🜂 Funções (Actions)

Essas sim têm custo definido pelo documento, e agora estão aqui organizadas como UMU base:

Função	Significado	Custo Base	Como interpretar
exsugat	absorver	0 UMU	custo ocorre na transferência, não na ativação
ligabis	conectar	0.1 UMU × tempo	manutenção contínua
vertere	converter	UMU convertido	transfere 1:1
iactare	evocar/projetar	1 UMU/s	custo flui ao longo do cast
vocant	invocar	1 UMU (lote)	custo instantâneo
reframe	ressignificar	5 UMU	custo fixo
surgit	conjurar	1 UMU	custo fixo
impediunt	repelir/conter	UMU do impulso	o valor do empurrão define custo

🌀 Feedback visual do Impediunt
- Quando o impulso atinge pelo menos um alvo, o caster dispara um anel de partículas centrado nele. A cor do anel usa o elemento ativo (`igni impediunt` → partículas flamejantes, `aqua impediunt` → bolhas, etc.).
- O som padrão agora é um golpe corporal (“knockback”) para comunicar rapidamente que a onda de choque saiu, mesmo sem ver os alvos.
- Esses efeitos não alteram custo; servem apenas para o jogador perceber se o filtro elemental escolhido realmente encontrou inimigos alinhados.
- Se o feitiço tiver uma fonte anterior (ex.: `igni impediunt`), blocos e drops marcados como "fontes primárias" daquele elemento também são afetados: lava vira uma entidade temporária tipo falling-block, água/areia solta partículas coloridas e itens/tagged drops são arremessados para longe.
- As listas de fontes vivem em `data/elderlexicon/tags/blocks/*_source_blocks.json` e `.../items/*_source_items.json`, então designer/balance pode expandir sem recompilar.

Fontes padrão por elemento (já configuradas nas tags):
- **Aqua**: água/bubble column, gelo (todas as variantes), kelp/seagrass/sea pickle, conduit; itens como water/axolotl bucket, peixes, prismarine shard, heart of the sea, kelp/seagrass drops.
- **Igni**: lava, fogo comum/soul, magma block, tochas e campfires; itens como tochas (todas), blaze powder, magma cream, fire charge, carvão.
- **Aura**: beacon, chorus plant/flower/fruit block, estruturas de amethyst, scaffolding; itens como feather, phantom membrane, elytra, ghast tear, chorus fruit, shulker shell, foguetes.
- **Firmo**: pedra + variações (granite/andesite/diorite, deepslate), clay/mud/brick blocs, blocos de metal precioso; itens como emerald, iron/copper ingot, clay ball, brick, stone/cobble/deepslate.
🜁 Filtros (Modifiers)

Filtros não têm custo próprio — eles custam exatamente o valor que modificam.

Filtro	Significado	Custo
quantum	quantidade	valor modificado (em UMU)
chronos	tempo	valor modificado (em segundos = UMU)
ubis	alcance	valor modificado (em metros = UMU)
📘 4. Exemplo de Cálculo de Custo

Feitiço linear:

igni vertere aura iactare


Cálculo:

igni → custo depende do fluxo (não é fixo)

vertere → 1:1 (converte o fluxo da igni)

aura → idem

iactare → 1 UMU por segundo da projeção

Supondo fluxo de 3 UMU e duração do iactare de 2s:

igni: 3 UMU

vertere: 3 UMU

aura: 0 (fonte não tem custo fixo)

iactare: 2 UMU

Total: 8 UMU

Se o jogador tem:

2 XP → gasta 2 UMU

4 saturação → gasta 4 UMU

faltam 2 UMU → paga com HP → perde 0.5 coração

📌 Resumo Final
Conversão

1 XP = 1 UMU

1 saturação = 1 UMU

1 HP = 4 UMU

Ordem

XP → Saturação → HP

Fontes

Custam a quantidade que estão manipulando.

Funções

Custo definido pela documentação.

Filtros

Custo = o valor modificado.