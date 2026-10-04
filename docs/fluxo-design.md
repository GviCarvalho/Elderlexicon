# Estado de fluxo: o transe que o mago sustenta

> "No transe, o tempo é teu inimigo. Aprende a domá-lo antes que ele te domine." (Grimório de EVL, cap. 5)

**Status:** primeira versão implementada (04/10/2026). Não testada dentro do jogo.

O livro só conhece o Transe do Despertar Invertido de dois segundos (cap. 5): o canal abre, o mago diz `surgit`, o
espírito lê e o canal fecha. O estado de fluxo é o mesmo transe **sustentado**: o canal fica aberto até o mago soltá-lo,
e o espírito, que continua escutando, desperta sozinho os feitiços que esperam por algo que acontece ao corpo do mago.
O livro não fala disso; é mecânica nova do mod.

## 1. Entrar e sair

- **Tecla `G`** (configurável em Controles, "Estado de fluxo"): entra no fluxo; apertar de novo sai.
- No HUD, no alto da tela: `Fluxo · 12 s · 0.74 UMU/s` (tempo sustentado e o custo do segundo atual).
- Enquanto dura, faíscas de encantamento rodeiam o mago, visíveis a quem está perto.
- O fluxo se desfaz quando o mago sai, quando a mana acaba, quando morre ou sai do mundo.

## 2. O custo: uma torneira que vai abrindo

- O primeiro segundo custa **0,5 UMU** de Vis (5 de experiência). Cada segundo sustentado custa **0,02 UMU a mais**
  que o anterior: um UMU a mais a cada cinquenta segundos.
- Em um minuto, o fluxo gasta cerca de 66 UMU; em cinco minutos, cerca de 1050.
- Quando a Vis não basta para o próximo pedaço, **o fluxo se desfaz**. O espírito não cobra da carne por isso: manter
  o canal aberto não é uma ordem cortada pela metade.
- Cada feitiço que desperta paga o **próprio custo à parte**, como qualquer feitiço.
- No modo criativo, o fluxo não custa nada.

## 3. As condições

Uma **condição** é uma runa como qualquer outra: é lida onde quer que se leiam runas (transe, `surgit`, marca, ritual,
círculo, fluxo). Ela funciona como um **"se"**: o espírito confere, no instante em que lê a linha, se o acontecimento
dela é verdade *agora*. Se não for, a linha não faz nada. Ela não faz parte do que o feitiço faz.

Há dois tipos de condição:

- **Acontecimentos** valem se se deram no último segundo (o servidor guarda sempre, em fluxo ou não, quando cada um se
  deu). Em fluxo, cada um acorda o espírito.
- **Estados** valem enquanto duram. Em fluxo, acordam o espírito no instante em que começam.

| Runa | Nome | A linha vale se quem conjura… | Gatilho | Tipo |
|---|---|---|---|---|
| `ferit` | golpear | acabou de golpear algo | `attack` | acontecimento |
| `patitur` | sofrer | acabou de ser ferido | `hurt` | acontecimento |
| `necat` | matar | acabou de matar uma criatura | `kill` | acontecimento |
| `salit` | pular | acabou de pular | `jump` | acontecimento |
| `cadit` | cair | acabou de aterrissar de uma queda (2 blocos ou mais) | `land` | acontecimento |
| `frangit` | quebrar | acabou de quebrar um bloco | `break` | acontecimento |
| `utitur` | usar | acabou de usar o que tem na mão (botão direito, no ar ou num bloco) | `use` | acontecimento |
| `latet` | agachar | está agachado | `sneak` | estado |
| `currit` | correr | está correndo | `sprint` | estado |
| `languet` | enfraquecer | está com 30% da vida ou menos | `low_health` | estado |
| `ardet` | arder | está em chamas | `burning` | estado |
| `mergitur` | submergir | está debaixo d'água (os olhos na água) | `underwater` | estado |

- Golpear e dizer `surgit` logo em seguida lê `ferit igni iactare` e lança o fogo; sem o golpe, nada. Agachado,
  `latet aura iactare` lança o ar; em pé, nada.
- Escreve-se **abrindo a linha**: `ferit igni iactare`. A gramática ignora a condição em qualquer posição, então
  `igni ferit iactare` também funciona.
- **"Se" e "e" são implícitos.** Uma condição já é um "se", e várias lado a lado valem **todas juntas**:
  `ferit patitur igni iactare` só vale se o mago acabou de golpear *e* de ser ferido.
- **`aut` ("ou")** separa as condições em grupos; a linha vale se qualquer grupo valer. `ferit latet aut patitur` é
  "(golpeou e está agachado) ou (foi ferido)".
- **`non` ("não")** vira ao contrário a condição logo depois dele: `non latet igni iactare` só lança em pé. Em fluxo,
  uma condição negada só filtra; quem desperta a linha é uma condição afirmativa (`wakesFor`).
- **"Enquanto" é o `chronos` sem número logo depois de uma condição**, como no livro (cap. 4.3.2: a barreira
  "permanece ativa e se regenera enquanto a chama escolhida estiver acesa"). `latet chronos aura impediunt` conjura a
  linha em janelas de 1 segundo (`chronos 1`), de novo a cada segundo enquanto as condições valerem: cada janela custa
  o seu (uma torneira aberta). Para quando as condições deixam de valer, quando o mago morre ou sai, ou quando uma
  janela falha. A mesma linha não roda duas vezes ao mesmo tempo.
