# Elder Lexicon Alpha Backlog

## Alta prioridade

- Implementar Ligabis com molduras/item frames:
  - Fazer molduras entrarem no elo.
  - Permitir fan-out de pergaminhos vinculados com `surgit`.
  - Cobrar custo normal por pergaminho disparado.
  - Evitar recursao/loop de molduras ativando outras molduras indefinidamente.
- Remover ou aposentar placeholders Forge:
  - `example_item`.
  - `example_block`.
  - Logs temporarios como `HELLO FROM COMMON SETUP`.
  - Nomes internos herdados do template.
- Corrigir warnings de assets:
  - Renomear texturas `Book_and_Quill_Page_Blank_*` para nomes lowercase.
  - Remover ou completar modelos de `example_block` e `example_item`.
- Expandir interacoes de bloco por elemento:
  - `firmo` com blocos minerais, terra, pedra, dureza e resistencia.
  - `aura` com fogo, fumaca, projeteis, itens leves, pressao e movimento.
  - Fusoes (`caligo`, `lutum`, `pulvis`, `fusus`, `nebula`, `fulmen`) com efeitos proprios.

## Jogabilidade

- Melhorar feedback de feitico:
  - Mensagens claras quando acerta bloco.
  - Mensagens claras quando nao encontra alvo.
  - Mensagens claras quando falta recurso.
  - Avisos compreensiveis quando a sequencia de runas e invalida.
- Balancear custos UMU por feitico real.
- Definir duracao, alcance e intensidade por runa.
- Definir modificadores por varinha/conduite.
- Fechar progressao survival:
  - Receitas dos itens principais.
  - Forma de obter grimorio, paginas e varinhas.
  - Loot ou crafting para itens que hoje dependem do criativo.
- Criar exemplos jogaveis dentro do jogo:
  - Grimorio inicial.
  - Pergaminhos de teste.
  - Sequencias basicas de runas.

## Sistema de runas

- Implementar ou consolidar `surgit` como runa de ativacao, principalmente para molduras/pergaminhos.
- Fazer filtros alterarem comportamento de forma consistente:
  - `quantum`: quantidade/intensidade.
  - `chronos`: duracao.
  - `ubis`: alcance.
- Definir comportamento concreto para fusoes funcionais:
  - `transiectio`.
  - `aversio`.
  - `cohaesio`.
  - `exhaustio`.
  - `transvocatio`.
  - `deflectio`.
  - `vinculatio`.
  - `exsuctio`.
  - `evocatio`.
  - `compeditio`.
  - `exinanitio`.
  - `coniuratio`.
  - `extractio`.
- Melhorar validacao de sequencias invalidas.
- Garantir que o parser, o repertorio e o executor aceitem o mesmo conjunto real de runas.

## Vita

- Alinhar valores de equilibrio entre docs e codigo:
  - Docs: 56 Aqua, 38 Aura, 2 Igni, 4 Firmo.
  - Codigo atual: 55 Aqua, 38 Aura, 2 Igni, 5 Firmo.
- Balancear recuperacao em gameplay real:
  - Agua.
  - Sol.
  - Calor.
  - Chuva/agua.
  - Repouso.
  - Movimento.
  - Alimentos Firmo.
  - Sono.
- Balancear dano elemental e overflow.
- Melhorar HUD/overlay do Vita.
- Revisar efeitos negativos para ficarem perceptiveis sem atrapalhar demais.
- Separar melhor comandos debug do uso normal.

## UX

- Corrigir traducoes em `pt_br` com encoding quebrado.
- Revisar nomes exibidos de itens, telas e mensagens.
- Melhorar a aba criativa:
  - Ordem tematica final.
  - Possiveis separadores visuais.
  - Remover qualquer item nao jogavel.
- Melhorar tela de repertorio:
  - Fluxo de configurar hotbar/runa.
  - Estado visual de slot vazio.
  - Indicacao de runas que exigem alvo.
- Melhorar tooltips:
  - Como usar varinhas.
  - Como usar grimorio.
  - Como usar pincel de Mark.
  - Como interpretar Bússola Vita.

## Tecnico

- Adicionar testes para interacoes de bloco:
  - `igni` em fogueira.
  - `igni` em furnace/smoker/blast furnace.
  - `aqua` em caldeirao.
  - `aqua` em fogo/fogueira.
  - `aqua` em farmland.
- Reduzir uso de reflection quando possivel:
  - Furnace burn state.
  - Reset de death state em Ligabis.
- Revisar e consolidar a worktree.
- Padronizar nomes internos:
  - `ExampleMod` -> nome real do mod.
  - `example_tab`/`example_*` removidos ou substituidos.
- Alinhar docs com implementacao real.
- Separar documentos de plano, design e backlog para evitar ambiguidade.

## Ordem sugerida para alpha

1. Limpar placeholders e warnings de assets.
2. Implementar Ligabis com molduras e fan-out de `surgit`.
3. Implementar filtros `quantum`, `chronos` e `ubis`.
4. Melhorar feedback de feiticos.
5. Balancear Vita/UMU.
6. Fechar receitas e progressao survival.
