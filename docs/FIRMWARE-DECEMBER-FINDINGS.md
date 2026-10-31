# Investigação do firmware CC2 PLUS-EN 2025.12.2

## Identidade, execução e cobertura

ZIP fornecido, SHA2564a7a8b91fcecf95128a9295a2c8b96d6d0c991accc4519177cd90a6bb2543254, igual à cópia já armazenada no servidor. Originais preservados.
AllAppUpdate.bin é ZIP com membros criptografados. Credencial embutida no atualizador foi validada contra um membro do pacote sem executar o atualizador; credencial não incluída nesta entrega. Extração seletiva por nome fixo. 6315_1.zip inventariado (system/product/vendor/socko, boot e componentes de rádio); imagens de partição não foram expandidas nem montadas.
Análise offline no container existente, sem rede,2CPU/8GiB, nenhum APK OEM executado, nenhum comando enviado ao veículo. Extração/decompilação por Python/unzip/JADX CLI, não é Evidence produzido pelo REA. Não houve alteração de stack, serviço de produção ou configuração da central.

MS extraído:25.1202/2125120210, exatamente a versão informada nos logs; correspondência por versão não é comprovação de hash do APK instalado. SHA25694a96a51a56367e18817fe66e76a84424116cb97861bf553c1938fcde2c4d969.
Canbus:versionCode2125112518. cc2plus.apk é ru.teyes.update (atualizador), não launcher.
Decompilados MS,Canbus,US,CarUI,cc2plus,com.syu.launcher,Launcher3 e launchers4/6/8. MS terminou com31 erros eUS com2; CarUI/Launcher3 também retornaram3, logs retidos. Exit0 dos launchers protegidos não significa recuperação de sua lógica.

Raiz servidor: /srv/development/firmware-lab/workspace/teyes-508-research/firmware-dec. Caminhos abaixo relativos a decompiled/.

## Mapa de funcionalidades

| Funcionalidade | Fonte confirmada | Resultado |
|---|---|---|
| Overlay HVAC PSA252 | CAN7 códigos10–96 | Caminho de UI e parser confirmados |
| Portas/capô/porta-malas | CAN7 códigos0–5 | Callback252 configura DoorHelper |
| Consumo/trips | CAN7 códigos98–106,172 | Páginas PSA confirmadas; não confundir com velocidade atual |
| Velocidade GPS | Main0/101 | Publicação confirmada no MS dezembro |
| Velocidade CAN | CAN7/1031 | Não encontrada publicação no caminho252 inspecionado; nenhum callback nos testes |
| RPM/direção brutos | Mensagem5AA5 tipo0x1A | Candidatos de captura; unidades ainda não corroboradas por decoder OEM |
| Iluminação | Main0/4 | Estado geral observado; não distingue farol alto |
| Farol alto | Indefinido | Sem decoder específico confirmado |
| Velocímetro launcher4/6/8 | Lógica protegida | OrigemGPS/CAN ainda não estabelecida |

## Overlay ar-condicionado — confirmado dezembro

com.syu.canbus/sources/com/syu/module/canbus/Callback_0252_WC2_PSAALL.java:185–198 configura DoorHelper0–5, cria Air_0252_WC2_PSAALL e registra AirHelper.SHOW_AND_REFRESH em10–96. out():202+ remove os callbacks.
com.syu.canbus/sources/com/syu/ui/air/Air_0252_WC2_PSAALL.java:37–77 lê13/55/11/53/12/65/16/18/19/20/51/21;95AQS54;99–118temperaturas27/28 eoffset242. Temperatura=(raw+offset*10)/10;−1NO,−2LOW,−3HI. O overlay trabalha com os mesmos dados Binder disponíveis ao cliente independente.
com.syu.ms/sources/j0/h8.java:795–815 recebe tipo0x31. Primeiro byte:power10=bit6;53=bit5;42=bit4;auto13=bit3;dual55=bit2;AC11=bit0. Segundo:recirculação12=bit4,AQS54=bit3. Terceiro:desembaçador traseiro16=bit5,dianteiro65=bit4. Modos de distribuição18/19/20 e22/23/24 são calculados; ventilador21 em900,temperaturas27/28em905+. Não assumir todos os bits suportados pelo carro.

## Vehicle Info / informações PSA

PSAOilMilePage1Acti.java:42–44 registra98/99/172;56+ consumo decimal em98 com unidade172 (2km/l,3mpg,demaisL/100KM);78+distância99. Page2:65+consumo100,84+campo102 exibidoKM,98+distância101. Page3 usa103/105/106. Representações OEM, não rótulos inferidos dos nomes antigos de constantes. MS h8:1295–1313 popula98–106 nos tipos0x13/0x14/0x15.
PSAFuncLightActi trata configurações enviadas com cmd14/16/17/26. Sua existência não confirma estado atual do farol alto. Busca textual delimitada aos consumidores PSA por high-beam/远光 não encontrou caminho explícito.

## GPS e launcher

com.syu.ms/sources/n1/i.java:346–348 usa Location.getSpeed()*3.6 ->t0.d.u0. t0/d.java:3958–3962 armazena W1 e publica Main101. Isso éGPS, não velocidade CAN.
com.syu.launcher é seletor de launchers: MainActivity.java:58–66 identifica4/6/8;162+altera persist.launcher.packagename. Não é o consumidor do velocímetro.
Launchers4/6/8 resultam em R.java,kdp/AR e com/kiwivm/security/StubApplication. No4,StubApplication:128–133 carrega biblioteca nativa;167/170 attachBaseContext/onCreate nativos. Recurso car_speed existe, porém seu código consumidor não aparece no DEX recuperado. Não afirmar velocidadeGPS apenas por encontrar o publicador. Desbloquear esta trilha exige aprofundar carregador nativo/proteção ou observar o launcher efetivamente instalado em execução. Nenhum acessoADB/runtime à central foi estabelecido nesta sessão.

## Mensagens brutas e limitações

MS j0/ht.java:2228–2229 seleciona h8 para252. h8.V2:707 não trata tipo26 nem publica1031/1032 literalmente; ct.V2:60 é vazio. z/i.java:792–804 passa MCU0xE3 ao parser e pode copiar1019;809–824MCU0x10 copia1019 sem chamar h8. Callback1019 não carrega identificação explícita da rota externa.
k0/o.java:j:523+ trata fluxo5AA5/AA55, tamanho byte2+5, checksum(sum bytes2 até penúltimo−1)&255 em545–550/585–594. Tipo0xF0 extrai identificaçãoASCII e publica1051 em554–555/599–600. Corrobora o enquadramento inferido nas capturas; não fornece semântica de RPM/direção.
As capturas existentes têm candidatoRPM bytes13/14 big-endian857–2897 e819–4955, candidato direção signed16bytes10/11 −226..220 no segundo teste. Não promover candidatos a medidas calibradas. Não concluir que a troca de perfil habilitou esses sinais: tipo0x1A já existe em ambos.

## Próximo trabalho fundamentado

Reaproveitar campos HVAC/portas/trips comprovados; adicionarGPS como fonte explicitamenteGPS, não substituto silencioso deCAN. Implementar reconstrução de fluxo5AA5 com validação antes de decodificar0x1A. Manter farol alto/velocidadeCAN e unidades de direção/RPM como lacunas. Investigar carregador nativo dos launchers para determinar consumidor do velocímetro. Nenhuma nova versão do Probe foi feita neste passo.

Rollback: análise apenas cria firmware-dec e scripts de investigação; não há alteração operacional para reverter. Arquivos grandes permanecem no workspace limitado do laboratório, não em volumes de produção. Não apagar capturas nem aplicativos anteriores.
