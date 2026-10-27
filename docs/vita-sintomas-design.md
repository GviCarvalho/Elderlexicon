# Vita: os sintomas do corpo

Decidido com o autor em 15/10/2026. O ponto de partida: "como seria se a magia existisse no mundo real?". Um corpo em
desequilíbrio não ganha um status mágico; ele **sente** alguma coisa, como um corpo de verdade sente. E quem sente não
sabe a causa: é para isso que existe a Bússola da Vida.

## Regras

- **Sem ícones, sem efeitos de poção.** Nenhum `MobEffect`: os efeitos aparecem no inventário (mesmo com o ícone
  escondido) e o leite os tiraria. Os sintomas são atributos (velocidade, força, ritmo do braço), eventos (quebrar,
  pular, cair) e sensações no cliente (câmera, visão, coração).
- **Leve primeiro, grave só no extremo.** Cada desvio vira uma gravidade de 0 a 1 que cresce em curva: quase nada até
  uns 15% de desvio, sintomas leves até a metade, e os episódios fortes (desmaio, cãibra, cólica) só perto do fim.
- **O sintoma é do corpo, não do elemento.** O jogador sente frio, tremor, coração disparado, falta de ar, peso; nunca
  "Igni baixo". A Bússola é quem diz o porquê.
- **Nada de dano extra.** A vida já segue o elemento mais em falta (vita-design.md, "O corpo como cálice"). Os sintomas
  atrapalham; quem mata é a falta. A morte pela falta ganha nome: frio, sangue, fôlego, ossos.
- **O excesso é passageiro** (a homeostase o expele em segundos), então os sintomas de excesso são ondas curtas. E o
  excesso empurra os outros elementos para fora: um corpo cheio de água fica sem ar e sente a falta de ar pela regra,
  não por um sintoma inventado.

## A gravidade

`desvio = elemento / parte cheia − 1`. Na falta, `g = ((falta − 0,15) / 0,6)²`, limitada a 1 (cheia aos 75% de falta).
No excesso, `g = ((excesso − 0,1) / 0,5)²`. Uma pessoa a meia vida por cortes está com o sangue a 50%: g ≈ 0,34,
sintomas leves. A dois corações, g = 1.

## Por elemento

| | Falta | Excesso |
|---|---|---|
| **Igni**, o calor | **Hipotermia.** Tremor fino na câmera, mãos dormentes (quebra e golpe mais lentos), passo mais lento, mais fome (o corpo queima comida para se aquecer). Grave: a geada nas bordas da tela (o congelamento do jogo, sem chegar a ferir) e sonolência. | **Febre.** A visão esquenta nas bordas, mais sede, e o suor gasta um pouco de água. Grave: tontura (a câmera balança devagar) e ondas de calor que escurecem a vista. |
| **Aqua**, o sangue | **Desidratação, perda de sangue.** Sede (fome), passo e golpe mais fracos. Médio: correr ou pular escurece a vista (a pressão cai), o coração bate alto. Grave: desmaios curtos. | **Edema.** Corpo pesado e inchado. Grave: pressão na cabeça (a visão pulsa). A falta de ar vem sozinha, do ar empurrado para fora. |
| **Aura**, o fôlego | **Hipóxia.** Falta de ar: a corrida não se sustenta, as bolhas de ar aparecem fora d'água, a visão se fecha num túnel. Grave: desmaios. | **Hiperventilação.** Cabeça leve (a visão respira), formigamento nas mãos. Grave: cãibras nas mãos (por um instante não se golpeia nem se quebra nada) e desmaios. |
| **Firmo**, os ossos | **Fragilidade.** Ossos fracos como vidro: quedas doem mais, e uma queda, um esmagamento ou um golpe forte pode **fraturar**. | **Calcificação.** Rigidez: passo e pulo mais curtos, braço mais lento. Grave: cólicas, uma dor que trava o corpo por um instante. |

## A fratura

A única marca que fica. Um golpe que tira os ossos (queda, esmagamento) ou um golpe forte de criatura pode quebrar um
osso; a chance cresce com o dano e com a falta de firmo (ossos inteiros quase nunca quebram).

- **Perna** (queda, esmagamento): manca (passo bem mais lento), não corre, pula baixo, e cair dói mais.
- **Braço** (golpe): golpe fraco e lento, quebra mais devagar.
- **Calcificar.** O osso só se emenda com os ossos cheios (firmo a 90% ou mais); leva um dia do jogo assim, o sono
  acelera três vezes, e terra posta no corpo além do cheio (um feitiço) acelera dez. Com os ossos em falta, a fratura não
  sara: primeiro se come e se descansa, ou se conjura terra.
- A fratura fica salva com o corpo e acaba com a morte.

## No código

- `vita/Symptoms.java`: a gravidade, os atributos e os episódios (desmaio, cãibra, cólica) no servidor; substitui os
  quatro `*ImbalanceEffects`.
- `vita/Fractures.java`: a fratura (chance, efeitos, calcificação).
- `vita/network/BodyPacket` leva ao jogador os quatro desvios, as fraturas e o episódio; `client/BodySense.java` faz a
  câmera, a visão, o coração e a corrida que falha.
- Mortes pela falta: `elderlexicon:hypothermia`, `exsanguination`, `asphyxia`, `shattering`.
