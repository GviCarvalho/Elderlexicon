# As falas de Phantur

Phantur é o guardião dos portões de Nahvá. Ele ergueu a torre de Lig'dar. Não é um vilão: é o que mantém a ordem das
coisas. Um espírito que volta ao mundo desperto volta sem a **represa** (grimório, 2.1.2), com um poder que pode
desfazer o mundo físico. Por isso tudo o que ele quer é que o mago **desista** e vá viver o pós-vida em paz na sua ilha.
Ele nunca impede ninguém à força: oferece a saída, sempre, e fala.

Ele precisa parecer vivo: às vezes uma palavra, às vezes uma frase, às vezes um devaneio longo. Ele é cansado, gentil,
irônico, às vezes ríspido, às vezes triste; lembra das tentativas anteriores; fala dos que vieram antes (alguns ainda
estão na torre); fala de Harlvilt (o mundo desperto), da represa, do kern, das janelas que mostram possibilidades e das
portas soltas no ar. Ele trata o mago por "tu".

**Este arquivo é a fonte das falas.** Depois de editar, rode `python tools/phantur_lines.py`: ele gera as traduções
(`lang/pt_br.json`, `lang/en_us.json`) e o índice que o jogo lê (`lexicon/phantur.json`).

**Formato:** cada `##` é uma ocasião (a chave entre crases). Cada fala é um bloco de linhas `> pt:` e `> en:` (o mesmo
número de cada), separado do próximo por uma linha em branco. As linhas de uma fala aparecem uma depois da outra, com uma
pausa do tamanho da linha. `{name}` é o nome do mago.

## `first` — a primeira chegada a Lig'dar

> pt: Ah. Mais um.
> pt: Não te assustes com a altura. Ninguém nunca viu o topo. Nem eu, e fui eu quem a ergueu.
> pt: Tu morreste, {name}. Isso não é um castigo. É só o fim de uma coisa que tinha que acabar.
> pt: Há uma ilha esperando por ti. É tua desde antes de nasceres. Lá não há fome, não há pressa, não há ninguém para decepcionar.
> pt: Mas tu vieste até aqui. Então sobe, se precisares. Eu estarei em cada degrau.
> en: Ah. Another one.
> en: Do not fear the height. No one has ever seen the top. Not even I, and I am the one who raised it.
> en: You died, {name}. That is not a punishment. It is only the end of something that had to end.
> en: There is an island waiting for you. It has been yours since before you were born. There is no hunger there, no hurry, no one to disappoint.
> en: But you came all this way. So climb, if you must. I will be on every step.

## `again` — chegar de novo à torre

> pt: De novo.
> en: Again.

> pt: Tu de novo. A torre lembra de ti. Eu também.
> en: You again. The tower remembers you. So do I.

> pt: Quantas vezes já subiste esta escada? Eu sei. Tu não.
> en: How many times have you climbed these stairs? I know. You do not.

> pt: Sabes o que mais me cansa nos que voltam?
> pt: Não é a teimosia. A teimosia eu entendo; o mundo inteiro é feito dela.
> pt: É a esperança. Tu chegas sempre com a mesma, intacta, como se a última vez não tivesse acontecido.
> pt: Eu guardo todas as tuas tentativas. Tu não guardas nenhuma.
> en: Do you know what wearies me most in the ones who return?
> en: It is not the stubbornness. Stubbornness I understand; the whole world is made of it.
> en: It is the hope. You always arrive with the same one, untouched, as if the last time had never happened.
> en: I keep every one of your attempts. You keep none.

> pt: A ilha sentiu tua falta. Achei que devias saber.
> en: The island missed you. I thought you should know.

> pt: Olha a torre, {name}. Ela não ficou menor desde a última vez.
> en: Look at the tower, {name}. It has not grown any shorter since last time.

## `kern` — chegar à própria ilha

> pt: Bem-vindo. Aqui ninguém vai te pedir nada.
> en: Welcome. No one here will ask anything of you.

