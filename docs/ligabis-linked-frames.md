# Objetivo
Permitir que `ligabis` conecte molduras com pergaminhos de magia, de modo que ativar o `surgit` em uma moldura dispare automaticamente os pergaminhos das demais molduras vinculadas (fan-out da mesma Mark), respeitando alcance, duracao do elo e os custos/avisos de cada cast.

# Diagnostico atual
- `LigabisFunctionHandler` so aceita `LivingEntity`, `ItemEntity` e blocos como `LinkTarget`; `ItemFrame` cai no fallback e nao entra no elo.
- `LigabisLinkManager` guarda apenas UUIDs de entidades vivas ou posicao de bloco; logo molduras nao aparecem nos mapas de membros.
- `ServerSpellingController.processFramedPageSpell` trata uma moldura por vez e nao conhece ligacoes ativas.

# Estrategia
1) **Novo alvo Ligabis para moldura**  
   - Adicionar `FrameTarget implements LinkTarget` guardando `ItemFrame` (UUID + posicao).  
   - Ajustar `LinkTarget.fromImpact` para retornar `FrameTarget` quando a entidade atingida for `ItemFrame`.  
   - No elo, tratar moldura como seguidor legitimo (pode ser mestre tambem, se for o primeiro alvo).

2) **Persistencia de molduras no manager**  
   - Expandir `LigabisLinkManager` para aceitar membros genericos por UUID, nao apenas `LivingEntity`.  
   - Registrar `ItemFrame` em `allMembers` e `protectedMembers` conforme direcao do elo.  
   - Para mestre bloco: ja existe caminho; para mestre moldura: armazenar UUID e dimensao.

3) **Propagacao de surgit**  
   - Em `ServerSpellingController.processFramedPageSpell`, apos encontrar a moldura principal, consultar `LigabisLinkManager.find` usando o UUID dela (precisa overload que aceite `Entity`, nao so `LivingEntity`).  
   - Se nao houver elo ou estiver expirado, comportamento atual.  
   - Se houver elo valido:  
     * Coletar todas as molduras seguidoras no mesmo elo (excluir mestre se direcao ONE_WAY).  
     * Validar distancia/linha de visao? (opcao: apenas verificar que estao carregadas na mesma dimensao).  
     * Para cada moldura, executar o mesmo fluxo de extracao de runas e `castingService.cast` independentemente.  
     * Acumular avisos/erros: se qualquer cast falhar, retornar a primeira falha; senao mesclar avisos e aplicar cooldown baseado no maior dos casts.  
     * Opcional: marcar no response uma lista de molduras acionadas para feedback.
   - Evitar loop: fan-out so ocorre quando a entrada original foi `surgit` manual (nao reencadear quando disparar seguidor).

4) **Feedback e custos**  
   - Cada cast consome custo normal do jogador; somar UMU total gasto e exibir em aviso.  
   - Cooldown: usar o maior cooldown aplicado entre as execucoes para evitar spam.  
   - Se um pergaminho estiver vazio/invalidado, abortar apenas aquele cast e seguir com os demais, mas reportar warning.

5) **Limpeza e validade**  
   - `LigabisLinkManager.cleanup` continua removendo elos expirados; ao tentar acionar moldura ausente/descarregada, remover membro e prosseguir com os restantes.  
   - Se mestre do elo for bloco e estiver quebrado, limpar elo antes do fan-out (mesma regra ja usada para imortalidade).

# Pontos de implementacao
- `src/main/java/com/elderlexicon/mod/spell/function/LigabisFunctionHandler.java`: criar `FrameTarget`, ajustar `LinkTarget.fromImpact`, permitir mestre seguidor de moldura em `addTarget`.  
- `src/main/java/com/elderlexicon/mod/spell/function/LigabisLinkManager.java`: generalizar `find`/`register` para `Entity`, armazenar UUIDs de `ItemFrame`, e expor `find(Entity, long gameTime)`.  
- `src/main/java/com/elderlexicon/mod/spell/function/LigabisEvents.java`: decidir se molduras precisam de protecao especial (provavelmente nao; ignorar em eventos de morte).  
- `src/main/java/com/elderlexicon/mod/spelling/server/ServerSpellingController.java`: fan-out em `processFramedPageSpell`; helper para executar `castFramedPage(ItemFrame frame, Player player, long now)` reutilizando logica existente.  
- `src/main/resources/.../lang/*.json`: adicionar mensagem curta informando quantas molduras foram ativadas (ex.: `overlay.elderlexicon.spelling.linked_frames`).

# Testes sugeridos
- Criar elo bidirecional com duas molduras; usar `surgit` olhando para uma: ambos pergaminhos disparam e o jogador sofre custo duas vezes.  
- Elo unidirecional (com `vertere`): apenas seguidores disparam quando o mestre e acionado; acionar seguidor nao dispara mestre.  
- Moldura sem pergaminho no elo: fan-out pula esse membro e retorna warning.  
- Elo expirado: `surgit` volta a funcionar apenas na moldura alvo.  
- Moldura descarregada (chunk off): nao falha o cast nas demais; warning de membro inalacancavel.  
- Jogador sem runas exigidas por um dos pergaminhos: apenas esse cast retorna erro, os outros executam (confirmar regra de negocio desejada).
