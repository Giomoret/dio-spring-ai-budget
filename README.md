# Orçamento por voz com Spring AI

Projeto de estudo para o desafio **API Inteligente com Spring Boot e Spring AI**, da DIO.

**Autor:** Giovanni Alvarenga Moretto.

## O que faz

Recebe comandos de texto ou um arquivo de áudio, transcreve a fala, utiliza `ChatClient` para selecionar ferramentas reais da aplicação e devolve uma resposta. No endpoint de áudio, a resposta é sintetizada em MP3. O aplicativo registra receitas/despesas em um orçamento local; não movimenta dinheiro nem se conecta a bancos.

```text
Áudio → TranscriptionModel → ChatClient → @Tool → BudgetService → domínio → JPA/H2
                                 ↓
                       resposta → TextToSpeechModel → MP3
```

## Evoluções em relação à proposta base

- **Saldo por período:** ferramenta `summarizeBudget` calcula receitas, despesas, saldo e quantidade de registros. Datas inicial e final são inclusivas.
- **Receitas e despesas:** tipos `INCOME` e `EXPENSE`; valores sempre positivos e em reais.
- **Validações compartilhadas:** valor positivo, limite de 12 dígitos inteiros, precisão de centavos, descrição e campos obrigatórios. São aplicadas no domínio, inclusive quando a IA chama ferramentas.
- **Precisão monetária:** `BigDecimal` de ponta a ponta, sem conversão para `double` e sem misturar reais e centavos.
- **Execução local simples:** H2 em arquivo preserva os dados entre reinicializações; não exige Docker/MySQL.
- **Testes sem cobrança:** regras, HTTP e banco reais, com modelos de IA simulados nas verificações do fluxo de áudio.
- **Rastreabilidade básica:** UUID e `createdAt` para cada registro. Não equivale a um histórico completo de auditoria de alterações.

## Tecnologias e arquitetura

Java 25, Spring Boot 4.0.5, Spring AI 2.0.0-M4, Spring Web, Bean Validation, Spring Data JPA, H2, JUnit 5, Mockito e Gradle Wrapper 9.4.1.

As versões de Boot/AI/Java acompanham o módulo de referência consultado. Spring AI M4 é uma versão de pré-lançamento, mantida aqui para acompanhar o laboratório.

| Camada | Responsabilidade |
|---|---|
| `domain` | Transação imutável, categorias, tipos, validações e contrato de repositório |
| `application` | Criação, consultas, saldo e coordenação do fluxo de áudio |
| `infrastructure/ai` | ChatClient, transcrição, síntese e adaptação das ferramentas |
| `infrastructure/persistence` | Entidade JPA e implementação do repositório |
| `infrastructure/http` | Endpoints, DTOs e respostas de erro |

REST e ferramentas usam o mesmo `BudgetService`. O domínio não depende do Spring AI nem do JPA.

## Como executar

Instale JDK **25** e configure `JAVA_HOME` para a pasta do JDK. Não é necessário instalar Gradle: o wrapper baixa a distribuição e as dependências na primeira execução, que requer internet.

### 1. REST sem chave de IA

PowerShell, dentro desta pasta:

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

Linux/macOS:

```sh
sh gradlew test
sh gradlew bootRun
```

A aplicação escuta em `http://127.0.0.1:8080`. Neste modo os endpoints de IA retornam **503**, com instrução para ativar o perfil. Não há interpretação fictícia de comandos.

### 2. Ativar texto e áudio com IA real

Configure `OPENAI_API_KEY` **no seu terminal**, sem colocar a chave no código, em commits ou em capturas de tela. O arquivo `.env.example` é apenas uma referência: `.env` não é carregado automaticamente.

```powershell
# Depois de definir OPENAI_API_KEY no ambiente deste terminal:
.\gradlew.bat bootRun --args='--spring.profiles.active=ai'
```

```sh
# Depois de definir OPENAI_API_KEY no ambiente deste terminal:
sh gradlew bootRun --args='--spring.profiles.active=ai'
```

