package idesyde.core.generator;

import com.squareup.javapoet.*;
import idesyde.core.AutoRegister;
import idesyde.core.Explorer;
import idesyde.core.IdentificationRule;
import idesyde.core.Module;
import idesyde.core.ReverseIdentificationRule;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SupportedAnnotationTypes({ "idesyde.core.AutoRegister" })
@SupportedSourceVersion(SourceVersion.RELEASE_17)

public class AutoModuleProcessor extends AbstractProcessor {

    protected Optional<? extends TypeElement> getValueOfAnnotationMirror(AnnotationMirror annotationMirror) {
        return annotationMirror.getElementValues().entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals("value"))
                .flatMap(e -> processingEnv.getElementUtils().getAllTypeElements(e.getValue().getValue().toString())
                        .stream())
                .findAny();
    }
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var identificationRuleCls = processingEnv.getElementUtils().getTypeElement(IdentificationRule.class.getCanonicalName());
        var reverseIdentificationRuleCls = processingEnv.getElementUtils().getTypeElement(ReverseIdentificationRule.class.getCanonicalName());
        var explorerCls = processingEnv.getElementUtils().getTypeElement(Explorer.class.getCanonicalName());
        var elems = roundEnv.getElementsAnnotatedWith(AutoRegister.class);
        HashMap<TypeElement, TypeElement> irules = new HashMap<>();
        HashMap<TypeElement, TypeElement> rrules = new HashMap<>();
        HashMap<TypeElement, TypeElement> explorers = new HashMap<>();
        for (var elem : elems) {
            if (elem.getKind().isClass()) {
                elem.getAnnotationMirrors().stream().map(this::getValueOfAnnotationMirror).flatMap(Optional::stream).forEach(module -> {
                    var moduleElem = processingEnv.getElementUtils().getTypeElement(module.getQualifiedName());
                    if (processingEnv.getTypeUtils().isAssignable(elem.asType(), identificationRuleCls.asType())) { // is IRule
                        if (elem instanceof TypeElement typeElement) {
                            irules.put(typeElement, moduleElem);
                        }
                    }
                    if (processingEnv.getTypeUtils().isAssignable(elem.asType(), reverseIdentificationRuleCls.asType())) { // is IRule
                        if (elem instanceof TypeElement typeElement) {
                            rrules.put(typeElement, moduleElem);
                        }
                    }
                    if (processingEnv.getTypeUtils().isAssignable(elem.asType(), explorerCls.asType())) { // is IRule
                        if (elem instanceof TypeElement typeElement) {
                            explorers.put(typeElement, moduleElem);
                        }
                    }
                });
            }
        }
        var modules = Stream.concat(irules.values().stream(), Stream.concat(rrules.values().stream(), explorers.values().stream()))
                .collect(Collectors.toSet());
        for (var module : modules) {
            var autoModuleBuilder = TypeSpec.classBuilder("AutoModule" + module.getSimpleName()).addSuperinterface(ClassName.get(module.asType())).addModifiers(Modifier.FINAL, Modifier.PUBLIC);
            var moduleIRules = irules.entrySet().stream().filter(e -> e.getValue().equals(module)).map(Map.Entry::getKey).collect(Collectors.toSet());
            var moduleRRules = rrules.entrySet().stream().filter(e -> e.getValue().equals(module)).map(Map.Entry::getKey).collect(Collectors.toSet());
            var moduleExplorers = explorers.entrySet().stream().filter(e -> e.getValue().equals(module)).map(Map.Entry::getKey).collect(Collectors.toSet());
            // add minimal stuff
            autoModuleBuilder.addMethod(
                    MethodSpec.methodBuilder("uniqueIdentifier").returns(String.class).addModifiers(Modifier.PUBLIC).addStatement("return $S", module.getQualifiedName()).build()
            );
            // add the methods for irules
            autoModuleBuilder.addMethod(
                    MethodSpec.methodBuilder("identificationRules")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(ParameterizedTypeName.get(Set.class, IdentificationRule.class))
                            .addStatement("return Set.of(" + moduleIRules.stream().map(irule -> "new $T()").collect(Collectors.joining(", ")) + ")", moduleIRules.stream().map(irule -> ClassName.get(irule.asType())).toArray())
                            .build()
            );
            // add the methods for the orchestrator
            autoModuleBuilder.addMethod(
                    MethodSpec.methodBuilder("identicationRulesCanonicalClassNames")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(String[].class)
                            .addStatement("return new String[]{" + moduleIRules.stream().map(irule -> '"' + irule.getQualifiedName().toString() + '"').collect(Collectors.joining(", ")) + "}")
                            .build()
            );
            // add the methods for rrules
            autoModuleBuilder.addMethod(
                    MethodSpec.methodBuilder("reverseIdentificationRules")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(ParameterizedTypeName.get(Set.class, ReverseIdentificationRule.class))
                            .addStatement("return Set.of(" + moduleRRules.stream().map(irule -> "new $T()").collect(Collectors.joining(", ")) + ")", moduleRRules.stream().map(irule -> ClassName.get(irule.asType())).toArray())
                            .build()
            );
            // add the methods for the orchestrator (reverse)
            autoModuleBuilder.addMethod(
                    MethodSpec.methodBuilder("reverseIdenticationRulesCanonicalClassNames")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(String[].class)
                            .addStatement("return new String[]{" + moduleRRules.stream().map(irule -> '"' + irule.getQualifiedName().toString() + '"').collect(Collectors.joining(", ")) + "}")
                            .build()
            );
            // finally the same for explorers
            autoModuleBuilder.addMethod(
                    MethodSpec.methodBuilder("explorers")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(ParameterizedTypeName.get(Set.class, Explorer.class))
                            .addStatement("return Set.of(" + moduleExplorers.stream().map(irule -> "new $T()").collect(Collectors.joining(", ")) + ")", moduleExplorers.stream().map(irule -> ClassName.get(irule.asType())).toArray())
                            .build()
            );
            try {
                var pak = processingEnv.getElementUtils().getPackageOf(module);
                JavaFile.builder(pak.toString(), autoModuleBuilder.build()).build().writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        var modulesForMetaINF = modules.stream().map(m -> processingEnv.getElementUtils().getPackageOf(m).toString() + ".AutoModule" + m.getSimpleName()).collect(Collectors.toCollection(HashSet::new));
        // try to get the resource file
        try {
            var metaINF = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/idesyde/automodules");
            try (var reader = new BufferedReader(new InputStreamReader(metaINF.openInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    modulesForMetaINF.remove(line);
                }
            }

            try (var writer = new BufferedWriter(new OutputStreamWriter(metaINF.openOutputStream(), StandardCharsets.UTF_8))) {
                for (var m : modulesForMetaINF) {
                    writer.append(m).append('\n');
                }
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, "No IDeSyDe META-INF found. Creating one");
        }
        // the only reason the modules still exist is if the file did not exist. We create it now.
        if (!modulesForMetaINF.isEmpty()) {
            try {
                var metaINF = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/idesyde/automodules", modules.toArray(new TypeElement[0]));
                try (var writer = new BufferedWriter(new OutputStreamWriter(metaINF.openOutputStream(), StandardCharsets.UTF_8))) {
                    for (var m : modulesForMetaINF) {
                        writer.append(m).append('\n');
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }
}
