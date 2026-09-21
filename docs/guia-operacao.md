# Guia de operação — subir e descer os serviços (local e nuvem)

Guia prático para operar o JogaFácil nas duas modalidades: **local** (Docker +
LocalStack, sem custo) e **nuvem** (AWS: ECS Fargate + ALB). Inclui a
verificação de **custo zero**.

Sumário:
1. [Pré-requisitos](#1-pré-requisitos)
2. [Ambiente local — subir](#2-ambiente-local--subir)
3. [Ambiente local — verificar e usar](#3-ambiente-local--verificar-e-usar)
4. [Ambiente local — descer](#4-ambiente-local--descer)
5. [Ambiente local sem Docker](#5-ambiente-local-sem-docker-opcional)
6. [Nuvem AWS — subir](#6-nuvem-aws--subir)
7. [Nuvem AWS — verificar e usar](#7-nuvem-aws--verificar-e-usar)
8. [Nuvem AWS — descer](#8-nuvem-aws--descer)
9. [Como garantir custo zero](#9-como-garantir-custo-zero)
10. [Solução de problemas](#10-solução-de-problemas)

---

## 1. Pré-requisitos

| Ferramenta | Para quê | Como validar |
|---|---|---|
| Java 21 | rodar/testar os serviços | `java -version` |
| Maven (ou `./mvnw`) | build | `./mvnw -v` |
| Docker + Compose v2 | ambiente local e build de imagem | `docker compose version` |
| AWS CLI v2 | operar a nuvem | `aws sts get-caller-identity` |
| Terraform 1.5+ | infraestrutura AWS | `terraform -version` |
| `curl` e `python3` | script de fumaça | `curl --version` |

Configure a AWS uma única vez (região sugerida `us-east-1`):

```bash
aws configure        # Access Key, Secret, regiao us-east-1
```

> Dica: os comandos abaixo são executados a partir da **raiz do repositório**.
> O teste ponta a ponta usa `RESERVATION_URL` e `MATCH_URL`; localmente são
> `http://localhost:8081` e `http://localhost:8082`.

---

## 2. Ambiente local — subir

Sobe o LocalStack (DynamoDB) e os dois serviços em containers. As tabelas são
criadas automaticamente pelo script de bootstrap do LocalStack.

```bash
docker compose -f infra/docker/docker-compose.yml up -d --build
```

O primeiro build compila o projeto dentro do Docker (alguns minutos). Depois,
as subidas são rápidas.

---

## 3. Ambiente local — verificar e usar

```bash
# Health dos dois servicos
curl -s localhost:8081/actuator/health
curl -s localhost:8082/actuator/health

# Teste ponta a ponta (cria partida -> reserva, conflito 409, cancela, reusa)
./scripts/smoke.sh
```

Acompanhar logs:

```bash
docker logs -f jogafacil-match-service
docker logs -f jogafacil-reservation-service
docker logs -f jogafacil-localstack
```

Exemplo manual de criação de partida (reserva o horário via REST):

```bash
curl -s -X POST localhost:8082/matches \
  -H 'Content-Type: application/vnd.jogafacil.v1+json' \
  -H 'Idempotency-Key: manual-1' \
  -d '{"courtId":"court_01ARZ3NDEKTSV4RRFFQ69G5FCC","slotId":"slot_01ARZ3NDEKTSV4RRFFQ69G5FDD","organizerId":"usr_01ARZ3NDEKTSV4RRFFQ69G5FAV","maxParticipants":10}'
```

---

## 4. Ambiente local — descer

```bash
# Para e remove os containers (mantem o volume, se houver)
docker compose -f infra/docker/docker-compose.yml down

# Para, remove containers E apaga os dados do LocalStack
docker compose -f infra/docker/docker-compose.yml down -v
```

---

## 5. Ambiente local sem Docker (opcional)

Útil para depurar pelo IDE. Suba só o LocalStack:

```bash
docker run --rm -d --name jogafacil-localstack -p 4566:4566 \
  -e SERVICES=dynamodb \
  -v "$PWD/infra/localstack/init:/etc/localstack/init/ready.d" \
  localstack/localstack:3.7.2
```

E rode cada serviço com o perfil `local` (aponta para `localhost:4566`):

```bash
./mvnw -q -pl services/reservation-service -am spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -q -pl services/match-service        -am spring-boot:run -Dspring-boot.run.profiles=local
```

---

## 6. Nuvem AWS — subir

O script `deploy.sh` cria os repositórios ECR, faz build/push das imagens e
aplica toda a infraestrutura com Terraform (ECS Fargate, ALB, Cloud Map,
DynamoDB, IAM, logs).

```bash
export AWS_REGION=us-east-1   # opcional (default)
./scripts/deploy.sh
```

Ao final ele imprime os endpoints, por exemplo:

```text
alb_dns_name = "jogafacil-alb-XXXXXXXXXX.us-east-1.elb.amazonaws.com"
```

Locais alternativos, se preferir executar por partes:

```bash
terraform -chdir=infra/terraform init
terraform -chdir=infra/terraform apply
```

> Os serviços levam ~2–3 minutos para ficar `RUNNING` e saudáveis no ALB após o
> `apply`.

---

## 7. Nuvem AWS — verificar e usar

```bash
export AWS_PAGER=""
ALB="http://$(terraform -chdir=infra/terraform output -raw alb_dns_name)"

# Servicos e tasks
aws ecs describe-services --cluster jogafacil-cluster \
  --services jogafacil-reservation-service jogafacil-match-service \
  --query 'services[].{servico:serviceName,status:status,running:runningCount}' --output table

# Saude dos alvos no ALB
aws elbv2 describe-target-health \
  --target-group-arn "$(aws elbv2 describe-target-groups \
      --names jogafacil-reservation --query 'TargetGroups[0].TargetGroupArn' --output text)"

# Fluxo ponta a ponta pela nuvem (o ALB nao roteia /actuator, por isso pulamos a espera)
SKIP_HEALTH_WAIT=1 RESERVATION_URL="$ALB" MATCH_URL="$ALB" ./scripts/smoke.sh

# Logs
aws logs tail /ecs/jogafacil/reservation-service --since 1h
aws logs tail /ecs/jogafacil/match-service      --since 1h
```

---

## 8. Nuvem AWS — descer

```bash
terraform -chdir=infra/terraform destroy
```

Confirme com `yes`. Isso remove **tudo** (ALB, ECS, ECR com imagens, DynamoDB,
Cloud Map, IAM, logs).

Caso a remoção do ECR falhe por conter imagens (contas sem `force_delete`),
force manualmente e repita o destroy:

```bash
aws ecr delete-repository --repository-name jogafacil/reservation-service --force
aws ecr delete-repository --repository-name jogafacil/match-service --force
terraform -chdir=infra/terraform destroy
```

---

## 9. Como garantir custo zero

O custo é controlado por **destruir quando não estiver em uso** + **alarmes**.

### 9.1 O que custa dinheiro

| Recurso | Custo se ficar no ar | Free tier? |
|---|---|---|
| ALB (Application Load Balancer) | ~US$16–20/mês (maior custo fixo) | não |
| 2× Fargate 0.25 vCPU / 0.5 GB | ~US$0,6/dia | praticamente não |
| IP público das tasks | US$0,005/hora cada | não |
| DynamoDB on-demand | centavos | 25 GB |
| CloudWatch Logs | centavos | 5 GB |
| Cloud Map | centavos | não |

Evitamos por projeto: NAT Gateway, RDS/Aurora, EC2, Elastic IPs ociosos.

### 9.2 Verificar que zerou (script de auditoria, somente leitura)

```bash
./scripts/aws-audit.sh
```

Saída esperada após o `destroy`:

```text
RESULTADO: tudo limpo. Nenhum recurso do JogaFacil ativo.
```

Se houver algo, o script lista o que sobrou e retorna código de erro, sugerindo
`terraform destroy`.

### 9.3 Alarmes de gasto (recomendado)

**AWS Budgets** — alertar em qualquer gasto:

```bash
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
aws budgets create-budget --account-id "$ACCOUNT" \
  --budget '{"BudgetName":"jogafacil-zero","BudgetType":"COST","TimeUnit":"MONTHLY","BudgetLimit":{"Amount":"0.01","Unit":"USD"}}' \
  --notifications-with-subscribers '[{"Notification":{"NotificationType":"ACTUAL","ComparisonOperator":"GREATER_THAN","Threshold":80,"ThresholdType":"PERCENTAGE"},"Subscribers":[{"SubscriptionType":"EMAIL","Address":"SEU_EMAIL@exemplo.com"}]}]'
```

**Alarme de billing no CloudWatch** (dispara acima de US$1):

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name jogafacil-billing-1usd \
  --namespace AWS/Billing --metric-name EstimatedCharges \
  --dimensions Name=Currency,Value=USD \
  --statistic Maximum --period 21600 --evaluation-periods 1 \
  --threshold 1 --comparison-operator GreaterThanThreshold
```

> O alarme de billing só funciona na região **us-east-1**.

### 9.4 Sobre o crédito

A conta possui **US$100 em crédito por 6 meses**, aplicado automaticamente.
Com a infraestrutura destruída, o consumo é praticamente zero e o crédito cobre
qualquer centavo residual (prolação do ALB no mês). **Sem destruir**, o crédito
é consumido a ~US$1,5–2/dia.

**Portanto:** "custo zero do seu bolso" = `destroy` ao terminar + `aws-audit.sh`
confirmando limpo + Budget/Billing alarm de rede de segurança.

---

## 10. Solução de problemas

| Sintoma | Causa provável | Ação |
|---|---|---|
| `docker login` no ECR dá timeout de DNS | instabilidade momentânea do DNS do Docker | repetir o login (`./scripts/deploy.sh` reexecuta a etapa) |
| Erro `serviceRegistries ... do not require a value for 'Port'` | registro tipo A no Cloud Map não aceita porta | o Terraform já corrige; remova `port` do bloco `service_registries` |
| `POST /matches` retorna 500/503 logo após subir | corrida com a criação das tabelas no LocalStack | aguardar alguns segundos e repetir (o `smoke.sh` já faz retry) |
| `Cannot do operations on a non-existent table` | bootstrap do LocalStack ainda não rodou | verificar `docker logs jogafacil-localstack`; aguardar "tabelas prontas" |
| `mvn: command not found` | Maven não instalado | usar `./mvnw` (não depende de Maven global) |
| Containers locais órfãos consumindo recursos | não desceu o ambiente | `docker compose -f infra/docker/docker-compose.yml down -v` |