Modelos padrão do laboratório: `gpt-4o-mini`, `whisper-1` e `gpt-4o-mini-tts`. É possível substituí-los pelas variáveis `OPENAI_CHAT_MODEL`, `OPENAI_TRANSCRIPTION_MODEL` e `OPENAI_SPEECH_MODEL`, usando modelos compatíveis com cada recurso e disponíveis para sua conta. A API do provedor exige credencial e pode cobrar por uso.

## Endpoints

| Método e caminho | Entrada | Saída |
|---|---|---|
| `POST /api/transactions` | JSON da transação | 201 + transação com UUID e data de criação |
| `GET /api/transactions?start=2026-10-01&end=2026-10-31` | Datas obrigatórias; `category` opcional | Lista de transações |
| `GET /api/transactions/summary?start=2026-10-01&end=2026-10-31` | Período obrigatório | Receitas, despesas, saldo e contagem |
| `POST /api/assistant/text` | `{"message":"..."}` | `{"reply":"..."}` |
| `POST /api/assistant/audio` | Multipart com campo `file` | MP3, `Content-Type: audio/mpeg` |

Categorias: `SALARY`, `FOOD`, `HEALTH`, `TRANSPORT`, `HOUSING`, `LEISURE`, `OTHER`.
Datas: `yyyy-MM-dd`. Valores: **reais**, como `25.90`, nunca `2590` para representar R$ 25,90.

### Criar uma despesa e consultar saldo

PowerShell:

```powershell
$body = @{
    description = 'Almoco'
    amount = 25.90
    type = 'EXPENSE'
    category = 'FOOD'
    date = '2026-10-27'
} | ConvertTo-Json
Invoke-RestMethod http://127.0.0.1:8080/api/transactions -Method Post -ContentType 'application/json' -Body $body
Invoke-RestMethod 'http://127.0.0.1:8080/api/transactions/summary?start=2026-10-01&end=2026-10-31'
```

Em um banco vazio, o saldo esperado após essa despesa é **-25.90**, receitas **0.00**, despesas **25.90** e contagem **1**.

### Testar texto com IA

Com perfil `ai` e chave configurada:

```powershell
$body = @{ message = 'Registre uma despesa de 25,90 reais de almoco, categoria FOOD, em 27 de outubro de 2026.' } | ConvertTo-Json
Invoke-RestMethod http://127.0.0.1:8080/api/assistant/text -Method Post -ContentType 'application/json' -Body $body
$body = @{ message = 'Qual meu saldo de 1 a 31 de outubro de 2026?' } | ConvertTo-Json
Invoke-RestMethod http://127.0.0.1:8080/api/assistant/text -Method Post -ContentType 'application/json' -Body $body
```

### Testar o fluxo de voz

Grave um arquivo próprio dizendo, por exemplo: “Registre uma despesa de vinte e cinco reais de almoço na categoria alimentação hoje”. Evite dados pessoais reais no teste.

No Windows, use `curl.exe` (não o alias `curl` de versões antigas do PowerShell):

```powershell
curl.exe --fail-with-body http://127.0.0.1:8080/api/assistant/audio -F 'file=@comando.wav' --output resposta.mp3
```

Linux/macOS: substitua `curl.exe` por `curl`. Abra `resposta.mp3` em seu reprodutor de áudio; **a voz é gerada por IA**. Confirme o lançamento consultando `/api/transactions`. Se houver erro, o arquivo de saída pode conter JSON de erro, e não áudio.

São aceitas extensões mp3, wav, m4a, webm, mp4, mpeg e mpga, até 10 MB. A checagem local é de tamanho/extensão; a decodificação e validação do áudio são feitas pelo provedor.

## Persistência, erros e limites

- Dados em `data/budget.mv.db`, ignorados pelo Git. `WRITE_DELAY=0` desativa a escrita adiada do H2. Testes usam H2 em memória isolado.
- Erros de validação usam `ProblemDetail` e status 400; uploads muito grandes usam 413; IA desativada usa 503; falhas do provedor e respostas de IA vazias usam 502, sem expor seus detalhes internos.
- Tipo e categoria devem ser enviados pelos nomes, como `EXPENSE` e `FOOD`. Índices numéricos são rejeitados para evitar conversões silenciosas.
- As conversas são independentes: não há memória entre requisições. Informe os detalhes na mesma mensagem.
- Não há autenticação ou separação por usuário. Uso didático local, com bind em `127.0.0.1`; todos os registros pertencem a um único orçamento.
- A IA pode interpretar comandos incorretamente. Confira as transações registradas. O prompt orienta perguntar em caso de ambiguidade, mas isso não é uma garantia determinística.
- Não há idempotência: repetir um pedido pode duplicar um lançamento. Se a síntese falhar depois de uma ferramenta gravar, consulte os registros antes de reenviar. Uma implementação futura pode acrescentar confirmação e chave de idempotência.
- UUID e `createdAt` dão rastreabilidade básica; não há exclusão/edição nem histórico de mudanças.

