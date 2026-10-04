# Varinhas: o primeiro foco

> "Foci (staves, wands, etched conduits) channel magical flow outside the nerves. [...] Personalize the focus. The body
> itself is the final living focus." (Grimório de EVL, 9.3)

**Status:** etapas 1, 2 e 3 implementadas (04/10/2026): as partes, a reserva, a bigorna, a recarga por marca e o eco.
A galdraria é a próxima. A seção 8 diz como ficou.

## Termos

- **Foco** é a categoria: tudo o que conduz a UMU, carregando a carga energética no lugar do conjurador. Varinhas,
  cajados e cetros são focos. Cajados e cetros vêm depois das varinhas.
- **Galdraria** é a arte de gravar runas em objetos para fazê-los funcionar como aparelhos tecnomágicos. A varinha é o
  primeiro objeto gravado.

## 1. As três partes

| Parte | Material | Papel |
|---|---|---|
| **Haste** (obrigatória) | graveto, osso, bambu, vara de blaze | O corpo da varinha. Dá a afinidade com uma fonte (como hoje). |
| **Empunhadura** | toras e metais | Onde o conjurador segura. Dá a **capacidade de condução** e a afinidade com um verbo (madeira) ou fonte (metal). |
| **Engaste** | gemas | Guarda uma **reserva extra de UMU**, gasta no lugar da reserva do conjurador. |

**Só a haste é obrigatória.** A empunhadura e o engaste são opcionais: a varinha improvisada de hoje é só uma haste.

**A varinha é definitiva:** a haste e a empunhadura escolhidas ficam até ela se destruir. O engaste é a exceção: ele
pode ser trocado na bigorna (seção 3), mas a gema antiga quebra no processo.

### Condução e reserva são coisas diferentes

A varinha tem duas medidas, e cada uma tem sua barra:

- **Condução** (a empunhadura + a haste): quanto a varinha aguenta carregar no lugar do corpo antes de quebrar. É o
  que existe hoje (`SpellConduitItem`). Ela se gasta a cada feitiço e pode ser consertada.
- **Reserva** (o engaste): de onde a energia sai. Sem engaste, ou com ele vazio, a energia sai do conjurador, como hoje.

## 2. Materiais

Números e afinidades são **propostas**, para serem ajustados no `foci.json`.

### Hastes (como hoje)

| Haste | Afinidade |
|---|---|
| Graveto | nenhuma |
| Osso | firmo 20% |
| Bambu | aura 10%, aqua 10% |
| Vara de blaze | igni 20% |

### Empunhaduras

As madeiras ficam como estão no `foci.json` (capacidade 22 a 40, desconto num verbo; crimson e warped carregam as
conversões). Os metais entram ao lado delas:

| Empunhadura | Capacidade | Afinidade | Por quê |
|---|---|---|---|
| Cobre | 30 | igni 20% | O livro: "Copper wands favor `igni`". |
| Ferro | 35 | firmo 20% | |
| Ouro | 25 | aura 20% | Conduz bem, mas é mole. |
| Netherita | 60 | igni 20%, firmo 20% | O topo: tem a energia das duas. |

### Engastes

Um engaste guarda **vis** (que substitui a mana do conjurador, a experiência) ou **uma fonte** (que substitui aquele
elemento do Vita).

| Engaste | Guarda | Reserva |
|---|---|---|
| Quartzo | vis | 10 |
| Lápis-lazúli | vis | 20 |
| Ametista | vis | 30 |
| Esmeralda | vis | 40 |
| Diamante | vis | 60 |
| Estrela do Nether | vis | 200 |
| Pérola do Ender | vis | 100 |
| Coração do mar | aqua | 80 |
| Pederneira | firmo | 40 |
| Carga de fogo | igni | 40 |
| Fragmento de eco | (especial) | — |

**O fragmento de eco** não guarda reserva: ele **ecoa o feitiço que o seu portador diz no modo spelling**, palavra por
palavra, seja ele qual for. **O próprio spell é o gatilho:** sempre que o portador spella, o eco repete. Se ele disse
`igni vocant iactare`, o eco repete `igni vocant iactare`; se disse só `surgit`, repete só `surgit`.