> pt: Olha ao redor. Isto é teu.
> pt: Não do jeito que as coisas eram tuas lá embaixo: emprestadas, gastando, quebrando.
> pt: Teu como um pensamento é teu. Molda-o como quiseres. Ele não vai envelhecer.
> pt: Descansa, {name}. Tu já fizeste o bastante.
> en: Look around. This is yours.
> en: Not the way things were yours down there: borrowed, wearing out, breaking.
> en: Yours the way a thought is yours. Shape it as you like. It will not grow old.
> en: Rest, {name}. You have done enough.

> pt: Ouve. Nada. É isso que a paz faz.
> en: Listen. Nothing. That is what peace does.

> pt: Tu voltaste para casa. Não te desculpes; todos voltam.
> en: You came home. Do not apologize; everyone does.

## `kern_idle` — devaneios na ilha, de tempos em tempos

> pt: Às vezes eu venho aqui só para ver alguém que parou de lutar. É bonito.
> en: Sometimes I come here only to watch someone who has stopped fighting. It is beautiful.

> pt: O vento aqui não vem de lugar nenhum. Não precisa.
> en: The wind here comes from nowhere. It does not need to.

> pt: Lá embaixo, alguém já esqueceu o som da tua voz.
> pt: Isso não é triste. É assim que o mundo cicatriza.
> en: Down there, someone has already forgotten the sound of your voice.
> en: That is not sad. It is how the world heals.

> pt: Sabes por que a ilha é pequena?
> pt: Não é mesquinhez minha. É que um espírito em paz não precisa de mais do que isto.
> pt: Os que querem mais... esses eu encontro na torre.
> en: Do you know why the island is small?
> en: It is not stinginess of mine. A spirit at peace does not need more than this.
> en: The ones who want more... those I find in the tower.

> pt: Dorme, se quiseres. Aqui o sono não leva a lugar nenhum.
> en: Sleep, if you like. Here sleep leads nowhere.

> pt: Hm.
> en: Hm.

## `kern_leave` — tomar o caminho da ilha para a torre

> pt: Já? A grama nem se acostumou com teus pés.
> en: Already? The grass has not even grown used to your feet.

> pt: O caminho é longo. Espero que te canses antes de chegar.
> en: The road is long. I hope you tire before you arrive.

> pt: Vai, então. Ela vai estar aqui quando voltares. Ela sempre está.
> en: Go, then. It will be here when you come back. It always is.

## `room` — entrar numa sala nova

> pt: Mais uma sala. Achavas que seria a última?
> en: Another room. Did you think it would be the last?

> pt: Este lugar não existia até tu chegares. Eu o fiz pensando em ti.
> en: This place did not exist until you arrived. I made it with you in mind.

> pt: Olha bem. Tu já estiveste aqui. Ou ainda vais estar.
> en: Look closely. You have been here before. Or you will be.

> pt: Outros pararam nesta sala. Alguns ainda estão.
> en: Others stopped in this room. Some still are.

> pt: Nenhuma janela desta torre dá para fora. Todas dão para dentro.
> en: No window in this tower looks out. They all look in.

> pt: Entra.
> en: Come in.

## `stairs` — na escada sem fim

> pt: Sobe.
> en: Climb.

> pt: Este degrau é igual ao último. O próximo também será.
> en: This step is the same as the last. So will the next one be.

> pt: Tu contas os degraus? Eu contei, uma vez. Parei quando percebi que eles me contavam de volta.
> en: Do you count the steps? I did, once. I stopped when I noticed they were counting me back.

> pt: No mundo desperto há uma represa em cada cabeça.
> pt: Ela segura o que tu sonhas, para que o mundo não se desfaça cada vez que alguém deseja alguma coisa.
> pt: Tu morreste, e a tua rachou. Agora queres voltar assim, sem ela.
> pt: Entendes o que me pedes? Não é a tua vida. É o mundo de todos os outros.
> en: In the waking world there is a dam in every head.
> en: It holds back what you dream, so the world does not come apart every time someone wants something.
> en: You died, and yours cracked. Now you want to go back like that, without it.
> en: Do you understand what you are asking of me? It is not your life. It is everyone else's world.

