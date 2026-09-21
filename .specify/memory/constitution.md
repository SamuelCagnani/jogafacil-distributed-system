<!--
Sync Impact Report
==================
Versão: scaffold sem versão → 1.0.0
Tipo de incremento: MAJOR (adoção inicial ratificada)
Princípios modificados:
  - [PRINCIPLE_1..5] → substituídos por I..VI (documento preenchido)
Seções adicionadas:
  - Core Principles (I–VI)
  - Tecnologias e Restrições da Plataforma
  - Fluxo de Desenvolvimento e Qualidade
  - Governance
Seções removidas: Nenhuma
TODOs adiados: Nenhum
-->

# JogaFácil Constitution

## Core Principles

### I. Microsserviços Orientados a Domínio

Cada funcionalidade central MUST ser implementada como um serviço independente,
implantável e escalável de forma autônoma (user-service, court-service, match-service,
reservation-service, notification-service). Cada serviço MUST ser dono do seu domínio e
dos seus dados; nenhum serviço MUST acessar diretamente o armazenamento de outro. Toda
integração entre serviços MUST ocorrer por contrato de API ou evento de domínio. Cada
serviço MUST suportar múltiplas instâncias simultâneas (múltiplas tasks) sem estado local
compartilhado.

Rationale: isolar falhas e permitir escala independente; quadras, partidas e reservas
possuem cargas e ritmos distintos.

### II. Consistência e Coordenação sob Concorrência (NÃO NEGOCIÁVEL)

Operações concorrentes sobre recursos compartilhados (horários, reservas e vagas) MUST
ser serializáveis: no máximo uma de duas solicitações conflitantes MUST ser confirmada.
O controle de concorrência MUST usar escrita condicional (DynamoDB Conditional Writes)
sobre o estado esperado do recurso, e a operação MUST falhar de forma explícita e
idempotente quando esse estado não for mais válido. Não MUST existir confirmação de
reserva ou de vaga sem verificação de condição. Toda reserva MUST ser única por
(quadra, horário) e nenhuma partida MUST ultrapassar o seu número máximo de participantes.

Rationale: a dupla reserva e o overbooking de vagas são o risco central do domínio e o
principal objetivo didático de Sistemas Distribuídos do projeto.

### III. Comunicação Contratualizada e Desacoplada

A comunicação síncrona MUST usar REST sobre HTTPS com contratos versionados e
documentados. A comunicação assíncrona MUST usar filas e tópicos (Amazon SQS e Amazon
SNS). Consumidores de eventos MUST ser idempotentes, deduplicando por identificador de
evento. Serviços MUST se referenciar por nome lógico via descoberta de serviço (AWS Cloud
Map), nunca por endereço IP fixo. Quando a resposta não for necessária para concluir a
operação, MUST ser preferido o fluxo assíncrono.

Rationale: desacoplar serviços, evitar dependência da disponibilidade imediata de outro
serviço e permitir a distribuição de um mesmo evento a múltiplos consumidores.

### IV. Identificação Única e Ordenação de Eventos

Todo recurso e todo evento MUST possuir um identificador único global. Eventos de domínio
MUST seguir um envelope canônico contendo `event_id`, `type`, `timestamp` (UTC, ISO-8601),
`service_id` e `logical_clock`. Eventos MUST ser ordenáveis por relógio lógico de modo a
reconstruir a causalidade entre instâncias. Nenhum evento MUST ser publicado sem
identificador e carimbo temporal.

Rationale: nomeação consistente e ordenação são pré-requisitos para deduplicação,
auditoria, análise de consistência e depuração do comportamento distribuído.

### V. Tolerância a Falhas e Recuperação Automática

A indisponibilidade de uma instância MUST NOT interromper o serviço: o sistema MUST operar
com múltiplas instâncias e substituição automática. Todo serviço MUST expor verificação de
saúde (health check) e métricas de falha, e o sistema MUST detectar e se recuperar
automaticamente (Amazon CloudWatch + Auto Scaling). Operações interrompidas MUST ser
retomadas ou revertidas de forma consistente, preservando a integridade do estado. O
sistema MUST degradar de forma controlada e observável, nunca de forma silenciosa.

Rationale: garantir continuidade do serviço e preservação do estado diante de falhas são
requisitos explícitos de demonstração do projeto.

