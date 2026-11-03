# Changelog

## 0.5.2 — catálogo compacto e rolagem estável
- Cada sinal do catálogo ocupa uma linha com nome e valor; confiança, fonte, escopo, nota e idade permanecem no SDK/JSONL, mas não poluem a tela de teste.
- Atualizações preservam a posição vertical do `ScrollView`. Quando nenhum valor mudou, o texto nem sequer é substituído.
- A interface continua distinguindo zero, indisponível e não recebido; o decoder, os graus de confiança e os mapeamentos não foram alterados.

## 0.5.1 — catálogo ao vivo no Probe
- Probe integrado a `VehicleDecoder`: a captura passa a registrar a lista `decodedSignals` com valor, raw, unidade, fonte, confiança, nota e horário.
- Nova tela de catálogo completo, atualizada durante a leitura, distinguindo zero, indisponível recebido, nunca recebido e valor histórico após desconexão.
- Catálogo estático reutilizável em `VehicleDecoder.catalog()` e `PsaPhysicalCanExperimental.catalog()`; os 51 sinais de CAN físico aparecem como indisponíveis no Probe Binder, sem fingir telemetria.
- Filtro opcional para mostrar somente sinais recebidos. Probe 0.5.1 mantém leitura passiva e exportação JSONL.

## 0.5.0 — API ampliada experimental
- VehicleDecoder/VehicleSignal: interpretação com origem, timestamp, unidade, confiança e disponibilidade.
- Velocidade raw/10 exposta explicitamente como estimativa; GPSMain101 separado e assinado pelo transporteAndroid.
- HVAC, portas, consumo, campos de viagem raw e desconhecidos preservados; cache de unidades/offsets reiniciado ao trocar perfil.
- Mapeamento externoAEE2010/0x128:51campos experimentais numa entradaCANfísico separada, não confundida comHiWorld.
- Fontes online fixadas por commit e catálogo de limites. Testes de isolamento de perfis, escala estimada, sentinelas e timestamps. Nenhum novoAPK doProbe.

## Pesquisa — 2026-11-02
- Relato aproximado do proprietário (50–80km/h) compatível com hipótese velocidade raw/10; ainda sem calibração sincronizada ou alteração da API.
- Replay de sessão JSONL usando parserJava de produção, com teste de fragmentação/separação de sessões.
- Análise de captura urbana e candidato à velocidade0x1A/bytes7–8, ainda sem unidade validada.
- API e Probe permanecem inalterados.

## 0.4.0 — core / baseline do repositório
- Separação core, transporte Android e Probe0.3 existente.
- Parser incremental de frames5AA5/AA55 com comprimento/checksum e descarte de ruído.
- RPM e direção raw para os dois perfis observados, com confirmação humana documentada.
- Testes portáveis e roteiro de continuidade. API ainda experimental.
- Não houve lançamento de novo APK; Probe preservado0.3.
