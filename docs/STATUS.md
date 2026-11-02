# Estado e continuidade — 31/10/2026

Atualização02/11: captura urbana analisada em [DRIVE-20261102.md](DRIVE-20261102.md). RPM1216–1704, direção raw−18..16. Novo candidato à velocidade: tipo0x1A bytes7/8BE, raw541–760, zero nos ensaios anteriores. Sem escala validada; ainda fora da API de velocidade. Replay agora usa diretamente o parserJava. Nove falhas de checksum observadas, não confundidas com perda de fila do app.

## Decisão

Retorno do proprietário em02/11: velocidade lembrada de aproximadamente50–80km/h, compatível com raw/10=54,1–76,0km/h. Escala provável, ainda não validada por referência simultânea. Sem informação adicional sobre paradas. API permanece inalterada.

SDK incorporável: core Java puro + transporte Android + Probe separado. Um serviço central instalado na central fica adiado até haver necessidade demonstrada de compartilhar conexão entre vários apps. Nada de MQTT/servidor/rede obrigatório. Launcher é apenas consumidor possível e está fora do escopo atual.

## Sinais

| Sinal | Estado | Fonte |
|---|---|---|
| RPM | Confirmado pelo proprietário, 31/10 |5AA5tipo1A bytes13/14 BE; perfis252/46 e47|
| Direção | Movimento confirmado pelo proprietário; graus e sentido físico não calibrados |tipo1A bytes10/11 signed16BE|
| Iluminação | Alternância observada |Main0/4; não equivale a farol alto|
| HVAC/portas | Parser/UI OEM rastreados |CAN7 campos0–5,10–96,242|
| Consumo/trips | UI e parser OEM rastreados |CAN7 campos98–106,172|
| VelocidadeGPS | Publicação estática confirmada |Main0/101|
| VelocidadeCAN / farol alto / teclas | Não decodificados/validados completamente |Pesquisa pendente|

Os dois JSONLs originais são cumulativos. Nunca contar o segundo como sessão inteiramente nova. Sessões novas:perfil3080444=722callbacks/41,3s;3014908=700callbacks/35,8s. Sem descartes reportados. O proprietário confirmou posteriormente os camposRPM/direção: isso atualiza os relatórios históricos que ainda os chamam candidatos.

## Limites técnicos

1019 é fluxo MCU/CANbox, não sniffer de todos os IDsCAN. Parser aceita cabeçalhos5AA5/AA55; decoderPSA só5AA5. Máximo260bytes por pacote. Frames parciais aguardam próximos bytes; desconexão/perfil novo exige reset. Checksum valida enquadramento, não garante todos os dados físicos. Não há precisão/latência automotiva certificada. Assinar1019 ativa flag de cópia OEM que pode persistir após unregister. Sem comandos de atuação.

## Próximos passos ordenados

1. Integrar core0.4 ao Probe, com sessão nova explícita, timestamps e aviso de dados antigos; manter arquivo antigo até exportação.
2. Adicionar sinais tipados HVAC/portas/trips à API e seus testes baseados nos caminhos OEM já documentados.
3. Reproduzir localmente capturas com parser de fluxo, preservar amostras desconhecidas e mapear velocidadeCAN/farol alto; não converter ausência para zero.
4. Calibrar direção em graus/sentido e registrar box/firmware/perfil por teste.
5. Empacotar AndroidAAR e exemplo Kotlin/Flutter quando a API estabilizar. Criar releases versionadas, changelog e matriz de compatibilidade.

## Fluxo de trabalho

Cada descoberta deve registrar origem (captura/código OEM/validação humana), perfil e versão, offsets/unidades/sentinelas e limitações. Implementar no core, adicionar teste com fixture mínima, validar, depois atualizar Probe/exemplos. Novos apps importam versão fixa do SDK e não duplicam decodificadores. Mudanças de unidade/nome exigem changelog e revisão de consumidores. Antes de ampliar perfis, obter evidência específica.

Arquivos originais permanecem no laboratório existente; documentos referenciam caminhos históricos para rastreabilidade, mas builds não dependem deles. Firmware dezembro hash4a7a8b91fcecf95128a9295a2c8b96d6d0c991accc4519177cd90a6bb2543254. APKMShash94a96a51a56367e18817fe66e76a84424116cb97861bf553c1938fcde2c4d969. Sem necessidade de repetir decompilação.
