# Teyes CAN SDK â€” Peugeot 508

Biblioteca para criar aplicativos que leem dados do carro atravÃ©s dos serviÃ§os Teyes/SYU. Projeto principal: **SDK e decodificaÃ§Ã£o**, nÃ£o launcher. VeÃ­culo de referÃªncia: Peugeot508SW2014, sem amplificador; firmware MS25.1202/2125120210.

## API ampliada 0.5

Use `VehicleDecoder`: ele expÃµe candidatos com `confidence`, `source`, `unit`, `receivedAtMs` e `note`. Consulte o [catÃ¡logo completo](docs/SIGNAL-CATALOG.md) e as [fontes online](docs/ONLINE-SOURCES.md). `speed_kmh_estimated` usa raw/10 como hipÃ³tese: **nÃ£o Ã© velocidade calibrada**. Farol alto/baixo estÃ£o no decoder separado de CAN fÃ­sico experimental, ainda inacessÃ­veis pela conexÃ£o Teyes.

```java
final VehicleDecoder decoder = new VehicleDecoder();
// Dentro de SyuCanClient.Listener.onEvent:
for (VehicleSignal s : decoder.acceptBinder(e.module, e.code, e.ints, e.timeMs)) {
    // s.value pode ser null. Preserve confidence/source e rotule as estimativas.
    // s.isStale(nowMs, 2000) usa limite de validade escolhido pelo app.
}
// Recrie ou reset o decoder ao reconectar.
```

Core0.5 reÃºne parser e decodificaÃ§Ã£o nesta API, mantendo transporte separado. Probe0.3 ainda nÃ£o foi atualizado. Os exemplos de baixo nÃ­vel abaixo continuam disponÃ­veis.

## Estrutura e decisÃ£o

- `core/`: Java8 sem Android, enquadramento5AA5/AA55, checksum e decodificadores. Pode ser testado no laptop.
- `android/`: transporte Binder `SyuCanClient`, para reutilizaÃ§Ã£o em apps Android ou mÃ³dulo Android de um app Flutter.
- `probe/`: app diagnÃ³stico0.3 preservado, usado para captura/exportaÃ§Ã£o. NÃ£o incorpora automaticamente o novo parser0.4.
- `tools/`: anÃ¡lise de JSONL e testes; `tests/`: regressÃµes dos decodificadores.
- `docs/`: evidÃªncias, decisÃ£o arquitetural, estado e prÃ³ximos passos.

**IntegraÃ§Ã£o atual:** incluir as fontes de `core/src` e `android/src` no mÃ³dulo Android consumidor. Alternativamente compile o JAR core e use-o junto de SyuCanClient.java. NÃ£o Ã© preciso executar o Probe junto do seu app. Ainda nÃ£o hÃ¡ publicaÃ§Ã£o Maven/AAR, serviÃ§o central ou API de comandos.

```java
final CanboxStream stream = new CanboxStream();
final SyuCanClient reader = new SyuCanClient(context, new SyuCanClient.Listener() {
    public void onStatus(String message) { /* registrar e mostrar desconexÃµes */ }
    public void onEvent(SyuCanClient.Event e) {
        if (e.module == 7 && e.code == 1019 && e.profile != null) {
            for (byte[] packet : stream.feed(e.ints)) {
                PsaTelemetry sample = PsaTelemetry.decode(e.profile, packet);
                if (sample != null) {
                    // sample.rpm: RPM confirmado pelo proprietÃ¡rio.
                    // sample.steeringRaw: direÃ§Ã£o confirmada; NÃƒO sÃ£o graus calibrados.
                    // Use e.timeMs/e.elapsedNs como horÃ¡rio de recepÃ§Ã£o do Ãºltimo fragmento.
                }
            }
        }
    }
}, true);
reader.connect(); // main thread; close() ao terminar
```

Inclua `<queries><package android:name="com.syu.ms"/></queries>` no manifest. NÃ£o copie sharedUserId OEM. Listener roda em worker; encaminhe UI Ã  main thread. Crie cliente/parser/decoder novos a cada conexÃ£o e troque/reset o parser quando o perfil mudar. NÃ£o misture streams de dispositivos/conexÃµes. Para Flutter, adapte o listener para EventChannel; esse adaptador ainda nÃ£o foi implementado.

## ValidaÃ§Ã£o atual

RPM e movimento da direÃ§Ã£o foram confirmados pelo proprietÃ¡rio em31/10/2026. O decoder bruto restringe-se aos perfis observados3014908(2010â€“2018High) e3080444(2019â€“2024), tipo0x1A. NÃ£o generalizar a outros CAN boxes. Origem das amostras e limites constam em docs/STATUS.md.
IluminaÃ§Ã£o geral foi observada; velocidadeCAN estÃ¡ exposta como estimativa. Farol alto no fluxoTeyes segue desconhecido. GPSMain0/101 Ã© fonte separada. HVAC/portas/trips estÃ£o expostos com graus de confianÃ§a e escalas explÃ­citas. AusÃªncia de evento nÃ£o Ã© valor zero. Apps devem marcar dados antigos/desconexÃ£o e mostrar origem/unidade, sem misturar GPS eCAN.

## Construir e testar

JDK9+ (gera bytecodeJava8) ePython3, sem pacotesPython extras:

```text
python tools/check.py
```

Gera `dist/teyes-can-core-0.5.0.jar`. Testa separaÃ§Ã£o de callbacks, perfis, temperaturas, fragmentaÃ§Ã£o em todos os pontos, concatenaÃ§Ã£o, checksum invÃ¡lido, sentinela inicial e direÃ§Ã£o negativa. O pacote completo do Probe pode ser reconstruÃ­do no Linux comJDK21,ANDROID_SDK_ROOT contendo platform35/build-tools35.0.0: `bash build-android.sh`. Esse script continua produzindo Probe0.3; chaves ficam fora do repo em HOME/.local/share/teyes-probe-signing. Use a mesma chave anterior para preservar upgrades de instalaÃ§Ãµes existentes.

Capturas completas, APKs OEM, firmware, fontes decompiladas e credenciais nÃ£o pertencem aoGit. SÃ³ um pacote mÃ­nimo observado, sem identificaÃ§Ã£o pessoal, integra o teste. RelatÃ³rios histÃ³ricos preservam hipÃ³teses da Ã©poca; **docs/STATUS.md Ã© o estado atual**, incluindo validaÃ§Ã£o posterior do usuÃ¡rio. Repo privado; nenhuma licenÃ§a de redistribuiÃ§Ã£o do material OEM Ã© concedida.
