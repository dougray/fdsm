/*
 * Copyright (c) 2018 - present Fidesmo AB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.fidesmo.fdsm;

import java.io.Console;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CommandLineFormHandler implements FormHandler {

    private final Map<String, String> predefinedFields;

    public CommandLineFormHandler(Map<String, String> predefinedFields) {
        this.predefinedFields = predefinedFields;
    }

    @Override
    public Map<String, Field> processForm(List<Field> form) {
        if (form == null || form.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Field> results = new HashMap<>();

        System.out.println("Press Ctrl-D to cancel at any time");
        Thread cleanup = new Thread(() -> {
            System.err.println("\nCtrl-C received, shutting down");
        });
        Runtime.getRuntime().addShutdownHook(cleanup);
        try {
            for (Field field : form) {
                // Display-only fields
                if ("text".equals(field.getType()) || "image".equals(field.getType())) {
                    System.out.println(field.getLabel());
                    continue;
                }
                // Value fields
                if (predefinedFields.containsKey(field.getId())) {
                    field.setValue(predefinedFields.get(field.getId()));
                } else {
                    field.setValue(askForField(field).orElseThrow(() -> new UserCancelledException("User cancelled input")));
                }
                results.put(field.getId(), field);
            }
        } finally {
            Runtime.getRuntime().removeShutdownHook(cleanup);
        }
        return results;
    }

    protected Optional<String> askForField(Field f) {
        Console console = System.console();
        Optional<String> input;
        switch (f.getType()) {
            case "checkbox":
                System.out.println(f.getLabel() + ":");
                do {
                    System.out.println("Must be \"y\" or \"n\", press Ctrl-D to cancel");
                    input = Optional.ofNullable(console.readLine("> [y/n] "));
                } while (input.isPresent() && !input.get().trim().toLowerCase().matches("^(y|n)$"));
                return input.map(i -> i.trim().toLowerCase().equals("y") ? "true" : "false");
            case "edit":
                System.out.println(f.getLabel() + ":");
                return Optional.ofNullable(console.readLine("> "));
            case "paymentcard":
                System.out.println(f.getLabel() + ":");
                do {
                    System.out.println("Format must be \"PAN;MM/YY;CVV\", press Ctrl-D to cancel");
                    input = Optional.ofNullable(console.readLine("> "));
                } while (input.isPresent() && !input.get().trim().matches("^[0-9]{16};[0-1][0-9]/[0-9]{2};[0-9]{3}$"));
                return input;
            case "date":
                System.out.println(f.getLabel() + ":");
                // Validate
                do {
                    System.out.println("Format must be \"YYYY-MM-DD\", press Ctrl-D to cancel");
                    input = Optional.ofNullable(console.readLine("> [YYYY-MM-DD] "));
                } while (input.isPresent() && !input.get().trim().matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"));
                return input;
            case "option":
                String[] options = f.getLabel().split("\n");
                List<String> indexes = IntStream.range(0, options.length).mapToObj(i -> String.valueOf(i)).collect(Collectors.toList());
                Set<String> allowed = indexes.stream().collect(Collectors.toSet());
                String format = IntStream.range(0, options.length).mapToObj(i -> String.valueOf(i)).collect(Collectors.joining(","));
                do {
                    indexes.stream().forEachOrdered(i -> System.out.printf("[%s] %s\n", i, options[Integer.parseInt(i)]));
                    input = Optional.ofNullable(console.readLine("> [%s] ", format));
                } while (input.isPresent() && !allowed.contains(input.get()));
                return input;
            default:
                return Optional.ofNullable(console.readLine("> "));
        }
    }
}