## 3. Ciclo de vida

1. **Montagem**, na bancada comum: a haste, com ou sem empunhadura e engaste. A peça que falta pode ser posta depois,
   também na bancada (uma varinha com empunhadura recebe a gema; uma haste com gema recebe a empunhadura), mas uma peça
   já posta não é trocada ali.
2. **Uso:** cada feitiço passa pela condução, e a energia sai do engaste se ele tiver a fonte certa e reserva; senão,
   sai do conjurador.
3. **Recarga** do engaste: por feitiços, mirando a varinha por uma **marca** (seção 3.1).
4. **Conserto**, na bigorna:
   - **Varinha + material da empunhadura** (a mesma tora ou o mesmo metal): devolve a condução, sem mudar as partes nem a
     gravação.
   - **Varinha + gema**: põe essa gema como o novo engaste. A gema antiga **quebra** para dar lugar à nova, com a reserva
     que tinha. Numa varinha sem engaste, a gema só entra.
5. **Destruição:** quando a condução chega a zero, a varinha quebra, com tudo o que estava nela.

### 3.1 Recarga por marca

A varinha é um item, e itens já carregam marcas (`docs/marks.md`: o Pincel Elder ou a tag `elderlexicon:mark`). A
recarga usa a gramática que já existe: invocar a fonte **no lugar da marca**, e o engaste que está lá a guarda.

- `vis quantum 20 v1 ubis vocant`: 20 de vis do conjurador vão para a varinha marcada `v1`.
- `aqua tenet quantum 20 v1 ubis vocant`: 20 de aqua **tirados do mundo** vão para um engaste de aqua. Assim um engaste
  de fonte pode ser enchido sem gastar o Vita do conjurador.
- O engaste só aceita o que guarda: vis num coração do mar não entra, e a energia segue o caminho normal do feitiço
  (é invocada ali, como se não houvesse engaste).
- O que passar da reserva cheia também é invocado ali, como sobra.

É o mesmo que invocar uma fonte dentro de qualquer objeto marcado: a varinha é só mais um objeto.

## 4. Galdraria na varinha

- A runa é gravada **na varinha inteira**, não numa parte. A gravação é feita na **mesa de galdraria**.
- A varinha gravada guarda um **feitiço pronto**. Ele é ativado com **`surgit`**: como no livro (5.1.1), o Surgit faz o
  espírito ler o que está escrito, e a varinha é a escrita.
- O feitiço gravado passa pela condução da varinha como qualquer outro, e pode sair da reserva do engaste.
- **Dependendo do feitiço gravado, a varinha muda de comportamento.** (A definir: quais feitiços mudam o quê.)

## 5. O que já existe

- `SpellConduitItem`: a condução, que se gasta e quebra a varinha.
- `ImprovisedWandItem`: a haste sozinha, convertida da mão por uma tecla (`RequestImprovisedWandPacket`).
- `ModularWandItem` + `WandUpgradeRecipe`: haste improvisada + tora = varinha, com a madeira gravada no NBT.
- `Foci` + `foci.json`: os descontos por item e por madeira.

## 6. Perguntas abertas

2. Quais feitiços gravados mudam o comportamento da varinha, e como.
3. Alguma gema que faça sentido guardar **aura**? Quando houver, entra no `foci.json`.

## 7. Decisões tomadas

1. A varinha tem haste, empunhadura e engaste; só a haste é obrigatória. (04/10/2026)
2. Haste: graveto, osso, bambu, blaze. Empunhadura: toras e metais. Engaste: gemas. (04/10/2026)
3. Os engastes diferem na quantidade e no tipo do que guardam. (04/10/2026)
4. A recarga do engaste é por feitiço, mirando a varinha por marca. (04/10/2026)
5. Conserto na bigorna com o material da empunhadura; gema na bigorna troca o engaste e quebra a antiga. (04/10/2026)
6. A runa é gravada na varinha inteira, na mesa de galdraria, e ativada com `surgit`. (04/10/2026)
7. Qualquer varinha pode ser gravada, até só a haste. Qualquer coisa pode ser gravada. (04/10/2026)
8. Pérola do Ender: mais vis. Pederneira: firmo. Carga de fogo: igni. Fragmento de eco: ecoa o
   último feitiço dito pelo portador no modo spelling. (04/10/2026)
