# Catálogo 0.5 — escopo e incerteza

## Contrato

VehicleDecoder.acceptBinder(module,code,ints,timeMs) retorna uma lista de VehicleSignal. Instância por conexão; reset ao reconectar. Perfil1000 limpa caches e frames incompletos. Campos não recebidos não aparecem; value=null significa indisponível. Metadata242/172 pode reemitir temperatura/consumo já recebido: mantém timestamp da medida original, não simula dado novo. Cache de metadados vale até reset/troca de perfil. receivedAtMs é horário da recepção, não timestamp físico do sensor; callback inicial pode vir do cache OEM. isStale(now,limit) permite ao app definir expiração.

`VehicleDecoder.catalog()` descreve todos os sinais Binder conhecidos mesmo antes de serem recebidos. `PsaPhysicalCanExperimental.catalog()` descreve separadamente os 51 sinais do CAN físico externo. `SignalDefinition.observableByBinderProbe=false` impede que uma interface apresente esses sinais externos como telemetria disponível no Probe. `psa_field_N` é um modelo para códigos ainda não nomeados; ao receber um código real, o decoder preserva o nome com seu número.

Confidence:
- OWNER_CONFIRMED: RPM e resposta da direção confirmados pelo usuário; direção continua raw.
- OEM_STATIC: interpretação reconstruída do código fornecido, sem garantir suporte/calibração no veículo.
- ESTIMATED: hipótese oferecida deliberadamente; usar rótulo visível de estimativa.
- EXTERNAL_UNVERIFIED: mapeamento público de outra implementação, sem validação neste carro.
- RAW_UNKNOWN: sem interpretação física segura.

## Transporte disponível: Binder Teyes

| Campos | Fonte | Unidade/confiança/limite |
|---|---|---|
|rpm,steering_raw|5AA5tipo1A,13/14 e10/11BE|rpm confirmado; direção signed16raw, sem graus|
|speed_kmh_estimated|tipo1A,7/8BE dividido10|ESTIMATED; faixa lembrada pelo dono compatível, não calibração sincronizada|
|box1a_byte4,5,6,9,12,15|bytes não atribuídos|RAW_UNKNOWN, sem nomes como marcha ou farol inventados|
|gps_speed_kmh|Main0/101|km/h,OEM_STATIC; não velocidadeCAN nem prova de fixGPS|
|illumination|Main0/4|estado raw, não alto/baixo/DRL|
|key_candidate_0_60,10_6,7_1053|Main/Steer/CAN|ESTIMATED, podem depender de modo aprendizagem|
|profile|CAN7/1000|catálogo de perfis, não identificação física do box|
|oem_speed_callback_candidate,oem_rpm_callback_candidate|CAN7/1031,1032|ESTIMATED, raw; não recebidos nos testes, escala não presumida|
|hood,front_left/right_door,rear_left/right_door,trunk|CAN7/0–5|estadosOEM raw, sem normalizar polaridade não verificada|
|hvac_power,ac,recirculation,auto|7/10,11,12,13|OEM_STATIC, estadosraw|
|rear_defrost,front_defrost|7/16,65|OEM_STATIC|
|left_air_distribution18/19/20,right_air_distribution22/23/24|7/18–20,22–24|bitsOEM, nomes físicos da distribuição não impostos|
|left_fan_raw,hvac_mode_raw|7/21,51|nível/modoOEM; não vazão nem porcentagem|
|hvac_max_ac_candidate,hvac_aqs,hvac_dual|7/53,54,55|53estimado; demaisOEM_STATIC|
|left/right_temperature_setting|7/27,28+offset242|graus de exibiçãoOEM; não assumir°C;−1NO,−2LOW,−3HI; offset ausente=>null|
|consumption_current,trip1,trip2|7/98,100,103+unidade172|raw/10;172=2km/L,3mpg,demaisL/100km conformeUI;65535=>null; unidade ausente=>null|
|distance_display99_raw,trip1_distance_display_raw,trip1_secondary_display_raw,trip2_secondary_display_raw,trip2_secondary2_display_raw,trip2_distance_display_raw|7/99,101,102,104,105,106|RAW_UNKNOWN: representação daUI documentada, escala/função exata não calibradas|
|psa107_unknown,psa192_unknown|7/107,192|não adotar nomes de constantes OEM conflitantes|
|panel_brightness_candidate|7/197|ESTIMATED; candidato brilho, não farol alto|
|consumption_unit_code,temperature_offset|7/172,242|metadadosOEM|
|psa_field_N|demais7/0–257|RAW_UNKNOWN; preservação, não mapeamento semântico|

MapeamentosBinderPSA somente protocolo252; frames1A somente perfis3014908/3080444. Outras variantes não herdam a interpretação1A. API antiga Peugeot508Decoder permanece disponível, mas VehicleDecoder é a interface recomendada. SyuCanClient agora assinaMain101 também. Não envia comandos.

## Entrada separada: CAN físico experimental

PsaPhysicalCanExperimental.decode("AEE2010",0x128,payload8,timeMs). Exige exatamente8bytes e arquitetura explícita. NÃO passar1019nem tipo0x1A aqui: não são arbitrationIDs. Não há hardware/transportador CAN físico implementado.

51campos derivados de definições públicas doPSACANBridge: DRL, setas, neblinas, alto/baixo/posição; códigos de marcha, seleção/modo/recomendação do câmbio; freio de estacionamento/inibição, indicação de pedal de freio; airbag passageiro, trava infantil, avisosSTOP/service; suspensão,ESP ativo/desativado; avisos de portas, pré-aquecimento diesel; cintos dianteiros/traseiros; combustível baixo,SCR; clutch/engine/ready e demais flags explicitamente nomeadas no fonte. Todos EXTERNAL_UNVERIFIED e raw, incluindo códigos multibit. Indicador de painel não é necessariamente estado físico de atuador (por exemplo foot_on_brake_indicator e stop_warning).

Farol alto: byte0bit5; baixo:byte0bit6. Isso é fato do mapeamento externoAEE2010, NÃO prova que esses bits existem no fluxoHiWorld atual. Detalhes de nomes/offsets em PsaPhysicalCanExperimental.java e fonte fixada em ONLINE-SOURCES.md. Os testes verificam implementação, não validam o veículo.
