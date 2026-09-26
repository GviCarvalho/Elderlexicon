# Impediunt de fonte: a zona que o elemento não atravessa

O grimório só dá o nome da runa, M: impediunt, "repelir" (apêndice 10.2), e não descreve a mecânica. Esta proposta segue
as regras de filtro que já valem para as outras funções. O nome é lido ao pé da letra: *impediunt* = "impedem".

**Quando há uma marca antes** (`m1 impediunt`), nada muda: a coisa marcada é empurrada para longe
(`marcas-como-runas-design.md`). Quando é **imagem** (`igni surgit impediunt`), vale o que diz `surgit-visao-design.md`.

## Regras

- **Centro: o `ubis`.** Os casos, iguais aos do vocant:
  - sem `ubis`: o centro é o mago e acompanha o mago;
  - com uma marca (`eu ubis`, `m1 ubis`): o centro acompanha a marca;
  - com um lugar (`10 ubis`, coordenadas): o centro fica fixo nesse lugar.
- **Tamanho: o `quantum`.** O filtro age no que vem depois, então o `quantum` é a potência, e a potência compra **área**:
  - raio = 3 × √(UMU/10), com máximo de 32;
  - 10 UMU (o padrão) → raio 3; 40 → raio 6; 100 → raio ~9,5;
  - paga-se o que passa de 10 UMU.
- **Duração: o `chronos`.**
  - Sem ele, a zona dura 2 s.
  - Com ele, dura o tempo escrito e é uma torneira aberta: gasta a potência a cada janela de 2 s (SpellFlow).
- **Forma: um cilindro com o raio definido e 3 blocos de altura, a partir do nível dos pés do centro.** O chão nunca faz parte da zona.

## O que acontece com cada elemento

| Elemento | Ao abrir a zona | Enquanto ela dura (a cada 5 ticks) |
|---|---|---|
| igni | o fogo de dentro apaga e reacende logo depois da borda, onde puder queimar; a fonte de lava vai para a borda | o fogo que entra é apagado |
| aqua | a água de fonte vai para a borda | a água que escorre para dentro é tirada |
| firmo | a terra de dentro (acima do chão) é empurrada e **se empilha na borda como um muro**; se o muro já estiver cheio, o bloco vira item | nada; o muro fica depois que a zona acaba |
| aura, vis, sem fonte | nenhum bloco | só as criaturas |

- **Criaturas e itens.** Os do elemento são empurrados para fora e mantidos fora. Com vis, ou sem fonte, isso vale para todos. O mago nunca é empurrado.
- **Borda visível.** Um anel de partículas do elemento marca a borda enquanto a zona dura.

## O que é invocado dentro vai para a borda

A zona não proíbe o elemento, **empurra**. Tudo o que um feitiço tenta criar dentro dela vai parar do lado de fora, e
o anel ou o muro nasce dessa briga, sem ser desenhado:

- **Blocos (fogo, terra, água):** empurrado igualmente para todos os lados a partir do centro, o que cairia dentro
  **se espalha pela borda inteira de uma vez**. O fogo vira o anel completo no mesmo instante, e a terra vira uma camada
  inteira do muro; cada nova porção que o vocant manda sobe o muro mais uma camada, até 3 blocos de altura. O fogo que
  já estava dentro quando a zona abre também vira o anel na hora. Esse fogo é real: queima, e o exsugat pode puxá-lo.
- **Efeitos em área:** o calor do fogo que queima as criaturas e o vento (com `aura`, soprando para cima quando é
  invocado nos pés de uma marca) agem numa **faixa** logo depois da borda, da largura do próprio efeito. No meio fica
  calmo.

## Exemplo: o anel de fogo

```
igni eu ubis quantum 40 chronos 30 vocant
igni eu ubis quantum 40 chronos 30 impediunt
```

- O vocant fica acendendo fogo nos pés do mago durante 30 s.
- O impediunt empurra esse fogo para fora, então ele vai se acumulando num anel de raio 6.
- As criaturas nessa faixa queimam, e o mago fica seguro no meio.

Com `firmo`, o mesmo par ergue um muro em volta. Com `aura`, forma uma coluna de vento subindo ao redor.

### Puxar o anel e lançar

Estando dentro de uma zona do mesmo elemento, o exsugat alcança a zona inteira e a borda dela, e não só os 5 blocos de
costume. Com `igni exsugat quantum iactare` (o `quantum` sem número, livro 4.3.2), todo o anel é puxado e lançado de
uma vez.
