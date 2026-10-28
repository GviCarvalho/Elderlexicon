# A Torre de Pesquisa

Decidido com o autor em 27/10/2026. O observatório do núcleo era só decoração. No campus ele vira a **Torre de
Pesquisa**: a casa dos pesquisadores (Lectores), com quatro funções úteis de verdade. As universidades antigas, as do
`UniversityPiece`, continuam com o observatório de antes.

## O prédio

Três torres no lugar do observatório, a sudeste do núcleo (`CampusBuildings.pesquisa`):

- **A torre do Atlas** (raio 6, 33 de parede, cone de ardósia): a escada em caracol em volta de uma coluna e um andar
  para cada coisa.
  - **Térreo:** o balcão dos pesquisadores e os barris dos pedidos da casa. O pesquisador do Atlas fica atrás do
    balcão.
  - **Andar 7:** a biblioteca de feitiços (estantes em volta, um atril em cada quarto da sala).
  - **Andar 14:** a sala dos mapas (mesas de cartografia).
  - **Andar 21:** a Siglística Pura e a pós (escrivaninha, suporte de poções, uma cama roxa para as idas a Nahvá).
  - **Alto:** o observatório, vidro em volta entre oito pilares sob o cone, a luneta (a estação da aula do `surgit`) e
    a mesa das estrelas.
- **O laboratório de medidas** (raio 5, cone de cobre): o recipiente em que uma coisa se desfaz em partículas, no meio,
  com os instrumentos em volta. Em cima, o depósito de amostras, por escada de mão.
- **A torrezinha de vigia**, com escada de mão até o mirante.

## As funções, em etapas

1. **Os mapas do Atlas** (feito em 27/10/2026, `university/Atlas.java`). O pesquisador atrás do balcão faz um mapa do
   que a torre vê ao redor, até 100 chunks: a torre de mago, a torre de feiticeiro, a mansão da floresta (os illagers
   que leem magia) ou o portal em ruínas mais perto. O mapa sai com a marca no lugar e o terreno já desenhado.
   - Preço em esmeraldas: torre de mago 4, de feiticeiro 6, mansão 8, portal 3. Quem estuda na casa (tem um grau)
     paga a metade.
   - Se a torre não vê nenhum, ele diz e não cobra.
   - As torres do mod ganharam as tags `elderlexicon:wizard_tower` e `elderlexicon:sorcerer_tower` para isso.
2. **A sala de medidas** (feito em 27/10/2026, `university/Measures.java`). Com um bloco ou item na mão, clica-se com
   o botão direito no recipiente, e os instrumentos dizem o que ele é (a substância da tabela de materiais), quanto
   pesa em UMU, o **cerne** (os quatro elementos em partes de cem) e, se ele tiver falta, quanto falta de cada
   elemento (docs/particulas-design.md, 3 e 6). Nada se gasta: medir não consome a coisa, e o caldeirão não faz o que
   faria (encher, esvaziar).
   - **O recipiente é a montagem, não o lugar:** um caldeirão sobre pedra talhada com pelo menos duas magnetitas em
     volta (até dois blocos). Quem montar um em casa tem o mesmo instrumento.
   - Coisa feita (uma espada, um baú) não tem cerne: os instrumentos dizem que o que a faz ser o que é não é proporção
     (a Forma).
   - A matéria do mod (a da mesa de invocação) é medida pelo que guarda.
3. **A biblioteca de feitiços** (feito em 27/10/2026, `university/SpellLibrary.java`, as páginas em
   `data/elderlexicon/grimoires/research.json`). Um pesquisador no andar 7, entre as estantes, guarda os feitiços que a
   casa escreveu e testou, todos tirados do livro: os doze do primeiro ano (cap. 15), repor os ossos (cap. 22) e ler o
   próprio corpo nas pepitas da bússola (cap. 18). Ele mostra por estante (a luz e o calor, a água, a terra, o ar, para
   os corajosos, o corpo), e de cada feitiço o texto e o cuidado.
   - **Comprar** a página solta pronta, pelo preço dela (2 a 8 esmeraldas).
   - **Copiar** no papel de quem pede: metade do preço e uma folha de papel.
   - **Quem estuda na casa** copia só pelo papel, e compra pela metade.
   - Os achados de fora (as páginas dos feiticeiros, por exemplo) ficam para depois: a casa teria de decidir o que
     guarda do que é proibido.
   - O pesquisador do Atlas e o da biblioteca são o mesmo tipo de mago, com um posto (`Wizard.serveAt`).
4. **Os pedidos e a pós.** Os pesquisadores pedem amostras, idas a Nahvá ou feitiços testados, e pagam em runas, saber
   ou itens. A maior das quatro: tem o seu desenho antes de virar código.