> pt: Mais alto não é mais perto.
> en: Higher is not closer.

> pt: Teus passos ecoam. Não há mais ninguém aqui para ouvir. Só eu.
> en: Your steps echo. There is no one else here to hear them. Only me.

## `descent` — começar a descer a escada da sala sem saída para cima

> pt: Achavas que o caminho era sempre para cima?
> en: Did you think the way was always up?

> pt: Desce, então. A torre também sabe ir para baixo.
> en: Go down, then. The tower knows how to go down, too.

> pt: Repara como os teus passos ficam mais leves.
> pt: É o corpo a lembrar-se de que descer é natural.
> pt: Subir é que sempre foi a coisa estranha.
> en: Notice how your steps grow lighter.
> en: It is the body remembering that going down is natural.
> en: Climbing was always the strange thing.

> pt: Cada degrau para baixo apaga um para cima. Conta, se quiseres.
> en: Every step down erases one up. Count them, if you like.

## `memory` — chegar à memória (a cópia do lugar onde o mago dorme)

> pt: Lembras disto? Eu lembro melhor do que tu.
> en: Do you remember this? I remember it better than you do.

> pt: Tua casa. Ou o que sobrou dela na tua cabeça.
> en: Your home. Or what is left of it in your head.

> pt: Os mortos voltam sempre aos mesmos lugares.
> pt: Não porque querem. Porque não sabem ir a outro.
> pt: Olha bem, {name}. Uma destas coisas não é como a deixaste.
> en: The dead always go back to the same places.
> en: Not because they want to. Because they know no other.
> en: Look closely, {name}. One of these things is not as you left it.

> pt: Ninguém te espera aqui. Nunca esperou.
> en: No one is waiting for you here. No one ever was.

## `memory_wrong` — golpear a coisa errada na memória

> pt: Não era isso.
> en: That was not it.

> pt: Tens certeza de que moravas aqui?
> en: Are you sure you lived here?

> pt: A casa tem paciência. Tu não.
> en: The house is patient. You are not.

> pt: Cada vez que erras, a casa esquece um pouco de ti.
> pt: Daqui a pouco não vai ter mais nada para esquecer.
> en: Every time you are wrong, the house forgets a little of you.
> en: Soon there will be nothing left to forget.

## `mirror` — chegar ao espelho

> pt: Olha para ele. É assim que eu te vejo.
> en: Look at him. That is how I see you.

> pt: Ele não tem pressa. Ele só chega depois.
> en: He is in no hurry. He only arrives later.

> pt: Do outro lado do espelho estás tu, um pouco atrasado.
> pt: Ele não sabe que morreste. Ainda está a viver o momento de antes.
> pt: Por quanto tempo achas que consegues ficar à frente dele?
> en: On the other side of the mirror is you, a little late.
> en: He does not know you died. He is still living the moment before.
> en: How long do you think you can stay ahead of him?

## `mirror_caught` — o reflexo alcança o mago

> pt: Ele alcançou-te. Ele sempre alcança.
> en: He caught you. He always does.

> pt: Não se foge do próprio passo.
> en: No one outruns their own step.

> pt: Cada vez mais perto. Um dia ele chega antes de ti.
> en: Closer every time. One day he will arrive before you.

## `speech` — chegar à fala (o braseiro e as palavras em latim)

> pt: Lê devagar. As palavras mortas não fogem.
> en: Read slowly. Dead words do not run away.

> pt: A língua antiga não tem poder nenhum.
> pt: O poder é teu. Ela só te assusta o bastante para o deixares sair.
> en: The old tongue has no power at all.
> en: The power is yours. It only frightens you enough to let it out.

> pt: Antes de haver palavras, havia o fogo.
> pt: Os primeiros a falar falavam com ele.
> pt: Tu esqueceste como. Eu não.
> en: Before there were words, there was fire.
> en: The first ones to speak spoke to it.
> en: You have forgotten how. I have not.

