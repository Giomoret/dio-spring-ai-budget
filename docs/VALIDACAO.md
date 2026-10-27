# Evidências de validação

Executado em **27/10/2026**, com JDK 25 e Gradle Wrapper 9.4.1.

## Build e testes

Comando: `gradlew.bat test bootJar --no-daemon --console=plain`.

Resultado após revisão: **BUILD SUCCESSFUL**, 26 testes, zero falhas e zero erros.

| Suíte | Testes | Escopo |
|---|---:|---|
| BudgetApiTest | 13 | HTTP real, Bean Validation, domínio, H2, períodos, categorias, saldo, consistência de validações e rejeição de enums numéricos |
| AssistantServiceTest | 5 | Sequência transcrição/chat/voz, rejeição de comandos inválidos e respostas de texto/áudio vazias |
| SpringAiGatewayTest | 1 | ChatClient e interfaces de áudio com modelos simulados |
| VoiceFlowTest | 7 | Upload multipart, adaptadores reais, ferramenta/banco, erros de credencial/indisponibilidade, falha de voz após gravação, áudio vazio, arquivo inválido e limite de tamanho |

Em VoiceFlowTest, um servidor HTTP local simula os endpoints do provedor. O primeiro retorno de chat solicita `createTransaction`; o Spring AI executa a ferramenta e consulta novamente o provedor com o resultado. O teste exige duas chamadas de chat e verifica que o registro foi efetivamente gravado com **25.90 reais**. A resposta de áudio do teste contém bytes simulados, não fala audível.

## Teste manual do JAR e persistência

O JAR foi iniciado em localhost com um banco H2 em arquivo separado para teste.

1. Consulta inicial: contagem 0, saldo 0.00.
2. `POST /api/transactions`: despesa de 25.90 reais, categoria FOOD, retornou registro com UUID e `createdAt`.
3. Consulta de saldo: contagem 1, despesa 25.90, saldo -25.90.
4. Processo encerrado e iniciado novamente.
5. A consulta manteve contagem 1 e saldo -25.90.

Dados sintéticos retornados: [smoke-test.json](smoke-test.json). Nenhum banco de teste está incluído no pacote.

Durante essa verificação foi desativada a escrita adiada do H2 (`WRITE_DELAY=0`), para que uma interrupção imediata após a resposta não perdesse o registro que ainda aguardava escrita em disco.

## Ajuste encontrado pelos testes

Spring AI 2.0.0-M4 apresentou erro ao converter uma data ISO diretamente para `LocalDate` no argumento da ferramenta. O adaptador agora recebe `String` e usa `LocalDate.parse`, mantendo as datas tipadas na aplicação/domínio. O teste completo confirmou o ajuste até a gravação no banco.

## Limites da validação

- Não houve chamada à OpenAI com uma chave real. Interpretação de linguagem natural, qualidade da transcrição e fala sintetizada real ainda precisam de teste com credencial configurada pelo usuário.
- Não foram realizados testes de carga, autenticação, isolamento entre usuários ou ambiente de produção.
- O workflow do GitHub Actions foi preparado, mas ainda não executado em um repositório remoto.
- Os testes locais não comprovam que o modelo sempre seguirá o prompt. Confira os registros depois de comandos por IA.