9. A gema entra **cheia** na varinha, na bancada e na bigorna. (04/10/2026)
10. Engaste de fonte paga só feitiços daquela fonte; varinha só de haste se conserta com o material da haste; a recarga
    só vale para o `vocant` de um instante. (04/10/2026)
11. O eco repete o feitiço dito no modo spelling pelo portador, qualquer que seja, até um `surgit` sozinho. O gatilho é
    o próprio spell, sempre; não há botão. (04/10/2026)
12. A netherita é **empunhadura** (igni e firmo), não engaste. (04/10/2026)
13. Na bancada, a varinha recebe a peça que ainda não tem, em qualquer ordem; a que já tem não muda. (04/10/2026)

## 8. Como as etapas 1, 2 e 3 ficaram

- **Dados (`foci.json`):** `woods` virou `grips`, com madeiras e metais; cada empunhadura diz o `tag` ou o `item` que a
  faz, e a receita não tem mais a lista de madeiras no código. `settings` são os engastes: `reserves` (quanto de cada
  tipo) e `echo`. Lido por `Foci.grips()` e `Foci.settings()`.
- **Itens:** `WandItem` é a base das varinhas e guarda o engaste e a reserva no NBT (`WandSetting`, `SettingReserve`).
  `ImprovisedWandItem` (só a haste) e `ModularWandItem` (com empunhadura) herdam dela. A empunhadura continua na chave
  `WandMaterial`, para as varinhas já feitas não mudarem.
- **Montagem (`WandUpgradeRecipe`):** qualquer varinha + as peças que lhe faltam. Só uma haste sem empunhadura recebe
  empunhadura, e só uma varinha sem gema recebe gema. A haste que ganha empunhadura leva junto a gema, o que ela guarda e
  o nome dado na bigorna.
- **Reserva (`SettingReserveModule`):** roda depois da condução e antes do pagamento do mago. O engaste da fonte do
  feitiço paga primeiro; o de vis paga qualquer feitiço. A mensagem de custo mostra "Engaste: N UMU".
- **Bigorna (`WandAnvil`):** 1 nível e 1 item. Material da empunhadura conserta; gema troca o engaste.
- **Recarga (`SettingCharge`):** no `vocant` e no `vocant` com `tenet`. A varinha marcada é procurada nos inventários dos
  jogadores e no chão. O mago paga tudo o que invocou, inclusive o que foi para a gema.

- **Eco (`ServerSpellingController.echo`):** quando o mago spella segurando (em qualquer mão) uma varinha com fragmento de
  eco, a mesma frase é conjurada de novo **meio segundo depois** (10 ticks), como um feitiço dele, pela mesma varinha
  (custo, condução e engaste valem de novo). O eco não espera a recarga do spelling e não é ecoado outra vez. Um spell
  recusado porque o spelling ainda recarregava não foi ouvido, e não ecoa.

- **Visual (`WandSprites`):** o sprite é feito de camadas: a haste como é (graveto, osso, bambu, blaze do Minecraft),
  a empunhadura enrolada na base (`wand_grip.png`) e a gema na ponta (`wand_setting.png`, um cristal facetado), presa por um aro (`wand_setting_mount.png`) na cor da empunhadura, ou cinza-ferro numa varinha só de haste. As duas texturas são em tons
  de cinza e tingidas pela `color` de cada parte no `foci.json`, então uma empunhadura ou gema nova ganha visual só com
  a cor. Cada item tem um modelo sem gema e um com (`*_set.json`), escolhido pela propriedade `elderlexicon:setting`.

**Ainda a revisar:**

1. O atraso do eco (meio segundo) e "portador" como quem segura a varinha na mão.


