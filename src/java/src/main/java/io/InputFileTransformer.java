package io;

import forsyde.model.Converter;
import forsyde.model.ForSyDeDescription;
import org.eclipse.app4mc.amalthea.model.Amalthea;
import org.eclipse.app4mc.amalthea.model.io.AmaltheaLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class InputFileTransformer {

    private List<File> inputFiles;
    private Map<File, InputFileLabel> inputFilesLabels;
    private Boolean processed;

    public InputFileTransformer(List<File> inputFiles) {
        this.inputFiles = inputFiles;
        inputFilesLabels = new HashMap<>();
        processed = false;

        processFiles();
    }

    public InputFileTransformer(File[] inputFiles) {
        this.inputFiles = Arrays.asList(inputFiles);
        inputFilesLabels = new HashMap<>();
        processed = false;

        processFiles();
    }

    public void processFiles() {
        if(!processed) {
            for(File f : inputFiles) {
                System.out.println(f.getName());
                if(f.getName().endsWith("forsyde.json")) {
                    inputFilesLabels.put(f, InputFileLabel.FORSYDE_DESCRIPTION);
                }
                else if(f.getName().endsWith("amxmi")) {
                    inputFilesLabels.put(f, InputFileLabel.AMALTHEA);
                }
            }
        }
    }

    public ForSyDeDescription getForSyDeDescription() throws IOException {
        Path modelFilePath = inputFiles.stream()
                .filter((f) -> inputFilesLabels.get(f) == InputFileLabel.FORSYDE_DESCRIPTION)
                .findFirst()
                .get()
                .toPath();
        String modelString = Files.readAllLines(modelFilePath).stream()
                .reduce((s1, s2) -> s1 + System.lineSeparator() + s2)
                .get();
        return Converter.fromJsonString(modelString);
    }

    public Amalthea getAmaltheaModel() throws IOException {
        Path modelFilePath = inputFiles.stream()
                .filter((f) -> inputFilesLabels.get(f) == InputFileLabel.AMALTHEA)
                .findFirst()
                .get()
                .toPath();
        return AmaltheaLoader.loadFromFile(modelFilePath.toFile());
    }

}