- As linhas sem condição de uma página continuam valendo sempre.
- A condição ocupa uma coluna, como toda runa: a coluna é o relógio (`SpellBlock`), então ela atrasa a linha em um passo.
- **Glifos:** os números ganharam glifos próprios e deixaram as letras Q–Z para as condições
  (docs/marcas-como-runas-design.md). Q `ferit`, R `patitur`, S `necat`, T `salit`, U `cadit`, V `frangit`,
  W `utitur`, X `latet`, Y `currit`, Z `ardet`. Depois do Z, os glifos são do mod (docs/glifos-design.md):
  `languet` U+E010, `mergitur` U+E011, `aut` U+E012 e `non` U+E013.

## 4. Em fluxo: o espírito escuta

Em fluxo, cada acontecimento acorda o espírito, que lê **tudo o que o `surgit` leria** e mais a armadura:

- o pergaminho, a inscrição ou o círculo para onde se olha ou ao alcance da mão;
- as coisas gravadas nas duas mãos **e na armadura vestida** (docs/galdraria-design.md);
- a página destacada na mão (que se gasta, como no `surgit`, quando alguma linha dela é conjurada);
- a página aberta do grimório.

Ao contrário do `surgit`, que lê a primeira fonte que encontra, o fluxo lê **cada uma**. Em cada uma, conjura só as
linhas que têm uma condição daquele acontecimento e cujas condições valem todas. Linhas sem condição não disparam em
fluxo. O que não tem nada para o acontecimento é ignorado em silêncio, e a leitura não deixa recarga.

- Uma espada com `ferit igni iactare` lança fogo a cada golpe.
- Um peitoral com `patitur aura impediunt` afasta o ar ao redor quando o mago é ferido.
- O mesmo acontecimento só acorda o espírito de novo depois de **meio segundo**, para que um feitiço que fere o próprio
  mago não desperte a si mesmo sem parar.
- O feitiço desperto é conjurado no tick seguinte ao acontecimento, fora do evento que o despertou.

## 5. Fora do fluxo

Nada muda para quem não está em fluxo, a não ser a avaliação da condição: o transe, o `surgit`, a marca, os rituais e
os círculos leem as linhas com condição como qualquer outra e as conjuram se a condição vale naquele instante. Se
nenhuma linha vale, o espírito responde que a condição não se cumpre agora.

## 6. O que existe no código

- `magic/lexicon/WordClass.CONDITION`, `ConditionSpec` (o gatilho), `Lexicon.triggerOf` / `isCondition`.
- `runes.json`: `ferit`, `patitur`, `necat`, com `"class": "condition"` e `"trigger"`.
- `SpellGrammar`: pula a condição. `SpellReading` e `SpellDescription`: explicam a condição no grimório
  ("Se quem conjura acabou de golpear algo: …").
- `spelling/flow/Happenings`: quando cada acontecimento se deu (vale por um segundo) e como se reconhece cada estado
  (vale enquanto dura); quais estados acabam de começar, a cada tick.
- `spelling/flow/FlowState`: as sessões, o custo, o meio segundo de espera.
- `spelling/flow/FlowEvents`: golpe (`AttackEntityEvent`), ferimento (`LivingHurtEvent`), morte (`LivingDeathEvent`),
  pulo (`LivingJumpEvent`), aterrissagem (`LivingFallEvent`), bloco quebrado (`BlockEvent.BreakEvent`), uso
  (`RightClickItem`, `RightClickBlock`) e o começo de cada estado (o tick do jogador).
- `FlowTogglePacket` (tecla → servidor), `FlowStatePacket` (servidor → HUD), `client/ClientFlow`.
- `spell/block/LineConditions`: a expressão de condições da linha (lado a lado, `aut`, `non`) e o `chronos` de
  "enquanto". `ConditionSpec.Logic`: o que cada palavra de condição faz (`TRIGGER`, `OR`, `NOT`).
- `ServerSpellingController.passes`: a regra única de toda leitura (as condições valem agora; em fluxo, a linha fala do
  acontecimento). Vale em `castPage`, `castRitualText`, `castCircle` e no transe. `wake`: a leitura do fluxo.
  `sustain`: as linhas de "enquanto".

## 7. Perguntas abertas

1. O feitiço desperto por `ferit` deveria mirar quem foi golpeado, em vez da direção do olhar?

## 8. Decisões tomadas

1. O fluxo é um transe sustentado, ligado e desligado por uma tecla. (04/10/2026)
2. Uma runa por condição, abrindo a linha. (04/10/2026)
3. Primeira leva: combate (golpear, ser ferido, matar). (04/10/2026)
4. Custo crescente: 0,5 UMU/s, mais 0,02 a cada segundo. (04/10/2026)
5. ~~O espírito escuta as mãos e a armadura vestida.~~ Substituída pela 7. (04/10/2026)
6. A condição é uma runa como qualquer outra: um "se" avaliado no instante da leitura, em toda leitura. (04/10/2026)
7. Em fluxo, o espírito escuta tudo o que o `surgit` lê, e mais a armadura vestida. (04/10/2026)
8. Segunda leva: movimento, trabalho e estado do corpo. Os estados valem enquanto duram e, em fluxo, despertam ao
   começar. (04/10/2026)
9. Os números ganham glifos próprios (da fonte "SGA Number Characters", redesenhados em 5×7) e Q–Z vão para as
   condições; os textos antigos são convertidos na primeira leitura. (04/10/2026)
10. Sem runas de "se" e "e" (implícitos); "enquanto" é o `chronos` sem número depois de uma condição, como no livro;
   runas novas `aut` ("ou") e `non` ("não"). (04/10/2026)
