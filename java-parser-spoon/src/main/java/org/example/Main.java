package org.example;

import org.json.JSONObject;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;


public class Main {
    record Result(boolean success, Exception exception) {};

    private static Result writeStringToFile(String fileName, String str) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(str);
        } catch (IOException e) {
            return new Result(false, e);
        }
        return new Result(true, null);
    }

    private static String getUsage(String execName) {
        return "USAGE: " + execName + "<project-dir> [--json <output-json>]";
    } 

    private static HashMap<String, HashMap<String, Double>> getNodeMap(String projectDir) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(projectDir);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.buildModel();

        CtModel model = launcher.getModel();
        HashMap<String, HashMap<String, Double>> nodeMap = new HashMap<>();
        Collection<CtType<?>> allTypes = model.getAllTypes();
        HashSet<String> allNames = new HashSet<>();
        for (CtType<?> type : allTypes) {
            allNames.add(type.getQualifiedName());
        }

        for (CtType<?> type : allTypes) {
            double fullWeight = 0;
            String name = type.getQualifiedName();
            HashMap<String, Double> refMap = new HashMap<>();
            List<CtTypeReference<?>> typeReferences = type.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class));
            for (CtTypeReference<?> ref : typeReferences) {
                String refName = ref.getQualifiedName();
                if (allNames.contains(refName) && !Objects.equals(refName, name)) {
                    double weight = refMap.getOrDefault(refName, 0.) + 1;
                    fullWeight++;
                    refMap.put(refName, weight);
                }
            }
            for(Map.Entry<String, Double> refEntry : refMap.entrySet()){
                refEntry.setValue((refEntry.getValue())/(fullWeight));
            }
            nodeMap.put(name, refMap);
        }

        return nodeMap;
    }

    private static String serialize(HashMap<String, HashMap<String, Double>> nodeMap) {
        JSONObject jo = new JSONObject();
        nodeMap.forEach((key, value) -> {
            JSONObject refMap = new JSONObject();
            nodeMap.get(key).forEach(refMap::put);
            jo.put(key, refMap);
        });
        return jo.toString();
    }

    public static void main(String[] args) {
        final String execName = 
            Objects.requireNonNull(Main.class.getResource(Main.class.getSimpleName() + ".class")).getFile();
            
        if (args.length <= 0) {
            System.out.println(getUsage(execName));
            return;
        }

        final String serializedMap = serialize(
            getNodeMap(args[0])
        );

        if (args.length == 3 && Objects.equals(args[1], "--json")) {
            Result writeFile = 
                writeStringToFile(args[2], serializedMap);
            if (!writeFile.success()) {
                System.err.println(
                    "Writing to file " + 
                    args[2] + 
                    " failed with exception " + 
                    writeFile.exception().toString()
                );
            }
        } else if (args.length == 1) {
            System.out.println(serializedMap);
        } else {
            System.out.println(getUsage(execName));
        }
    }
}