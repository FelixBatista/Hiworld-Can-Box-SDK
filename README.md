# Teyes CAN SDK — Peugeot 508

Biblioteca para criar aplicativos que leem dados do carro através dos serviços Teyes/SYU. Projeto principal: **SDK e decodificação**, não launcher. Veículo de referência: Peugeot508SW2014, sem amplificador; firmware MS25.1202/2125120210.

## Estrutura e decisão

- `core/`: Java8 sem Android, enquadramento5AA5/AA55, checksum e decodificadores. Pode ser testado no laptop.
- `android/`: transporte Binder `SyuCanClient`, para reutilização em apps Android ou módulo Android de um app Flutter.
- `probe/`: app diagnóstico0.3 preservado, usado para captura/exportação. Não incorpora automaticamente o novo parser0.4.
- `tools/`: análise de JSONL e testes; `tests/`: regressões dos decodificadores.
- `docs/`: evidências, decisão arquitetural, estado e próximos passos.

**Integração atual:** incluir as fontes de `core/src` e `android/src` no módulo Android consumidor. Alternativamente compile o JAR core e use-o junto de SyuCanClient.java. Não é preciso executar o Probe junto do seu app. Ainda não há publicação Maven/AAR, serviço central ou API de comandos.

```java
final CanboxStream stream = new CanboxStream();
final SyuCanClient reader = new SyuCanClient(context, new SyuCanClient.Listener() {
    public void onStatus(String message) { /* registrar e mostrar desconexões */ }
    public void onEvent(SyuCanClient.Event e) {
        if (e.module == 7 && e.code == 1019 && e.profile != null) {
            for (byte[] packet : stream.feed(e.ints)) {
                PsaTelemetry sample = PsaTelemetry.decode(e.profile, packet);
                if (sample != null) {
                    // sample.rpm: RPM confirmado pelo proprietário.
                    // sample.steeringRaw: direção confirmada; NÃO são graus calibrados.
                    // Use e.timeMs/e.elapsedNs como horário de recepção do último fragmento.
                }
            }
        }
    }
}, true);
reader.connect(); // main thread; close() ao terminar
```

Inclua `<queries><package android:name="com.syu.ms"/></queries>` no manifest. Não copie sharedUserId OEM. Listener roda em worker; encaminhe UI à main thread. Crie cliente/parser/decoder novos a cada conexão e troque/reset o parser quando o perfil mudar. Não misture streams de dispositivos/conexões. Para Flutter, adapte o listener para EventChannel; esse adaptador ainda não foi implementado.

## Validação atual

RPM e movimento da direção foram confirmados pelo proprietário em31/10/2026. O decoder bruto restringe-se aos perfis observados3014908(2010–2018High) e3080444(2019–2024), tipo0x1A. Não generalizar a outros CAN boxes. Origem das amostras e limites constam em docs/STATUS.md.
Iluminação geral foi observada; farol alto e velocidadeCAN seguem desconhecidos. GPSMain0/101 é uma fonte diferente. HVAC/portas/trips têm caminhos estáticos identificados; nem todos foram expostos na API. Ausência de evento não é valor zero. Apps devem marcar dados antigos/desconexão e mostrar origem/unidade, sem misturar GPS eCAN.

## Construir e testar

JDK9+ (gera bytecodeJava8) ePython3, sem pacotesPython extras:

```text
python tools/check.py
```

Gera `dist/teyes-can-core-0.4.0.jar`. Testa separação de callbacks, perfis, temperaturas, fragmentação em todos os pontos, concatenação, checksum inválido, sentinela inicial e direção negativa. O pacote completo do Probe pode ser reconstruído no Linux comJDK21,ANDROID_SDK_ROOT contendo platform35/build-tools35.0.0: `bash build-android.sh`. Esse script continua produzindo Probe0.3; chaves ficam fora do repo em HOME/.local/share/teyes-probe-signing. Use a mesma chave anterior para preservar upgrades de instalações existentes.

Capturas completas, APKs OEM, firmware, fontes decompiladas e credenciais não pertencem aoGit. Só um pacote mínimo observado, sem identificação pessoal, integra o teste. Relatórios históricos preservam hipóteses da época; **docs/STATUS.md é o estado atual**, incluindo validação posterior do usuário. Repo privado; nenhuma licença de redistribuição do material OEM é concedida.