> pt: Emprestei-te o que precisas. Não te habitues.
> en: I have lent you what you need. Do not grow used to it.

## `still` — ficar parado por muito tempo

> pt: Cansado? Eu também. Há tanto tempo.
> en: Tired? So am I. For so long.

> pt: Parar não é desistir. Mas é parecido. Experimenta mais um pouco.
> en: Stopping is not giving up. But it is close. Try it a little longer.

> pt: Isso. Fica. É bem mais fácil do que parece.
> en: That's it. Stay. It is much easier than it looks.

> pt: Tu paraste, e eu senti o alívio no teu peito.
> pt: Não finjas que não sentiste também.
> pt: Esse alívio é a coisa mais verdadeira que te aconteceu desde que chegaste.
> en: You stopped, and I felt the relief in your chest.
> en: Do not pretend you did not feel it too.
> en: That relief is the truest thing that has happened to you since you arrived.

## `door` — perto da porta de descanso

> pt: Ela está aberta. Sempre esteve.
> en: It is open. It always was.

> pt: Do outro lado, a grama está molhada de orvalho. Tu lembras do cheiro?
> en: On the other side, the grass is wet with dew. Do you remember the smell?

> pt: Não é uma armadilha. Juro pelo que me resta de nome.
> en: It is not a trap. I swear by what is left of my name.

> pt: Basta um passo, {name}. Um passo, e acabou.
> en: One step, {name}. One step, and it is over.

> pt: Eu não sou teu inimigo.
> pt: Sou o que fica entre o teu desejo e o fim do teu mundo.
> pt: Essa porta não é uma derrota. É a única coisa nesta torre que eu fiz com carinho.
> en: I am not your enemy.
> en: I am what stands between your wish and the end of your world.
> en: That door is not a defeat. It is the only thing in this tower I made with care.

## `hurt` — com pouca vida

> pt: Dói, não dói? Lá embaixo doeria mais.
> en: It hurts, doesn't it? Down there it would hurt more.

> pt: Teu corpo de vis está se desfazendo. Não lutes contra isso.
> en: Your body of vis is coming apart. Do not fight it.

> pt: Se caíres aqui, cais de verdade. Pensa em quem ficaria.
> en: If you fall here, you fall for real. Think of who would be left.

## `idle` — devaneios, de tempos em tempos, na torre

> pt: Shh.
> en: Shh.

> pt: Eu vejo tudo o que tu poderias ter sido. Está tudo nas janelas.
> en: I see everything you could have been. It is all in the windows.

> pt: Achas que és o primeiro a pensar que é diferente?
> en: Do you think you are the first to believe you are different?

> pt: Eu não tenho pressa. Tu é que tens.
> en: I am in no hurry. You are.

> pt: Houve uma mulher, há muito tempo, que subiu até onde a névoa engole a pedra.
> pt: Ela falava sozinha, para não ouvir a minha voz.
> pt: Um dia ela parou de falar. Não sei se foi coragem ou cansaço.
> pt: Ela ainda está lá em cima. Às vezes eu a visito.
> en: There was a woman, long ago, who climbed to where the fog swallows the stone.
> en: She talked to herself, so as not to hear my voice.
> en: One day she stopped talking. I do not know if it was courage or weariness.
> en: She is still up there. Sometimes I visit her.

> pt: O fogo de Lig'dar não queima. Ele lembra.
> en: The fire of Lig'dar does not burn. It remembers.

> pt: Quando eras criança, tinhas medo do escuro.
> pt: Não do escuro em si, mas do que podia estar nele.
> pt: Aqui é igual. A torre está vazia. Quem a enche és tu.
> en: When you were a child, you were afraid of the dark.
> en: Not of the dark itself, but of what could be in it.
> en: It is the same here. The tower is empty. You are the one who fills it.

> pt: Teu nome está ficando mais leve. Percebeste?
> en: Your name is growing lighter. Have you noticed?

> pt: Por que voltar? Diz-me uma razão, só uma, e eu te digo se ela vale um mundo.
> en: Why go back? Give me one reason, just one, and I will tell you if it is worth a world.