### VI. Segurança e Observabilidade por Padrão

Toda comunicação externa MUST usar HTTPS/TLS e as APIs públicas MUST ser protegidas pelo
AWS WAF. O acesso a recursos MUST exigir autenticação e autorização por papel (PLAYER,
ORGANIZER, OWNER, ADMIN), seguindo o princípio do menor privilégio (AWS IAM). Segredos
MUST ser armazenados no AWS Secrets Manager e chaves criptográficas gerenciadas pelo AWS
KMS; segredos MUST NOT aparecer em código, logs ou no repositório. Todos os componentes
MUST emitir logs estruturados, métricas e alarmes para o Amazon CloudWatch, cobrindo no
mínimo requisições, erros, latência, tamanho de filas, operações de reserva e ocorrências
de concorrência.

Rationale: segurança e observabilidade são condições de entrega e a base para demonstrar
o comportamento distribuído do sistema.

## Tecnologias e Restrições da Plataforma

A plataforma MUST ser executada integralmente na AWS e a stack abaixo é vinculante;
alterações MUST seguir o processo de emenda desta constituição.

- **Arquitetura / Middleware**: Amazon API Gateway como entrada das APIs e AWS App Mesh
  para gerenciamento da comunicação interna.
- **Processos e Virtualização**: containers Docker executados em Amazon ECS com AWS
  Fargate.
- **Comunicação**: REST sobre HTTPS para operações síncronas; Amazon SQS para operações
  assíncronas; Amazon SNS para distribuição de eventos.
- **Coordenação e Concorrência**: Amazon DynamoDB com escritas condicionais para reservas
  e controle de vagas.
- **Nomeação e Descoberta**: AWS Cloud Map e identificadores únicos para serviços e
  recursos.
- **Consistência e Replicação**: Amazon Aurora Multi-AZ para persistência e alta
  disponibilidade.
- **Tolerância a Falhas**: Amazon CloudWatch e Auto Scaling para monitoramento, detecção
  e recuperação.
- **Segurança**: AWS IAM, AWS Secrets Manager, AWS KMS e AWS WAF.
- **Observabilidade**: Amazon CloudWatch para métricas, logs e alarmes.

O escopo do MVP definido no documento de requisitos MUST ser priorizado; funcionalidades
posteriores (pagamentos reais, chat, ranking, matchmaking por nível, campeonatos,
aplicativo mobile nativo, entre outras) MUST NOT ser implementadas antes da conclusão do
MVP.

## Fluxo de Desenvolvimento e Qualidade

- Toda mudança MUST ser submetida via Pull Request e revisada por, no mínimo, um outro
  integrante antes do merge.
- Testes de concorrência, consistência e tolerância a falhas MUST acompanhar os
  mecanismos correspondentes; nenhum Pull Request pode ser integrado com testes
  obrigatórios falhando.
- As garantias dos Princípios II e V MUST ser verificáveis por teste automatizado ou por
  experimento documentado e reproduzível.
- A infraestrutura MUST ser definida como código (containers e provisionamento AWS), não
  por configuração manual não versionada.
- Cada entrega MUST ser acompanhada da documentação técnica correspondente e da
  descrição dos experimentos realizados.

## Governance

Esta constituição substitui as demais práticas do projeto; em caso de conflito, os
princípios aqui definidos prevalecem.

- **Emendas**: qualquer alteração MUST ser proposta via Pull Request sobre
  `.specify/memory/constitution.md`, contendo descrição da mudança, justificativa e
  atualização de versão. A aprovação de ambos os integrantes (Ana Carolina Fuentes e
  Samuel de Mello Cagnani) é exigida para ratificação.
- **Versionamento**: a constituição segue versionamento semântico. MAJOR para remoções ou
  redefinições incompatíveis de princípios/governança; MINOR para adição de princípio ou
  expansão material de orientação; PATCH para esclarecimentos, correções de texto ou
  refinamentos não semânticos.
- **Conformidade**: toda revisão de Pull Request MUST verificar aderência a esta
  constituição. Violações MUST ser registradas e corrigidas, e a complexidade adicionada
  MUST ser justificada. O fluxo de especificação, planejamento e tarefas MUST observar os
  princípios aqui definidos como orientação de execução.

**Version**: 1.0.0 | **Ratified**: 2026-09-16 | **Last Amended**: 2026-09-16
