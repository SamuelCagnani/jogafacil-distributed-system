#!/usr/bin/env bash
#
# Auditoria SOMENTE LEITURA da conta AWS.
#
# Verifica se sobrou algum recurso do JogaFacil (que geraria custo) e se ha
# recursos comuns que cobram por hora em qualquer conta. Nao cria nem altera
# nada.
#
# Uso:  ./scripts/aws-audit.sh
# Saida: 0 = tudo limpo | 1 = ha recursos ativos | 2 = AWS CLI nao configurada
#
set -uo pipefail

if ! aws sts get-caller-identity >/dev/null 2>&1; then
  echo "ERRO: AWS CLI nao configurada ou sem credenciais. Rode: aws configure"
  exit 2
fi

ACCOUNT="$(aws sts get-caller-identity --query Account --output text 2>/dev/null)"
REGION="$(aws configure get region 2>/dev/null || true)"
echo "Conta AWS: ${ACCOUNT:-?} | Regiao: ${REGION:-nao definida}"

LEFTOVERS=0

check() {
  local label="$1"
  shift
  local result
  result="$("$@" 2>/dev/null | tr '\t' '\n' | sed '/^$/d')"
  if [ -z "$result" ]; then
    printf '  [ok]      %s\n' "$label"
  else
    printf '  [ATENCAO] %s:\n' "$label"
    printf '            %s\n' "$result"
    LEFTOVERS=1
  fi
}

echo
echo "== Recursos do JogaFacil =="
check "ECS clusters"          aws ecs list-clusters --query 'clusterArns' --output text
check "ECR repositorios"      aws ecr describe-repositories --query "repositories[?contains(repositoryName,'jogafacil')].repositoryName" --output text
check "Load balancers"        aws elbv2 describe-load-balancers --query "LoadBalancers[?contains(LoadBalancerName,'jogafacil')].LoadBalancerName" --output text
check "DynamoDB tabelas"      aws dynamodb list-tables --query "TableNames[?contains(@,'jogafacil')]" --output text
check "CloudWatch log groups" aws logs describe-log-groups --log-group-name-prefix /ecs/jogafacil --query 'logGroups[].logGroupName' --output text
check "Cloud Map namespaces"  aws servicediscovery list-namespaces --query "Namespaces[?contains(Name,'jogafacil')].Name" --output text
check "IAM roles"             aws iam list-roles --query "Roles[?contains(RoleName,'jogafacil')].RoleName" --output text
check "Security groups"       aws ec2 describe-security-groups --query "SecurityGroups[?contains(GroupName,'jogafacil')].GroupName" --output text
check "CloudWatch alarms"     aws cloudwatch describe-alarms --query "MetricAlarms[?contains(AlarmName,'jogafacil')].AlarmName" --output text

echo
echo "== Recursos que cobram por hora em qualquer conta =="
check "Elastic IPs"           aws ec2 describe-addresses --query 'Addresses[].PublicIp' --output text
check "NAT gateways"          aws ec2 describe-nat-gateways --query "NatGateways[?State=='available'].NatGatewayId" --output text
check "EC2 em execucao"       aws ec2 describe-instances --filters "Name=instance-state-name,Values=running" --query 'Reservations[].Instances[].InstanceId' --output text
check "RDS instancias"        aws rds describe-db-instances --query 'DBInstances[].DBInstanceIdentifier' --output text
check "S3 buckets jogafacil"  aws s3api list-buckets --query "Buckets[?contains(Name,'jogafacil')].Name" --output text

echo
if [ "$LEFTOVERS" -eq 0 ]; then
  echo "RESULTADO: tudo limpo. Nenhum recurso do JogaFacil ativo."
  echo "Observacao: o Billing pode levar ate 24h para refletir; centavos de"
  echo "projecao sao cobertos pelo credito da conta."
  exit 0
else
  echo "RESULTADO: existem recursos ativos que podem gerar custo."
  echo "Para remover tudo:  terraform -chdir=infra/terraform destroy"
  exit 1
fi