## Testes e evidências

```powershell
.\gradlew.bat clean test bootJar
```

O relatório é gerado em `build/reports/tests/test/index.html`. A suíte possui **26 testes**, incluindo API HTTP com servidor real, persistência em H2, valores monetários, limites de período, validações também pelas ferramentas, fluxo transcrição → chat → voz e adaptação das interfaces Spring AI. Inclui erros do provedor, respostas vazias, upload acima de 10 MB e rejeição de índices numéricos em enums. Modelos são simulados nos testes: não equivalem a uma chamada real ao provedor nem comprovam a qualidade da interpretação do modelo. Consulte [validação](docs/VALIDACAO.md) e [revisão de código](docs/REVISAO.md) para os resultados locais.

## Aprendizados e conceitos explorados

### Fluxo de uma aplicação com IA

O áudio enviado é convertido em texto. O modelo interpreta o comando e pode solicitar uma ferramenta Java para registrar ou consultar transações. A aplicação executa essa função e devolve o resultado ao modelo, que produz a resposta final. No fluxo de áudio, essa resposta também é convertida em voz.

### Papel do Tool Calling

A anotação `@Tool` identifica métodos disponibilizados ao modelo pelo Spring AI. O modelo solicita uma chamada, mas é a aplicação que executa o código Java, respeitando as regras e validações existentes. A IA não tem acesso direto ao banco.

### Validação e organização das responsabilidades

A IA pode interpretar informações incorretamente ou fornecer dados incompletos. As validações impedem registros com valores inválidos, datas incorretas ou campos obrigatórios ausentes. Elas ficam no domínio para proteger tanto as chamadas REST quanto as ferramentas. Essa separação permite evoluir a integração de IA sem substituir as regras de negócio.

### Melhoria implementada

A consulta de saldo por período permite acompanhar receitas, despesas e o resultado financeiro em um intervalo. O cálculo usa `BigDecimal` para preservar a precisão monetária. Explicitar que os valores são em reais evita confusão com centavos nos argumentos das ferramentas.

### Próximos passos

A integração com chave real da OpenAI ainda precisa ser validada para avaliar a transcrição, a interpretação de comandos e a voz gerada. Os testes atuais usam provedor simulado. Outras evoluções possíveis são autenticação e prevenção de lançamentos duplicados.

## Referências e origem

Implementação própria orientada pelo fluxo e pela arquitetura do [módulo oficial Spring AI da DIO](https://github.com/digitalinnovationone/dio-spring-boot-learning-track/tree/main/05-spring-ai), consultado no commit `9add4345f9f0f75e1a02dcd557c627fdf6187ff7`. Não é uma cópia integral nem um fork. Os arquivos do Gradle Wrapper foram aproveitados como infraestrutura de build; os scripts preservam os cabeçalhos de licença originais.

- [Enunciado técnico do módulo](https://github.com/digitalinnovationone/dio-spring-boot-learning-track/blob/main/05-spring-ai/README.md)
- [Spring AI: ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI: ferramentas](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI: transcrição](https://docs.spring.io/spring-ai/reference/api/audio/transcriptions.html)
- [Spring AI: geração de voz](https://docs.spring.io/spring-ai/reference/api/audio/speech.html)

## Entrega na DIO

Publique o conteúdo desta pasta em seu próprio repositório e envie a URL no desafio. Não envie `.env`, chaves, `data/`, `build/` ou `.gradle/`. Antes da entrega final, execute os testes e, se tiver uma chave de API, registre seu teste manual de texto/voz na seção de evidências.