> pt: Eu não minto. Não preciso.
> en: I do not lie. I have no need to.

> pt: Antes de haver torre, havia só o vulcão. E antes do vulcão, só o pensamento de um.
> pt: Eu o pensei tão forte que ele aconteceu.
> pt: É assim que tudo começa em Nahvá. E é por isso que nada do que nasce aqui deveria ir para lá.
> pt: Lá as coisas têm peso. Aqui têm vontade. Tu queres levar a vontade para onde tudo pesa.
> en: Before there was a tower, there was only the volcano. And before the volcano, only the thought of one.
> en: I thought it so hard that it happened.
> en: That is how everything begins in Nahvá. And that is why nothing born here should go there.
> en: There, things have weight. Here, they have will. You want to carry will to where everything weighs.

> pt: Respira. Ah. Esqueci. Tu não precisas mais.
> en: Breathe. Ah. I forgot. You do not need to anymore.

> pt: Ouviste isso? Não. Claro que não.
> en: Did you hear that? No. Of course not.

> pt: Cada porta que flutua lá fora é alguém que desistiu de abri-la.
> en: Every door floating out there is someone who gave up on opening it.

> pt: Eu poderia te parar. Não paro. Quero que a escolha seja tua.
> en: I could stop you. I do not. I want the choice to be yours.

> pt: Estás tão longe de casa. E a casa está tão perto.
> en: You are so far from home. And home is so close.

> pt: Os vivos rezam para que os mortos descansem.
> pt: Ninguém nunca reza para que os mortos voltem. Já pensaste por quê?
> en: The living pray for the dead to rest.
> en: No one ever prays for the dead to return. Have you wondered why?

> pt: ...
> en: ...

> pt: Eu tive um nome antes deste. Deixei-o numa destas salas. Se o achares, não me devolvas.
> en: I had a name before this one. I left it in one of these rooms. If you find it, do not give it back.

## `summit` — chegar ao topo

> pt: Então chegaste.
> pt: Poucos chegam. Menos ainda chegam inteiros.
> pt: A fenda está ali. Atravessá-la é voltar com tudo o que Nahvá te deu, e que o mundo desperto não sabe segurar.
> pt: Não vou te impedir. Nunca impedi ninguém.
> pt: Só te peço que olhes para trás uma vez, antes. A porta ainda está aberta.
> en: So you have arrived.
> en: Few arrive. Fewer still arrive whole.
> en: The rift is there. Going through it is going back with everything Nahvá gave you, which the waking world does not know how to hold.
> en: I will not stop you. I have never stopped anyone.
> en: I only ask that you look back once, before. The door is still open.

## `rest` — atravessar a porta de descanso

> pt: Obrigado.
> pt: Dorme. Eu cuido do resto.
> en: Thank you.
> en: Sleep. I will see to the rest.

> pt: Bem-vindo à paz, {name}. Ela não vai acabar.
> en: Welcome to peace, {name}. It will not end.

> pt: Isso. Isso. Agora acabou.
> en: There. There. Now it is over.

## `fall` — morrer na torre

> pt: Ah. Não era isso que eu queria para ti.
> en: Ah. This is not what I wanted for you.

> pt: Eu te ofereci a ilha. Lembra disso, onde quer que estejas indo.
> en: I offered you the island. Remember that, wherever you are going.

> pt: Caíste. A torre não chora. Eu, às vezes, sim.
> en: You fell. The tower does not weep. I sometimes do.

## `won` — atravessar a fenda

> pt: Vai, então.
> pt: E quando o mundo rachar sob teus pés, lembra que eu avisei.
> en: Go, then.
> en: And when the world cracks under your feet, remember that I warned you.

> pt: Tu conseguiste.
> pt: Eu devia estar com raiva. Estou só cansado.
> pt: Leva a represa quebrada contigo. Usa-a com mais cuidado do que eu usaria.
> en: You did it.
> en: I should be angry. I am only tired.
> en: Take the broken dam with you. Use it more carefully than I would.
