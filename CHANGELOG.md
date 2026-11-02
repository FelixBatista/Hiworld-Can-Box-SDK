# Changelog

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
