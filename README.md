# Simulado - Sistema de Estudos Acelerados para Concursos Cebraspe

Sistema de estudos acelerados para concursos Cebraspe, desenvolvido com **Spring Boot 3.4.5**, **Spring AI** e **Ollama** para inteligência artificial, utilizando **PGVector** para armazenamento vetorial e busca semântica.

## 🚀 Funcionalidades

- **Questões**: Gerenciamento e simulação de questões estilo Cebraspe (Certo/Errado)
- **Simulações**: Realização de simulados com correção automática e análise de desempenho
- **Redação**: Geração de esqueletos de redação com IA
- **Análise de Pareto**: Identificação dos tópicos mais relevantes para estudo (80/20)
- **RAG (Retrieval-Augmented Generation)**: Busca semântica em documentos PDF usando PGVector
- **Tópicos**: Organização de conteúdo por disciplinas e tópicos

## 🛠️ Tecnologias

- **Java 21**
- **Spring Boot 3.4.5**
- **Spring AI** (com Ollama e PGVector)
- **PostgreSQL + PGVector** (banco de dados vetorial)
- **Flyway** (migrações de banco de dados)
- **Docker & Docker Compose**

## 📋 Pré-requisitos

- Java 21 ou superior
- Maven 3.8+
- Docker e Docker Compose
- Ollama instalado e configurado com os modelos:
  - `llama3.2:3b` (chat)
  - `nomic-embed-text` (embeddings)

## ⚙️ Configuração do Ambiente

### 1. Instalar e Configurar Ollama

```bash
# Instalar Ollama (Linux/Mac)
curl -fsSL https://ollama.com/install.sh | sh

# Baixar modelos necessários
ollama pull llama3.2:3b
ollama pull nomic-embed-text

# Iniciar o servidor Ollama
ollama serve
```

### 2. Subir o Banco de Dados com Docker

```bash
docker-compose up -d
```

Isso iniciará o PostgreSQL com extensão PGVector na porta `5432`.

### 3. Configurar a Aplicação

As configurações estão em `src/main/resources/application-dev.yaml`. Ajuste se necessário:

- URL do banco de dados
- Credenciais do PostgreSQL
- Base URL do Ollama
- Modelos de IA
- Parâmetros do PGVector

## 🏃‍♂️ Como Executar

### Opção 1: Usando Maven Wrapper

```bash
./mvnw spring-boot:run
```

### Opção 2: Usando Maven Direto

```bash
mvn spring-boot:run
```

### Opção 3: Build e Execução do JAR

```bash
./mvnw clean package -DskipTests
java -jar target/simulado-1.0.0-SNAPSHOT.jar
```

A aplicação estará disponível em: **http://localhost:8080**

## 📁 Estrutura do Projeto

```
simulado/
├── src/
│   ├── main/
│   │   ├── java/br/cebraspe/simulado/
│   │   │   └── domain/
│   │   │       ├── essay/          # Redação (esqueletos com IA)
│   │   │       ├── question/       # Questões e respostas
│   │   │       ├── simulation/     # Simulados e resultados
│   │   │       ├── pareto/         # Análise de Pareto
│   │   │       └── topic/          # Tópicos de estudo
│   │   └── resources/
│   │       ├── db/migration/       # Scripts Flyway
│   │       └── application*.yaml   # Configurações
│   └── test/
├── docker-compose.yml
├── pom.xml
└── README.md
```

## 🔌 API Endpoints

### Questões
- `GET /api/questions` - Listar questões
- `POST /api/questions` - Criar questão
- `GET /api/questions/{id}` - Obter questão por ID

### Simulações
- `POST /api/simulations` - Criar simulação
- `GET /api/simulations/{id}` - Obter resultado da simulação

### Redação
- `POST /api/essay-skeletons` - Gerar esqueleto de redação com IA

### Pareto
- `GET /api/pareto` - Obter análise de Pareto dos tópicos

### Tópicos
- `GET /api/topics` - Listar tópicos
- `POST /api/topics` - Criar tópico

## 🧪 Testes

```bash
./mvnw test
```

## 📊 Configurações Personalizadas

No arquivo `application-dev.yaml`, você pode ajustar:

```yaml
app:
  pareto:
    threshold: 0.70        # Limiar para análise de Pareto
  simulation:
    penalty-factor: 1.0    # Fator de penalidade para erros
  rag:
    chunk-size: 387        # Tamanho do chunk para RAG
    chunk-overlap: 50      # Overlap entre chunks
```

## 🐳 Docker

O projeto utiliza Docker Compose para subir o banco de dados:

```bash
# Iniciar serviços
docker-compose up -d

# Parar serviços
docker-compose down

# Ver logs
docker-compose logs -f
```

## 📝 Licença

Este projeto é parte do sistema de estudos para concursos Cebraspe.

## 👥 Contribuição

1. Faça um fork do projeto
2. Crie uma branch para sua feature (`git checkout -b feature/nova-feature`)
3. Commit suas mudanças (`git commit -m 'Adiciona nova feature'`)
4. Push para a branch (`git push origin feature/nova-feature`)
5. Abra um Pull Request

## 📞 Suporte

Para dúvidas ou problemas, abra uma issue no repositório.

---

**Desenvolvido com ❤️ para estudantes de concursos Cebraspe**
