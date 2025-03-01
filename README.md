# Envelofy

Envelofy is a personal finance management application designed to help you budget and track your expenses using the envelope budgeting method. With this app, you can categorize your spending, manage multiple accounts, and gain valuable insights into your financial habits through machine learning-powered analytics. Built with a modern tech stack, Envelofy provides a user-friendly interface and robust features to keep your finances in check. Envelofy also features an integrated LLM (Large Language Model) assistant, configurable to use providers like Groq, OpenAI, Ollama, or JLlama, to provide real-time non-financial advice, answer questions, and assist with managing your budget.

## Key Features

- **Envelope Budgeting**: Create and manage budget envelopes for different spending categories, each with an allocated amount to track your spending effectively.
- **Account Management**: Monitor multiple financial accounts, such as checking, savings, or credit cards, all in one place.
- **Transaction Recording**: Record income and expenses easily, linking them to specific envelopes and accounts.
- **Recurring Transactions**: Automate regular income or expenses with customizable recurring transaction setups.
- **Subscription Finder**: Automatically identify and categorize recurring subscriptions from your transaction history, helping you manage and optimize your regular expenses.
- **Smart Insights**: Leverage AI-driven analytics to detect unusual spending, predict future expenses, and receive budget suggestions.
- **Integrated LLM Assistant**: An LLM option is integrated as an assistant - supports multiple providers (Groq, OpenAI, Ollama, JLlama) to offer personalized non-financial guidance and natural language interaction (very glitchy!).
- **Data Import**: Quickly import transactions from CSV files
- **Customizable Settings**: Tailor the app to your preferences with options for appearance, security, and more.

## Setup and Installation

To get started with Envelofy, ensure you have the following prerequisites installed:

- **Java Development Kit (JDK) 17 or later**
- **Maven** for building the project
- A modern **web browser** to access the application

Follow these steps to set up and run Envelofy:

1. **Clone the Repository**:
   ```
   git clone https://github.com/nicholasjemblow/envelofy.git
   ```

2. **Navigate to the Project Directory**:
   ```
   cd envelofy
   ```

3. **Build the Project**:
   ```
   JAVA_HOME=/path/to/jdk/21 mvn --no-transfer-progress -Pproduction clean install
   ```

4. **Run the Application**:
   ```
   JAVA_HOME=/path/to/jdk/21 mvn   spring-boot:run   -Dspring-boot.run.jvmArguments="--add-modules jdk.incubator.vector --enable-preview"   --no-transfer-progress   -Pproduction
   ```

5. **Access the Application**:
   Open your web browser and go to `http://localhost:8081`.

## Configuration

Envelofy uses an **H2 in-memory database** by default, configured in `src/main/resources/application.properties`. For basic usage, no changes are needed. However, for production environments, consider switching to a persistent database like PostgreSQL or MySQL by updating the database settings in the properties file.

Example configuration snippet:
```
spring.datasource.url=jdbc:h2:file:./envelopifydb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
```

To explore the H2 database, access the H2 Console at `http://localhost:8081/h2-console` (enabled by default).

### LLM Configuration
The integrated LLM assistant is powered by the `LLMService`, which supports multiple providers: **Groq**, **OpenAI**, **Ollama**, and **JLlama**. Configuration is handled directly within the application via the **Settings screen**, where you can:

- Select your preferred LLM provider.
- Enter API keys (e.g., for Groq or OpenAI).
- Specify endpoints (e.g., for Ollama local instances) or model paths (e.g., for JLlama).
- Adjust additional LLM-specific settings as needed.

Supported providers:
- **Groq**: Fast and efficient inference (requires an API key).
- **OpenAI**: Industry-standard LLM capabilities (requires an API key).
- **Ollama**: Open-source, local LLM hosting (requires a running local instance).
- **JLlama**: Java-based LLM integration (requires model file configuration).

Once configured in the settings screen, the assistant will use your chosen provider to deliver real-time non-financial advice and support. Refer to the in-app documentation or tooltips in the settings interface for detailed guidance.


## Technology Stack

Envelofy is built with a robust and modern technology stack:

- **Backend**: Spring Boot, Java 17
- **Frontend**: Vaadin Flow, JavaScript (for custom Web Components)
- **Database**: H2 (default), with support for other SQL databases
- **Machine Learning**: Custom ML service for spending insights
- **LLM Integration**: `LLMService` abstraction Supporting Groq, OpenAI, Ollama, and JLlama for the assistant feature
- **Build Tool**: Maven

The app integrates Vaadin for a responsive UI and uses Web Components (via JavaScript) for enhanced visualizations like charts and data tables. The LLM assistant leverages a flexible `LLMService` to connect with various language model providers, enabling natural language understanding and non financial advice.

## License

Envelofy is open-source software licensed under the **GNU General Public License v3.0**. For more details, see the [LICENSE](LICENSE) file in the repository.

## Contributing

We welcome contributions to Envelofy! To get involved:

1. Fork the repository.
2. Create a new branch for your feature or bug fix.
3. Submit a pull request with a clear description of your changes.

## Support

Encounter an issue or have a question? Open an issue on the [GitHub repository](https://github.com/nicholasjemblow/envelofy/issues). 
