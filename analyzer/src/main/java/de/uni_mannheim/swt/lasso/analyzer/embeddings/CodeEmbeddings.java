package de.uni_mannheim.swt.lasso.analyzer.embeddings;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import de.uni_mannheim.swt.lasso.analyzer.batch.reader.LocalArtifactReader;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;

@Service
public class CodeEmbeddings {

    String DJL_MODEL = "bigcode/starcoder";
    String DJL_PATH = "djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2";
    ZooModel<String, float[]> model;
    private static final Logger LOG = LoggerFactory.getLogger(LocalArtifactReader.class);

    public CodeEmbeddings() {
        try {
            this.model = loadModelInitially();
            LOG.info("Loading model: " + DJL_MODEL + " from " + DJL_PATH + "successful");
        } catch (Throwable e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
            LOG.error(e.getLocalizedMessage());
        }
    }

    public ZooModel<String, float[]> loadModelInitially()
            throws ModelNotFoundException, MalformedModelException, IOException {
        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls(DJL_PATH)
                .optEngine("PyTorch")
                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                .build();

        return criteria.loadModel();
    }

    public float[] getEmbeddings(String text)
            throws TranslateException, Exception {
        if (text == null) {
            throw new Exception();
        }
        Predictor<String, float[]> predictor = this.model.newPredictor();
        return predictor.predict(text);
    }
}
