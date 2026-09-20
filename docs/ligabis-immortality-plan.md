# Plano de implementação – Ligabis “imortal” (via única e dupla)

Objetivo: tornar vínculos Ligabis (quando vinculado firmo dos alvos) capazes de interceptar a morte/remoção de alvos vinculados enquanto o elo estiver ativo e o mestre estiver íntegro, simulando o efeito de um Totem da Imortalidade (sem tela de respawn, sem perda de itens/XP, sem loops de morte/respawn). Para via única: apenas seguidores ganham esse efeito; para via dupla: todos os membros do elo protegidos enquanto o elo durar.

## Diagnóstico atual
- Links são criados no `LigabisFunctionHandler`, armazenados em memória com duração fixa (20 s).
- Via única tenta “curar” no tick interno, mas se a entidade chega a disparar `LivingDeathEvent`, o jogo segue para tela de morte/loot.
- Não há interceptação de morte global (eventos do Forge).
- Alguns estados visuais/flag de morte (`deathTime`, invulnerabilidade) não são resetados cedo o suficiente.

## Abordagem proposta
1. **Persistir/registrar elos ativos** em um serviço singleton (`LigabisLinkManager`) acessível por eventos Forge.
   - Guardar: UUID do mestre e seguidores, dimensão, tipo (ONE_WAY/BIDIRECTIONAL), aspecto, expiraEmTick.
   - Atualizar na criação (handler Ligabis), limpar na expiração/término.

2. **Interceptar morte globalmente**:
   - Assinar `LivingDeathEvent` (e opcionalmente `LivingDropsEvent` para garantir zero drop).
   - Se a entidade pertence a um elo ativo:
     - Validar: elo não expirou, mestre vivo e íntegro (bloco não quebrado).
     - Via única: se entidade == mestre → não cancela (morte válida). Se entidade é seguidor → cancelar morte.
     - Via dupla: cancelar morte se todos os participantes ainda válidos (ou opcionalmente se houver ≥2 vivos).
     - Ao cancelar: `event.setCanceled(true)`, `entity.setHealth(healthAfterTotem)`, `clearDeathState(entity)`, `applyTotemBuffs(entity)`, `entity.invulnerableTime = graceTicks`, `entity.setDeltaMovement(Vec3.ZERO)`, `entity.fallDistance = 0`.
     - Garantir que `deathTime` seja zerado (campo obfuscado fallback).
   - Para blocos mestres: validar na hora consultando o estado do bloco; se ar/invalid → considerar elo quebrado e remover proteção.

3. **Drops e XP**:
   - Em `LivingDropsEvent`, se entidade protegida teve morte cancelada, limpar lista de drops e `event.setCanceled(true)` (ou simplesmente `event.getDrops().clear()` e `event.setShouldDropExperience(false)`).
   - Evitar mexer em mobs permanentes: só atuar quando realmente houve interceptação (flag no manager).

4. **Renovação de duração**:
   - Se quiser prolongar proteção, estender `expiraEmTick` ao disparar o totem (ex.: +2 s) para evitar spam de morte em looping.
   - Caso contrário, manter duração original e remover elo ao expirar.

5. **Sincronização de mestre**:
   - Via única: proteger apenas se mestre ainda `isAlive`, não `isRemoved`, e (se bloco) ainda presente.
   - Via dupla: proteger enquanto houver pelo menos 2 vivos e o elo não expirou.

6. **Limpeza**:
   - Tick de limpeza no manager (ex.: a cada segundo) para remover elos expirados ou com todos os membros inválidos.
   - Remover listeners/elos ao sair do mundo (desconexão) para evitar leaks.

7. **Integração com o handler existente**:
   - No `LigabisFunctionHandler`, ao criar/atualizar elo, registrar no `LigabisLinkManager` com dados completos (UUIDs, dimensão, direção, prazo).
   - Remover lógica de ressurreição/criação de entidades no tick interno; focar apenas em sincronização de aspectos e deixar a imortalidade para o evento global.

## API/eventos a usar
```java
@SubscribeEvent
public void onLivingDeath(LivingDeathEvent event) {
    LivingEntity ent = event.getEntity();
    var link = LigabisLinkManager.findActive(ent);
    if (link == null) return;
    if (!link.isProtected(ent)) return; // respeita direção e estado do mestre
    if (!link.masterIntact()) { link.invalidate(); return; }

    event.setCanceled(true);
    ent.setHealth(Math.max(1.0F, ent.getMaxHealth() * 0.5F));
    ent.setDeltaMovement(Vec3.ZERO);
    ent.fallDistance = 0.0F;
    ent.invulnerableTime = 20;
    clearDeathState(ent);
    applyTotemBuffs(ent); // regen/absorption/fire_resist
    link.markTotemProc(); // opcional: estender tempo ou registrar cooldown
}
```

## clearDeathState (reflexão)
- Tentar `LivingEntity.deathTime` via `getDeclaredField("deathTime")`, fallback obfuscado (`f_20915_`).
- `field.setInt(ent, 0);`
- Opcional: `ent.setHealth` antes do cancel para evitar estados inconsistentes.

## Dados a armazenar por elo
- `UUID mestre`, `List<UUID> seguidores`, `ResourceKey<Level> dimension`, `Direction direction`, `long expiresAt`, `Aspect aspect`.
- Flags: `lastTotemTick`, `active`.
- Helpers: `isMember(UUID)`, `isProtected(UUID)`, `masterIntact(ServerLevel)`.

## Passos de implementação
1. Criar `LigabisLinkManager` (singleton) com mapa `UUID -> LigabisLink` e métodos `register`, `remove`, `cleanup`, `findActive(LivingEntity)`.
2. Mover/ajustar criação de elos no `LigabisFunctionHandler` para registrar no manager (incluindo dimensão e UUIDs).
3. Adicionar classe de eventos `LigabisEvents` com `@SubscribeEvent` para `LivingDeathEvent` e `LivingDropsEvent`.
4. Implementar `clearDeathState` e `applyTotemBuffs` utilitários.
5. Remover do handler lógica de recriação de entidades; manter apenas sincronização de aspectos e destruição de elos.
6. Adicionar limpeza periódica (tick server ou timer) no manager.
7. Testar:
   - Via única: mestre bloco, seguidor entidade → matar seguidor várias vezes, sem tela de respawn, sem drop/XP, buffs aplicados, mestre intacto.
   - Via única: matar mestre → elo quebra, seguidor morre normalmente.
   - Via dupla: ambos protegidos; matar um sem expirar elo → morte cancelada; expirar tempo → morre normalmente.
   - Jogador como seguidor: não perde inventário/XP, não vê tela de respawn, animação não trava.
   - Expiração de elo: após 20 s (ou valor configurado), proteção cessa.
   - Reconexão/Unload: limpar elos de entidades offline.

## Considerações
- Evitar loops: usar `lastTotemTick` para não reaplicar múltiplas vezes no mesmo tick.
- Garantir thread-safety (acesso em eventos/ticks do server → usar estruturas seguras ou limitar a thread do servidor).
- Manter comportamento de custo/duração já existente, a menos que o designer queira duração dinâmica.

