/*
 * Copyright (C) 2025 Nicholas J Emblow
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.emblow.envelofy.service.llm;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.util.Downloader;
import org.emblow.envelofy.service.AccountService;
import org.emblow.envelofy.service.EnvelopeService;
import org.emblow.envelofy.service.PatternService;
import org.emblow.envelofy.service.TransactionService;
import org.emblow.envelofy.service.IntentDetectionService;
import org.emblow.envelofy.service.ml.AdvancedMLService;
import org.emblow.envelofy.service.ml.SpendingInsightService;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class JllamaService extends AbstractLLMService {

    private final String modelName;
    private final String workingDirectory;
    private AbstractModel model;

    public JllamaService(
            TransactionService transactionService,
            EnvelopeService envelopeService,
            SpendingInsightService insightService,
            PatternService patternService,
            AdvancedMLService advancedMLService,
            IntentDetectionService intentDetectionService,
            AccountService accountService,
            String modelName,
            String workingDirectory
    ) {
        super(transactionService, envelopeService, insightService, patternService,
              advancedMLService, intentDetectionService, accountService);
        this.modelName = modelName;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String processUserQuery(String userQuery) {
        return super.processUserQuery(userQuery); // Delegate to AbstractLLMService
    }

    @Override
    protected String callLLM(String prompt) {
        try {
            AbstractModel localModel = getModel();

            PromptContext ctx;
            if (localModel.promptSupport().isPresent()) {
                ctx = localModel.promptSupport()
                        .get()
                        .builder()
                        .addSystemMessage("You are a financial analysis assistant with direct access to the user's financial data.")
                        .addUserMessage(prompt)
                        .build();
            } else {
                ctx = PromptContext.of(prompt);
            }

            return localModel.generateBuilder()
                .session(UUID.randomUUID())
                .promptContext(ctx)
                .ntokens(8096)
                .temperature(0.0f)
                .generate()
                .responseText;
        } catch (IOException e) {
            throw new RuntimeException("Error during JLlama inference: " + e.getMessage(), e);
        }
    }

    private synchronized AbstractModel getModel() throws IOException {
        if (model == null) {
            File localModelPath = new Downloader(workingDirectory, modelName).huggingFaceModel();
            model = ModelSupport.loadModel(localModelPath, DType.F32, DType.I8);
        }
        return model;
    }

    @Override
    protected boolean supportsStreaming() {
        return false;
    }
}