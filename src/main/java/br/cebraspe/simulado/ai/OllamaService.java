package br.cebraspe.simulado.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OllamaService {

    private final ChatModel chatModel;

    public OllamaService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    private static final String SYSTEM_CONTEXT = """
            Você é um assistente especialista em concursos públicos brasileiros,
            especialmente na banca Cebraspe/Cespe. Seu foco é em direito público,
            administração pública, controle externo e legislação federal.
            Seja direto, técnico e preciso. Quando mencionar leis, cite artigos e parágrafos.
            """;

    public String chat(String userMessage) {
        ChatResponse response = chatModel.call(
                new Prompt(List.of(
                        new SystemMessage(SYSTEM_CONTEXT),
                        new UserMessage(userMessage))));
        return response.getResult().getOutput().getText();
    }

    public String chatWithContext(String userMessage, String ragContext) {
        String contextualSystem = SYSTEM_CONTEXT + """

                Contexto do material de estudo relevante:
                ---
                %s
                ---
                Use este contexto para fundamentar sua resposta.
                """.formatted(ragContext);

        ChatResponse response = chatModel.call(
                new Prompt(List.of(
                        new SystemMessage(contextualSystem),
                        new UserMessage(userMessage))));
        return response.getResult().getOutput().getText();
    }
}