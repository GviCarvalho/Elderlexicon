# Aprender uma runa: entender e sonhar

> "Nahl" é a palavra antiga para "ideia" […] "vá" é a evolução da palavra "veil", que significa "espaço", "dimensão",
> "plano". (Grimório de EVL, 2.1)
>
> A linguagem antiga é um idioma que nasceu em Nahvá, no mundo adormecido, onde vivem as ideias. (4)

**Status:** o ciclo completo do `igni` está implementado (06/10/2026), numa primeira versão do desafio. Não testado
dentro do jogo (a dimensão carrega sem erro no servidor de testes).

## 1. O conceito

Saber que `igni` quer dizer "fogo" não ensina a runa. Escrevê-la mil vezes não ensina, nem a pronúncia perfeita. Para
aprender `igni`, o mago precisa **entender o calor**: não a palavra, nem o conceito, mas o que é de fato estar quente e
estar frio, queimar e congelar.

1. **Entender:** o mago vive, no corpo, as experiências que a runa significa.
2. **Sonhar:** entendida a runa, ele passa por um **sonho vívido**: o seu espírito vai, em Nahvá, a uma **dimensão
   que é a ideia daquela runa**.
3. **Escapar ou morrer:** só se acorda desse sonho escapando da dimensão ou morrendo nela.
   - **Escapou:** passou no desafio, e a runa é aprendida (`RuneUnlocks.unlock`).
   - **Morreu:** simplesmente acorda, sem ter aprendido a runa.
4. **O mesmo vale para todas as runas**, cada uma com o seu entendimento e a sua dimensão.

## 2. O que já existe e se encaixa

- **Os estados do Vita** (a Bússola Vita os mostra): o entendimento dos elementos pode ser vivê-los.
  | Runa | Estados do corpo |
  |---|---|
  | `igni` | Temperatura: Frio, Hipotermia, Quente, Fervendo |
  | `aqua` | Hidratação: Baixa, Crítica, Alta, Transbordando |
  | `aura` | Circulação: Baixa, Crítica, Alta, Sobrecarregada |
  | `firmo` | Minerais: Baixos, Deficientes, Elevados, Rígidos |
- **Os acontecimentos do fluxo** (`Happenings`): golpear, ser ferido, matar, pular, cair, quebrar, usar, agachar,
  correr, pouca vida, em chamas, debaixo d'água. São o entendimento natural dos **Fatos** (`ferit`, `cadit`…).
- **O registro de runas desbloqueadas** e o aviso de runa aprendida (docs/repertorio-design.md).

## 3. Decisões (06/10/2026)

1. **O sonho vem com uma chance**, a cada noite dormindo, para uma runa entendida e ainda não aprendida (35%).
2. **No sonho:** voo criativo e UMU infinita (nada é cobrado, nada passa pelo corpo), mas **nenhum item** no inventário.
   Itens podem ser conseguidos lá, de maneira fácil.
3. **Acordar é sempre ruim**, tendo escapado ou morrido: náusea (10 s), fraqueza (1 min), lentidão (30 s) e cansaço
   (1 min). O mago acorda na cama com tudo o que tinha: inventário, vida e fome de antes do sonho.
4. **Uma só dimensão, Nahvá, com várias regiões.** Cada região é como um planeta regido pelo kern: uma região não pode
   ver outra. Cada jogador sonha **sozinho**, numa instância só dele (há uma exceção, a definir).
5. **O entendimento das Funções, dos Filtros e dos Fatos** vem de ações que os representam: soltar uma flecha é
   aplicar força em algo (`iactare`); largar um item é trazer algo de dentro do corpo (`vocant`). A pensar runa a runa.
6. **O mago começa sem saber nenhuma runa:** todas, até as fontes, se aprendem assim.
7. **No repertório**, uma runa entendida que espera o sonho mostra o nome no **texto secreto** do Minecraft (o
   embaralhado), embaixo do bloquinho escuro.

## 4. O ciclo do `igni` (implementado)

- **Entender:** viver os quatro estados de temperatura do Vita (Frio, Hipotermia, Quente, Fervendo), **pegar fogo** e
  **congelar** (na neve fofa). Cada experiência nova dá um aviso discreto ("Você sente o frio de verdade."); com todas,
  "Você entende o calor por completo [...] Talvez sonhe com isso." As experiências de cada runa são dados
  (`lexicon/understanding.json`); o que cada experiência é fica em `nahva/Understanding`.
- **Sonhar:** dormindo na cama, a cada noite, 35% de chance. O espírito vai à região do `igni`.
- **A região do `igni`** (`nahva/IgniRealm`), primeira versão: um mar de lava sob uma cadeia de ilhas de netherrack em
  chamas, com névoa de brasa. **O ar queima:** a cada segundo, fora d'água e sem nada frio na mão, o sonhador pega fogo
  e se fere. **O frio é o caminho:** um baú ao lado de onde o sonhador aparece tem gelo azul, baldes de água, neve fofa e
  bolas de neve; **segurar algo frio apaga o fogo** e impede o ar de queimar. **A fenda** é um anel de obsidiana chorona no fim da cadeia, a
  270 blocos; atravessá-la é escapar.
- **O voo** é religado depois da viagem a Nahvá (a troca de dimensão o desligava no sobrevivência) e mantido a cada
  tick enquanto o sonho dura.
- **Acordar:** escapando, aprende `igni` ("Você aprendeu a runa igni."); morrendo (no fogo, na lava, no vazio),
  acorda sem aprender. Sair do jogo no meio do sonho acorda o mago antes de salvar.

**Por dentro:** `nahva/Nahva` (a dimensão e as instâncias), `RealmLayout` (onde fica cada região), `Realms` (o que cada
região faz), `Dreams` (dormir, sonhar, acordar), `client/NahvaFog` (a névoa); `data/elderlexicon/dimension/nahva.json`
(um vazio plano, céu do End, sempre noite). O entendimento fica no repertório (`experienced`, `understood`).

**Testar:** `/runas entender igni` (como se tivesse vivido tudo) e `/runas sonhar igni` (o sonho na hora). Num mundo
antigo, `/runas bloquear igni` antes, se o mago já o tinha nos slots.

## 5. Perguntas abertas

1. **O desafio do `igni`:** a primeira versão é um rascunho. Como deve ser?
2. **O entendimento de cada outra runa** (ações que as representam).
3. **A exceção** ao sonho solitário.
